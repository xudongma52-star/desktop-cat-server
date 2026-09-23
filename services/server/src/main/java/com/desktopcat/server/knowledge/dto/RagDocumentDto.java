package com.desktopcat.server.knowledge.dto;

/** Java 服务传给 Python 检索服务的最小文章数据。 */
public record RagDocumentDto(long documentId, String title, String content) {
}
