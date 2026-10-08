package com.desktopcat.server.knowledge.service;

import com.desktopcat.server.knowledge.dao.KnowledgeChatDO;
import com.desktopcat.server.knowledge.dao.KnowledgeChatDao;
import com.desktopcat.server.knowledge.dao.KnowledgeMessageDO;
import com.desktopcat.server.knowledge.dao.KnowledgeMessageDao;
import com.desktopcat.server.knowledge.dto.KnowledgeChatRequestDto;
import com.desktopcat.server.knowledge.dto.KnowledgeChatResponseDto;
import com.desktopcat.server.knowledge.dto.KnowledgeChatSummaryDto;
import com.desktopcat.server.knowledge.dto.KnowledgeChatTitleRequestDto;
import com.desktopcat.server.knowledge.dto.KnowledgeMatchDto;
import com.desktopcat.server.knowledge.dto.KnowledgeMessageDto;
import com.desktopcat.server.knowledge.dto.KnowledgeMessagePageDto;
import com.desktopcat.server.knowledge.dto.KnowledgeRetrieveRequestDto;
import com.desktopcat.server.knowledge.dto.KnowledgeSearchResultDto;
import com.desktopcat.server.knowledge.dto.RagConversationMessageDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** 编排知识库对话历史、RAG 回答、数据库持久化和 Redis 记忆。 */
@Service
@Profile("postgres")
public class KnowledgeChatService {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeChatService.class);
    private static final int CHAT_LIST_LIMIT = 30;
    private static final int MESSAGE_PAGE_SIZE = 100;
    private static final int MESSAGE_QUERY_LIMIT = MESSAGE_PAGE_SIZE + 1;
    private static final int TITLE_LENGTH = 30;
    private static final int MAX_TITLE_LENGTH = 80;
    private static final int MAX_QUESTION_LENGTH = 500;

    private final KnowledgeChatDao chatDao;
    private final KnowledgeMessageDao messageDao;
    private final KnowledgeRetrievalService retrievalService;
    private final KnowledgeChatPersistenceService persistenceService;
    private final KnowledgeMemoryStore memoryStore;
    private final ObjectMapper objectMapper;
    private final KnowledgeCompressionService compressionService;

    public KnowledgeChatService(
            KnowledgeChatDao chatDao,
            KnowledgeMessageDao messageDao,
            KnowledgeRetrievalService retrievalService,
            KnowledgeChatPersistenceService persistenceService,
            KnowledgeMemoryStore memoryStore,
            ObjectMapper objectMapper,
            KnowledgeCompressionService compressionService) {
        this.chatDao = chatDao;
        this.messageDao = messageDao;
        this.retrievalService = retrievalService;
        this.persistenceService = persistenceService;
        this.memoryStore = memoryStore;
        this.objectMapper = objectMapper;
        this.compressionService = compressionService;
    }

    public List<KnowledgeChatSummaryDto> listChats(long userId) {
        return chatDao.selectRecent(userId, CHAT_LIST_LIMIT).stream()
                .map(this::toSummary)
                .toList();
    }

    public KnowledgeChatSummaryDto renameChat(
            long userId, long chatId, KnowledgeChatTitleRequestDto request) {
        if (request == null) {
            throw badRequest("Request body is required.");
        }
        if (request.title() == null || request.title().trim().isEmpty()) {
            throw badRequest("Chat title is required.");
        }
        String title = request.title().trim();
        if (title.codePointCount(0, title.length()) > MAX_TITLE_LENGTH) {
            throw badRequest("Chat title must not exceed 80 characters.");
        }
        requireChat(userId, chatId);
        if (chatDao.updateTitle(userId, chatId, title) != 1) {
            throw notFound();
        }
        // 标题不改变内容版本和最后问答时间，最近消息的缓存仍然有效。
        KnowledgeChatDO updated = requireChat(userId, chatId);
        log.info("event=knowledge_chat_renamed userId={} chatId={} titleLength={}",
                userId, chatId, title.codePointCount(0, title.length()));
        return toSummary(updated);
    }

    public void deleteChat(long userId, long chatId) {
        requireChat(userId, chatId);
        if (chatDao.softDelete(userId, chatId, Instant.now()) != 1) {
            throw notFound();
        }
        // 单条 SQL 已提交后再失效缓存；即使 Redis 不可用，数据库仍会拒绝访问。
        memoryStore.invalidateDeleted(userId, chatId);
        log.info("event=knowledge_chat_deleted userId={} chatId={}", userId, chatId);
    }

    public KnowledgeMessagePageDto listMessages(
            long userId, long chatId, Long beforeMessageId) {
        KnowledgeChatDO chat = requireChat(userId, chatId);
        if (beforeMessageId != null && beforeMessageId <= 0) {
            throw badRequest("Before message id must be positive.");
        }
        List<KnowledgeMessageDO> newestFirst = new ArrayList<>(
                messageDao.selectRecentByChat(
                        userId, chat.getChatId(), beforeMessageId, MESSAGE_QUERY_LIMIT));
        boolean hasMore = newestFirst.size() > MESSAGE_PAGE_SIZE;
        if (hasMore) {
            newestFirst = new ArrayList<>(newestFirst.subList(0, MESSAGE_PAGE_SIZE));
        }
        Collections.reverse(newestFirst);
        List<KnowledgeMessageDto> items = newestFirst.stream()
                .map(this::toMessage)
                .toList();
        Long nextBeforeMessageId = hasMore && !items.isEmpty()
                ? items.getFirst().messageId()
                : null;
        return new KnowledgeMessagePageDto(
                List.copyOf(items), hasMore, nextBeforeMessageId);
    }

    public KnowledgeChatResponseDto chat(long userId, KnowledgeChatRequestDto request) {
        String question = validateQuestion(request);
        if (request.chatId() == null) {
            KnowledgeSearchResultDto result = retrievalService.retrieve(
                    userId, new KnowledgeRetrieveRequestDto(question), List.of());
            KnowledgeChatResponseDto response = persistenceService.createChat(
                    userId, titleFrom(question), question, answerFor(result), result.matches());
            updateMemory(userId, response);
            return response;
        }

        KnowledgeChatDO chat = requireChat(userId, request.chatId());
        List<RagConversationMessageDto> history = memoryStore.loadHistory(
                userId,
                chat,
                () -> messageDao.selectRecentByChat(
                        userId, chat.getChatId(), null, MESSAGE_PAGE_SIZE));
        KnowledgeCompressionService.Context context = compressionService.prepare(userId, chat, history);
        KnowledgeSearchResultDto result = retrievalService.retrieve(
                userId, new KnowledgeRetrieveRequestDto(question), context.history(), context.summary());
        KnowledgeChatResponseDto response = persistenceService.appendTurn(
                userId, chat, question, answerFor(result), result.matches());
        updateMemory(userId, response);
        memoryStore.cacheSummary(userId, chat);
        return response;
    }

    private void updateMemory(long userId, KnowledgeChatResponseDto response) {
        memoryStore.append(
                userId,
                response.chat().chatId(),
                response.chat().version(),
                response.userMessage(),
                response.assistantMessage());
    }

    private KnowledgeChatDO requireChat(long userId, long chatId) {
        if (chatId <= 0) {
            throw badRequest("Chat id must be positive.");
        }
        if (memoryStore.isMissing(userId, chatId)) {
            throw notFound();
        }
        KnowledgeChatDO chat = chatDao.selectActiveById(userId, chatId);
        if (chat == null) {
            memoryStore.markMissing(userId, chatId);
            throw notFound();
        }
        return chat;
    }

    private String validateQuestion(KnowledgeChatRequestDto request) {
        if (request == null) {
            throw badRequest("Request body is required.");
        }
        if (request.question() == null || request.question().trim().isEmpty()) {
            throw badRequest("Question is required.");
        }
        String question = request.question().trim();
        if (question.codePointCount(0, question.length()) > MAX_QUESTION_LENGTH) {
            throw badRequest("Question must not exceed 500 characters.");
        }
        return question;
    }

    private String titleFrom(String question) {
        int codePointCount = question.codePointCount(0, question.length());
        if (codePointCount <= TITLE_LENGTH) {
            return question;
        }
        int endIndex = question.offsetByCodePoints(0, TITLE_LENGTH);
        return question.substring(0, endIndex);
    }

    private String answerFor(KnowledgeSearchResultDto result) {
        if (result.answerGenerated() && result.answer() != null && !result.answer().isBlank()) {
            return result.answer().trim();
        }
        if (!result.matches().isEmpty()) {
            return "回答模型暂时没有响应，先为你保留本次找到的原文片段。";
        }
        if (result.searchableRecordCount() == 0) {
            return "知识库里还没有可检索的记录。";
        }
        return "暂时没有找到与这个问题相关的记录。";
    }

    private KnowledgeChatSummaryDto toSummary(KnowledgeChatDO chat) {
        return new KnowledgeChatSummaryDto(
                chat.getChatId(), chat.getTitle(), chat.getVersion(),
                chat.getCreatedAt(), chat.getUpdatedAt());
    }

    private KnowledgeMessageDto toMessage(KnowledgeMessageDO message) {
        return new KnowledgeMessageDto(
                message.getMessageId(),
                message.getRole(),
                message.getContent(),
                readSources(message.getSourcesJson()),
                message.getCreatedAt());
    }

    private List<KnowledgeMatchDto> readSources(String sourcesJson) {
        if (sourcesJson == null || sourcesJson.isBlank()) {
            return List.of();
        }
        try {
            List<KnowledgeMatchDto> sources = objectMapper.readValue(
                    sourcesJson, new TypeReference<>() { });
            return sources == null ? List.of() : List.copyOf(sources);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Knowledge sources could not be read.", exception);
        }
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Knowledge chat was not found.");
    }
}
