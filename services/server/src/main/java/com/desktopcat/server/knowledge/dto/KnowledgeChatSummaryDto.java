package com.desktopcat.server.knowledge.dto;

import java.time.Instant;

/** 知识库对话列表项。 */
public record KnowledgeChatSummaryDto(
        long chatId,
        String title,
        int version,
        Instant createdAt,
        Instant updatedAt) {
}
