package com.desktopcat.server.knowledge.dto;

import java.util.List;

/** 按消息主键向前翻页的知识库消息。 */
public record KnowledgeMessagePageDto(
        List<KnowledgeMessageDto> items,
        boolean hasMore,
        Long nextBeforeMessageId) {
}
