package com.desktopcat.server.knowledge.dto;

import java.util.List;

/** 知识检索结果；回答只允许基于同时返回的可信原文片段生成。 */
public record KnowledgeSearchResultDto(
        int searchableRecordCount,
        boolean candidateLimitReached,
        String answer,
        boolean answerGenerated,
        List<KnowledgeMatchDto> matches) {
}
