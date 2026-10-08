package com.desktopcat.server.knowledge.dto;

import java.util.List;
import com.fasterxml.jackson.databind.JsonNode;

/** Python 检索服务的内部请求。 */
public record RagRetrieveRequestDto(
        long userId,
        String question,
        int topK,
        List<Long> documentIds,
        List<RagConversationMessageDto> history,
        JsonNode summary) {

    public RagRetrieveRequestDto(long userId, String question, int topK,
            List<Long> documentIds, List<RagConversationMessageDto> history) {
        this(userId, question, topK, documentIds, history, null);
    }

    public RagRetrieveRequestDto(
            long userId,
            String question,
            int topK,
            List<Long> documentIds) {
        this(userId, question, topK, documentIds, List.of(), null);
    }
}
