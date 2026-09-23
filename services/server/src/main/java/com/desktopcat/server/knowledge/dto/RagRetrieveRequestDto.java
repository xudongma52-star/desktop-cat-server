package com.desktopcat.server.knowledge.dto;

import java.util.List;

/** Python 检索服务的内部请求。 */
public record RagRetrieveRequestDto(
        long userId,
        String question,
        int topK,
        List<RagDocumentDto> documents) {
}
