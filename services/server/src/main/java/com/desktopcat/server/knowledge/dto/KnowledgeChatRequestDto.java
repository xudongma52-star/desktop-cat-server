package com.desktopcat.server.knowledge.dto;

/** 创建知识库对话或继续现有对话的请求。 */
public record KnowledgeChatRequestDto(Long chatId, String question) {
}
