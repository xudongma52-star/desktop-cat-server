package com.desktopcat.server.knowledge.service;

import com.desktopcat.server.knowledge.dao.KnowledgeChatDO;
import com.desktopcat.server.knowledge.dao.KnowledgeChatDao;
import com.desktopcat.server.knowledge.dao.KnowledgeMessageDO;
import com.desktopcat.server.knowledge.dao.KnowledgeMessageDao;
import com.desktopcat.server.knowledge.dto.KnowledgeChatResponseDto;
import com.desktopcat.server.knowledge.dto.KnowledgeChatSummaryDto;
import com.desktopcat.server.knowledge.dto.KnowledgeMatchDto;
import com.desktopcat.server.knowledge.dto.KnowledgeMessageDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** 用短事务保存一轮完整问答，不把外部模型调用包含在数据库事务中。 */
@Service
@Profile("postgres")
public class KnowledgeChatPersistenceService {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeChatPersistenceService.class);

    private final KnowledgeChatDao chatDao;
    private final KnowledgeMessageDao messageDao;
    private final ObjectMapper objectMapper;

    public KnowledgeChatPersistenceService(
            KnowledgeChatDao chatDao,
            KnowledgeMessageDao messageDao,
            ObjectMapper objectMapper) {
        this.chatDao = chatDao;
        this.messageDao = messageDao;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public KnowledgeChatResponseDto createChat(
            long userId,
            String title,
            String question,
            String answer,
            List<KnowledgeMatchDto> sources) {
        Instant now = Instant.now();
        KnowledgeChatDO chat = new KnowledgeChatDO();
        chat.setUserId(userId);
        chat.setTitle(title);
        chat.setVersion(1);
        chat.setCreatedAt(now);
        chat.setUpdatedAt(now);
        if (chatDao.insertChat(chat) != 1 || chat.getChatId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Knowledge chat could not be created.");
        }

        KnowledgeMessageDO userMessage = insertMessage(
                chat.getChatId(), "USER", question, null, now);
        KnowledgeMessageDO assistantMessage = insertMessage(
                chat.getChatId(), "ASSISTANT", answer, writeSources(sources), now);
        log.info("event=knowledge_chat_created userId={} chatId={} version={} sourceCount={}",
                userId, chat.getChatId(), chat.getVersion(), sources.size());
        return toResponse(chat, userMessage, assistantMessage, sources);
    }

    @Transactional
    public KnowledgeChatResponseDto appendTurn(
            long userId,
            KnowledgeChatDO chat,
            String question,
            String answer,
            List<KnowledgeMatchDto> sources) {
        Instant now = Instant.now();
        int updated = chatDao.updateAfterMessage(
                userId, chat.getChatId(), chat.getVersion(), now, chat);
        if (updated != 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Knowledge chat has been updated. Refresh and try again.");
        }
        chat.setVersion(chat.getVersion() + 1);
        chat.setUpdatedAt(now);

        KnowledgeMessageDO userMessage = insertMessage(
                chat.getChatId(), "USER", question, null, now);
        KnowledgeMessageDO assistantMessage = insertMessage(
                chat.getChatId(), "ASSISTANT", answer, writeSources(sources), now);
        log.info("event=knowledge_chat_turn_saved userId={} chatId={} version={} sourceCount={}",
                userId, chat.getChatId(), chat.getVersion(), sources.size());
        return toResponse(chat, userMessage, assistantMessage, sources);
    }

    private KnowledgeMessageDO insertMessage(
            long chatId,
            String role,
            String content,
            String sourcesJson,
            Instant createdAt) {
        KnowledgeMessageDO message = new KnowledgeMessageDO();
        message.setChatId(chatId);
        message.setRole(role);
        message.setContent(content);
        message.setSourcesJson(sourcesJson);
        message.setCreatedAt(createdAt);
        if (messageDao.insertMessage(message) != 1 || message.getMessageId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Knowledge message could not be created.");
        }
        return message;
    }

    private KnowledgeChatResponseDto toResponse(
            KnowledgeChatDO chat,
            KnowledgeMessageDO userMessage,
            KnowledgeMessageDO assistantMessage,
            List<KnowledgeMatchDto> sources) {
        return new KnowledgeChatResponseDto(
                new KnowledgeChatSummaryDto(
                        chat.getChatId(), chat.getTitle(), chat.getVersion(),
                        chat.getCreatedAt(), chat.getUpdatedAt()),
                new KnowledgeMessageDto(
                        userMessage.getMessageId(), userMessage.getRole(),
                        userMessage.getContent(), List.of(), userMessage.getCreatedAt()),
                new KnowledgeMessageDto(
                        assistantMessage.getMessageId(), assistantMessage.getRole(),
                        assistantMessage.getContent(), List.copyOf(sources),
                        assistantMessage.getCreatedAt()));
    }

    private String writeSources(List<KnowledgeMatchDto> sources) {
        try {
            return objectMapper.writeValueAsString(sources);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Knowledge sources could not be serialized.", exception);
        }
    }
}
