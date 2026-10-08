package com.desktopcat.server.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.desktopcat.server.knowledge.client.RagServiceClient;
import com.desktopcat.server.knowledge.dao.KnowledgeChatDO;
import com.desktopcat.server.knowledge.dao.KnowledgeMessageDO;
import com.desktopcat.server.knowledge.dao.KnowledgeMessageDao;
import com.desktopcat.server.knowledge.dto.RagCompressRequestDto;
import com.desktopcat.server.knowledge.dto.RagCompressResponseDto;
import com.desktopcat.server.knowledge.dto.RagConversationMessageDto;
import com.desktopcat.server.knowledge.service.KnowledgeCompressionService;
import com.desktopcat.server.knowledge.service.KnowledgeMemoryStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class KnowledgeCompressionServiceTest {
    private final RagServiceClient rag = mock(RagServiceClient.class);
    private final KnowledgeMessageDao messages = mock(KnowledgeMessageDao.class);
    private final KnowledgeMemoryStore memory = mock(KnowledgeMemoryStore.class);
    private final KnowledgeCompressionService service = new KnowledgeCompressionService(rag, messages, memory);

    @Test
    void shortConversationSkipsCompression() {
        var chat = chat(2);
        var history = history(1, 4);
        var result = service.prepare(7, chat, history);
        assertThat(result.history()).isEqualTo(history);
        assertThat(result.summary()).isNull();
        verifyNoInteractions(rag, messages);
    }

    @Test
    void incrementalCompressionExcludesCoveredMessagesAndKeepsFiveRecentTurns() throws Exception {
        var chat = chat(20);
        chat.setMemorySummary(summary().toString());
        chat.setSummaryThroughMessageId(10L);
        when(memory.loadSummary(7, chat)).thenReturn(summary());
        when(rag.compress(any())).thenReturn(new RagCompressResponseDto(summary()));
        var result = service.prepare(7, chat, history(1, 40));
        assertThat(chat.getSummaryThroughMessageId()).isEqualTo(30L);
        assertThat(result.history()).isEqualTo(history(31, 40));
        var request = ArgumentCaptor.forClass(RagCompressRequestDto.class);
        verify(rag).compress(request.capture());
        assertThat(request.getValue().turns()).isEqualTo(history(11, 30));
        verifyNoInteractions(messages);
    }

    @Test
    void failureDoesNotAdvanceCheckpointAndReturnsRecentMessages() {
        var chat = chat(20);
        when(rag.compress(any())).thenThrow(new IllegalStateException("Unavailable"));
        var result = service.prepare(7, chat, history(1, 40));
        assertThat(chat.getSummaryThroughMessageId()).isNull();
        assertThat(chat.getMemorySummary()).isNull();
        assertThat(result.history()).isEqualTo(history(31, 40));
    }

    @Test
    void fillsGapOutsideRedisWindowUsingOwnedDatabasePagination() throws Exception {
        var chat = chat(60);
        List<KnowledgeMessageDO> older = LongStream.rangeClosed(1, 20).mapToObj(id -> {
            var message = new KnowledgeMessageDO();
            message.setMessageId(21 - id);
            message.setRole((21 - id) % 2 == 1 ? "USER" : "ASSISTANT");
            message.setContent("旧消息" + (21 - id));
            return message;
        }).toList();
        when(messages.selectRecentByChat(7, 12, 21L, 100)).thenReturn(older);
        when(rag.compress(any())).thenReturn(new RagCompressResponseDto(summary()));
        var result = service.prepare(7, chat, history(21, 120));
        var requests = ArgumentCaptor.forClass(RagCompressRequestDto.class);
        verify(rag, times(6)).compress(requests.capture());
        assertThat(requests.getAllValues().getFirst().turns().getFirst().messageId()).isEqualTo(1L);
        assertThat(chat.getSummaryThroughMessageId()).isEqualTo(110L);
        assertThat(result.history()).isEqualTo(history(111, 120));
    }

    @Test
    void malformedSummaryCannotAdvanceCheckpoint() {
        var chat = chat(20);
        when(rag.compress(any())).thenReturn(new RagCompressResponseDto(new ObjectMapper().createObjectNode()));
        service.prepare(7, chat, history(1, 40));
        assertThat(chat.getSummaryThroughMessageId()).isNull();
    }

    @Test
    void fallbackKeepsQuestionWithOversizedAnswer() {
        var chat = chat(1);
        var result = service.prepare(7, chat, List.of(
                new RagConversationMessageDto(1L, "USER", "那后来呢？"),
                new RagConversationMessageDto(2L, "ASSISTANT", "长".repeat(10000))));
        assertThat(result.history()).hasSize(2);
        assertThat(result.history().getFirst().content()).isEqualTo("那后来呢？");
        assertThat(result.history().getLast().content().length()).isLessThan(10000);
        verifyNoInteractions(rag);
    }

    private com.fasterxml.jackson.databind.JsonNode summary() throws Exception {
        return new ObjectMapper().readTree("""
                {"topic":"项目A","userFacts":[],"discussedFindings":[],
                 "constraints":[],"openQuestions":[],"entities":[]}
                """);
    }

    private KnowledgeChatDO chat(int turns) {
        var chat = new KnowledgeChatDO();
        chat.setChatId(12L);
        chat.setUserId(7L);
        chat.setVersion(turns);
        return chat;
    }

    private List<RagConversationMessageDto> history(long first, long last) {
        return LongStream.rangeClosed(first, last).mapToObj(id -> new RagConversationMessageDto(
                id, id % 2 == 1 ? "USER" : "ASSISTANT", "消息" + id)).toList();
    }
}
