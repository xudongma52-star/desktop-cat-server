package com.desktopcat.server.knowledge.dto;

import java.util.List;

/** 知识检索结果；第一版只返回相关原文，不生成大模型回答。 */
public record KnowledgeSearchResultDto(
        int searchableRecordCount,
        boolean candidateLimitReached,
        List<KnowledgeMatchDto> matches) {
}
