package com.desktopcat.server.knowledge.service;

import com.desktopcat.server.knowledge.client.RagServiceClient;
import com.desktopcat.server.knowledge.dao.KnowledgeChatDO;
import com.desktopcat.server.knowledge.dao.KnowledgeMessageDO;
import com.desktopcat.server.knowledge.dao.KnowledgeMessageDao;
import com.desktopcat.server.knowledge.dto.RagCompressRequestDto;
import com.desktopcat.server.knowledge.dto.RagCompressResponseDto;
import com.desktopcat.server.knowledge.dto.RagConversationMessageDto;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** 摘要和最近原文组合成工作记忆，数据库的原始消息始终保留。 */
@Service
@Profile("postgres")
public class KnowledgeCompressionService {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeCompressionService.class);
    private static final int RECENT_MESSAGES = 10;
    private static final int HISTORY_BUDGET = 3000;
    private static final int BATCH_BUDGET = 6000;
    private final RagServiceClient ragClient;
    private final KnowledgeMessageDao messageDao;
    private final KnowledgeMemoryStore memoryStore;

    public KnowledgeCompressionService(RagServiceClient ragClient,
            KnowledgeMessageDao messageDao, KnowledgeMemoryStore memoryStore) {
        this.ragClient = ragClient;
        this.messageDao = messageDao;
        this.memoryStore = memoryStore;
    }

    public Context prepare(long userId, KnowledgeChatDO chat, List<RagConversationMessageDto> history) {
        JsonNode originalSummary = memoryStore.loadSummary(userId, chat);
        long originalThrough = chat.getSummaryThroughMessageId() == null
                ? 0 : chat.getSummaryThroughMessageId();
        List<RagConversationMessageDto> pending = history.stream()
                .filter(message -> message.messageId() == null || message.messageId() > originalThrough)
                .toList();
        if (pending.size() <= RECENT_MESSAGES) {
            return new Context(originalSummary, bounded(pending, originalSummary));
        }

        // 缓存只保留50轮。摘要未覆盖的更早消息必须通过已有分页查询补齐，不能跳过。
        List<RagConversationMessageDto> all = new ArrayList<>(pending);
        boolean gap = chat.getVersion() * 2L > history.size()
                && !history.isEmpty() && history.getFirst().messageId() != null
                && originalThrough < history.getFirst().messageId();
        if (!gap && pending.size() - RECENT_MESSAGES < 20
                && estimate(pending) + estimate(originalSummary) <= HISTORY_BUDGET) {
            return new Context(originalSummary, pending);
        }
        try {
            if (gap) {
                Long before = history.getFirst().messageId();
                while (true) {
                    List<KnowledgeMessageDO> page = messageDao.selectRecentByChat(
                            userId, chat.getChatId(), before, 100);
                    List<RagConversationMessageDto> older = new ArrayList<>();
                    for (KnowledgeMessageDO message : page) {
                        if (message.getMessageId() > originalThrough) {
                            older.add(new RagConversationMessageDto(message.getMessageId(),
                                    message.getRole(), message.getContent()));
                        }
                    }
                    Collections.reverse(older);
                    all.addAll(0, older);
                    if (page.isEmpty() || page.size() < 100
                            || page.getLast().getMessageId() <= originalThrough) {
                        break;
                    }
                    before = page.getLast().getMessageId();
                }
            }
            int end = all.size() - RECENT_MESSAGES;
            JsonNode summary = originalSummary;
            long through = originalThrough;
            int start = 0;
            while (start < end) {
                int stop = start;
                int tokens = 0;
                // 每批按完整问答切分；超长单轮仍完整交给压缩模型，禁止丢弃尾部信息。
                while (stop < end && stop - start < 20) {
                    int next = Math.min(stop + 2, end);
                    int cost = estimate(all.subList(stop, next));
                    if (stop > start && tokens + cost > BATCH_BUDGET) {
                        break;
                    }
                    tokens += cost;
                    stop = next;
                }
                List<RagConversationMessageDto> batch = all.subList(start, stop);
                RagConversationMessageDto last = batch.getLast();
                if (last.messageId() == null || !"ASSISTANT".equals(last.role())) {
                    throw new IllegalStateException("Memory batch must end with an assistant message.");
                }
                RagCompressResponseDto response = ragClient.compress(new RagCompressRequestDto(summary, batch));
                summary = validate(response);
                through = last.messageId();
                start = stop;
            }
            // 只更新本次调用的对象；后续回答成功后由原有事务与消息一起持久化。
            chat.setMemorySummary(summary.toString());
            chat.setSummaryThroughMessageId(through);
            log.info("event=knowledge_memory_summary_generated userId={} chatId={} messageCount={} throughMessageId={}",
                    userId, chat.getChatId(), end, through);
            return new Context(summary, bounded(all.subList(end, all.size()), summary));
        } catch (RuntimeException exception) {
            log.warn("event=knowledge_memory_compression_failed userId={} chatId={} errorType={}",
                    userId, chat.getChatId(), exception.getClass().getSimpleName());
            return new Context(originalSummary, bounded(pending, originalSummary));
        }
    }

    private JsonNode validate(RagCompressResponseDto response) {
        JsonNode summary = response == null ? null : response.summary();
        if (summary == null || !summary.isObject() || summary.isEmpty()
                || summary.toString().length() > 6000 || !summary.path("topic").isTextual()) {
            throw new IllegalStateException("Invalid memory summary.");
        }
        for (String field : List.of("userFacts", "discussedFindings", "constraints", "openQuestions", "entities")) {
            if (!summary.path(field).isArray()) {
                throw new IllegalStateException("Invalid memory summary field.");
            }
            for (JsonNode item : summary.path(field)) {
                if (!item.isTextual()) {
                    throw new IllegalStateException("Invalid memory summary item.");
                }
            }
        }
        return summary;
    }

    private List<RagConversationMessageDto> bounded(List<RagConversationMessageDto> messages, JsonNode summary) {
        List<RagConversationMessageDto> recent = messages.subList(
                Math.max(0, messages.size() - RECENT_MESSAGES), messages.size());
        int remaining = Math.max(200, HISTORY_BUDGET - estimate(summary));
        List<RagConversationMessageDto> selected = new ArrayList<>();
        for (int index = recent.size(); index > 0; index -= 2) {
            List<RagConversationMessageDto> pair = recent.subList(Math.max(0, index - 2), index);
            int cost = estimate(pair);
            if (cost <= remaining) {
                selected.addAll(0, pair);
                remaining -= cost;
            } else {
                if (selected.isEmpty()) {
                    // 极长回答降级时仍保留对应问题，不能只留下孤立的助手消息。
                    int perMessage = Math.max(1, (remaining - 20) / pair.size());
                    for (RagConversationMessageDto message : pair) {
                        String content = message.content();
                        if (estimateText(content) > perMessage) {
                            int end = content.offsetByCodePoints(0, Math.min(
                                    content.codePointCount(0, content.length()), Math.max(1, perMessage / 2)));
                            content = content.substring(0, end);
                        }
                        selected.add(new RagConversationMessageDto(message.messageId(), message.role(), content));
                    }
                }
                break;
            }
        }
        return List.copyOf(selected);
    }

    private int estimate(List<RagConversationMessageDto> messages) {
        return messages.stream().mapToInt(message -> estimateText(message.content()) + 10).sum();
    }

    private int estimate(JsonNode summary) {
        return summary == null ? 0 : estimateText(summary.toString());
    }

    /** 当前豆包客户端没有tokenizer；ASCII按4字符、其余按2 token保守估算。 */
    private int estimateText(String text) {
        int nonAscii = (int) text.codePoints().filter(point -> point > 127).count();
        int ascii = text.codePointCount(0, text.length()) - nonAscii;
        return nonAscii * 2 + (ascii + 3) / 4;
    }

    public record Context(JsonNode summary, List<RagConversationMessageDto> history) {
    }
}
