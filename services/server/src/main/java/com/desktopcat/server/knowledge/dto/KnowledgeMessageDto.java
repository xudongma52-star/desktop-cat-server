package com.desktopcat.server.knowledge.dto;

import java.time.Instant;
import java.util.List;

/** 知识库对话中的一条用户或助手消息。 */
public record KnowledgeMessageDto(
        long messageId,
        String role,
        String content,
        List<KnowledgeMatchDto> sources,
        Instant createdAt) {
}
