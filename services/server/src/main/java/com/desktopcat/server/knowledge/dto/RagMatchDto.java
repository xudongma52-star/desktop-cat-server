package com.desktopcat.server.knowledge.dto;

/** Python 检索服务返回的一段相关正文。 */
public record RagMatchDto(long documentId, String content, double score) {
}
