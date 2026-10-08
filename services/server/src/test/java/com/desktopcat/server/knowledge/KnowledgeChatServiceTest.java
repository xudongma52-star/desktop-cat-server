package com.desktopcat.server.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.desktopcat.server.knowledge.dao.KnowledgeChatDO;
import com.desktopcat.server.knowledge.dao.KnowledgeChatDao;
import com.desktopcat.server.knowledge.dao.KnowledgeMessageDao;
import com.desktopcat.server.knowledge.dto.KnowledgeChatRequestDto;
import com.desktopcat.server.knowledge.dto.KnowledgeChatResponseDto;
import com.desktopcat.server.knowledge.dto.KnowledgeChatSummaryDto;
import com.desktopcat.server.knowledge.dto.KnowledgeChatTitleRequestDto;
import com.desktopcat.server.knowledge.dto.KnowledgeMessageDto;
import com.desktopcat.server.knowledge.dto.KnowledgeRetrieveRequestDto;
import com.desktopcat.server.knowledge.dto.KnowledgeSearchResultDto;
import com.desktopcat.server.knowledge.dto.RagConversationMessageDto;
import com.desktopcat.server.knowledge.service.KnowledgeChatPersistenceService;
import com.desktopcat.server.knowledge.service.KnowledgeChatService;
import com.desktopcat.server.knowledge.service.KnowledgeMemoryStore;
import com.desktopcat.server.knowledge.service.KnowledgeRetrievalService;
import com.desktopcat.server.knowledge.service.KnowledgeCompressionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

class KnowledgeChatServiceTest {
    private final KnowledgeChatDao chatDao = mock(KnowledgeChatDao.class);
    private final KnowledgeMessageDao messageDao = mock(KnowledgeMessageDao.class);
    private final KnowledgeRetrievalService retrievalService = mock(KnowledgeRetrievalService.class);
    private final KnowledgeChatPersistenceService persistenceService =
            mock(KnowledgeChatPersistenceService.class);
    private final KnowledgeMemoryStore memoryStore = mock(KnowledgeMemoryStore.class);
    private final KnowledgeCompressionService compressionService = mock(KnowledgeCompressionService.class);
    private KnowledgeChatService service;

    @BeforeEach
    void setUp() {
        service = new KnowledgeChatService(
                chatDao, messageDao, retrievalService, persistenceService,
                memoryStore, new ObjectMapper(), compressionService);
    }

    @Test
    void continuesOwnedChatWithCachedHistory() {
        KnowledgeChatDO chat = chat(12L, 7L, 2);
        List<RagConversationMessageDto> history = List.of(
                new RagConversationMessageDto("USER", "之前的问题"),
                new RagConversationMessageDto("ASSISTANT", "之前的回答"));
        KnowledgeSearchResultDto searchResult = new KnowledgeSearchResultDto(
                2, false, "新的回答", true, List.of());
        KnowledgeChatResponseDto persisted = response(12L, 3);
        when(chatDao.selectActiveById(7L, 12L)).thenReturn(chat);
        when(memoryStore.loadHistory(eq(7L), eq(chat), any())).thenReturn(history);
        when(compressionService.prepare(7L, chat, history))
                .thenReturn(new KnowledgeCompressionService.Context(null, history));
        when(retrievalService.retrieve(eq(7L), any(KnowledgeRetrieveRequestDto.class), eq(history), eq(null)))
                .thenReturn(searchResult);
        when(persistenceService.appendTurn(
                7L, chat, "那后来呢？", "新的回答", List.of()))
                .thenReturn(persisted);

        KnowledgeChatResponseDto result = service.chat(
                7L, new KnowledgeChatRequestDto(12L, " 那后来呢？ "));

        assertThat(result.chat().version()).isEqualTo(3);
        ArgumentCaptor<KnowledgeRetrieveRequestDto> questionCaptor =
                ArgumentCaptor.forClass(KnowledgeRetrieveRequestDto.class);
        verify(retrievalService).retrieve(eq(7L), questionCaptor.capture(), eq(history), eq(null));
        assertThat(questionCaptor.getValue().question()).isEqualTo("那后来呢？");
        verify(memoryStore).append(
                7L, 12L, 3, persisted.userMessage(), persisted.assistantMessage());
    }

