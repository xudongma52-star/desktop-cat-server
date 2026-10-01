package com.desktopcat.server.knowledge.dto;

/** 一轮知识问答完成后的对话与两条新消息。 */
public record KnowledgeChatResponseDto(
        KnowledgeChatSummaryDto chat,
        KnowledgeMessageDto userMessage,
        KnowledgeMessageDto assistantMessage) {
}
