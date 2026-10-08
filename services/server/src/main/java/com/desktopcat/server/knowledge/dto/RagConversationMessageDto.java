package com.desktopcat.server.knowledge.dto;

/** 传给 Python RAG 服务的历史对话消息。 */
public record RagConversationMessageDto(Long messageId, String role, String content) {
    public RagConversationMessageDto(String role, String content) {
        this(null, role, content);
    }
}