    @Test
    void rejectsChatThatDoesNotBelongToCurrentUser() {
        when(chatDao.selectActiveById(7L, 99L)).thenReturn(null);

        assertThatThrownBy(() -> service.chat(
                7L, new KnowledgeChatRequestDto(99L, "继续")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Knowledge chat was not found");

        verify(memoryStore).markMissing(7L, 99L);
    }

    @Test
    void answerFailureDoesNotPersistOrCacheGeneratedSummary() throws Exception {
        KnowledgeChatDO chat = chat(12L, 7L, 20);
        when(chatDao.selectActiveById(7L, 12L)).thenReturn(chat);
        when(memoryStore.loadHistory(eq(7L), eq(chat), any())).thenReturn(List.of());
        var summary = new ObjectMapper().readTree("{\"topic\":\"项目A\"}");
        when(compressionService.prepare(eq(7L), eq(chat), any())).thenAnswer(invocation -> {
            chat.setMemorySummary(summary.toString());
            chat.setSummaryThroughMessageId(30L);
            return new KnowledgeCompressionService.Context(summary, List.of());
        });
        when(retrievalService.retrieve(eq(7L), any(), eq(List.of()), eq(summary)))
                .thenThrow(new IllegalStateException("RAG unavailable"));

        assertThatThrownBy(() -> service.chat(7L, new KnowledgeChatRequestDto(12L, "继续")))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(persistenceService);
        verify(memoryStore, never()).cacheSummary(eq(7L), any());
        verify(memoryStore, never()).append(anyLong(), anyLong(), any(Integer.class), any(), any());
    }

    @Test
    void renamesOwnedChatWithoutChangingHistoryVersionOrLastAnswerTime() {
        KnowledgeChatDO chat = chat(12L, 7L, 2);
        when(chatDao.selectActiveById(7L, 12L)).thenReturn(chat);
        when(chatDao.updateTitle(7L, 12L, "学习记录")).thenAnswer(invocation -> {
            chat.setTitle("学习记录");
            return 1;
        });

        KnowledgeChatSummaryDto renamed = service.renameChat(
                7L, 12L, new KnowledgeChatTitleRequestDto(" 学习记录 "));

        assertThat(renamed.title()).isEqualTo("学习记录");
        assertThat(renamed.version()).isEqualTo(2);
        assertThat(renamed.updatedAt()).isEqualTo(Instant.EPOCH);
        verifyNoInteractions(messageDao, retrievalService, persistenceService);
        verify(memoryStore, never()).invalidateDeleted(anyLong(), anyLong());
    }

    @Test
    void rejectsEmptyAndTooLongTitlesBeforeUpdating() {
        assertThatThrownBy(() -> service.renameChat(
                7L, 12L, new KnowledgeChatTitleRequestDto("   ")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Chat title is required");
        assertThatThrownBy(() -> service.renameChat(
                7L, 12L, new KnowledgeChatTitleRequestDto("学".repeat(81))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Chat title must not exceed 80 characters");
        verifyNoInteractions(chatDao);
    }

    @Test
    void deletesOwnedChatAndInvalidatesMemoryOnlyAfterDatabaseSuccess() {
        when(chatDao.selectActiveById(7L, 12L)).thenReturn(chat(12L, 7L, 2));
        when(chatDao.softDelete(eq(7L), eq(12L), any())).thenReturn(1);

        service.deleteChat(7L, 12L);

        var order = org.mockito.Mockito.inOrder(chatDao, memoryStore);
        order.verify(chatDao).softDelete(eq(7L), eq(12L), any());
        order.verify(memoryStore).invalidateDeleted(7L, 12L);
        verifyNoInteractions(messageDao);
    }

    @Test
    void doesNotInvalidateCacheWhenDeletionFails() {
        when(chatDao.selectActiveById(7L, 12L)).thenReturn(chat(12L, 7L, 2));
        when(chatDao.softDelete(eq(7L), eq(12L), any()))
                .thenThrow(new IllegalStateException("Database unavailable"));

        assertThatThrownBy(() -> service.deleteChat(7L, 12L))
                .isInstanceOf(IllegalStateException.class);
        verify(memoryStore, never()).invalidateDeleted(anyLong(), anyLong());
    }

    @Test
    void rejectsRenameAndDeleteForAnotherUsersChat() {
        when(chatDao.selectActiveById(8L, 12L)).thenReturn(null);

        assertThatThrownBy(() -> service.renameChat(
                8L, 12L, new KnowledgeChatTitleRequestDto("不能修改")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Knowledge chat was not found");
        assertThatThrownBy(() -> service.deleteChat(8L, 12L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Knowledge chat was not found");

        verify(chatDao, never()).updateTitle(anyLong(), anyLong(), any());
        verify(chatDao, never()).softDelete(anyLong(), anyLong(), any());
        verify(memoryStore, never()).invalidateDeleted(anyLong(), anyLong());
    }

    private KnowledgeChatDO chat(long chatId, long userId, int version) {
        KnowledgeChatDO chat = new KnowledgeChatDO();
        chat.setChatId(chatId);
        chat.setUserId(userId);
        chat.setTitle("旧对话");
        chat.setVersion(version);
        chat.setCreatedAt(Instant.EPOCH);
        chat.setUpdatedAt(Instant.EPOCH);
        return chat;
    }

    private KnowledgeChatResponseDto response(long chatId, int version) {
        Instant now = Instant.EPOCH;
        return new KnowledgeChatResponseDto(
                new KnowledgeChatSummaryDto(chatId, "旧对话", version, now, now),
                new KnowledgeMessageDto(10L, "USER", "那后来呢？", List.of(), now),
                new KnowledgeMessageDto(11L, "ASSISTANT", "新的回答", List.of(), now));
    }
}
