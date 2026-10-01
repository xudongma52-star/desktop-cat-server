package com.desktopcat.server.knowledge.dto;

/** 传给 Python RAG 服务的历史对话消息。 */
public record RagConversationMessageDto(String role, String content) {
}
