package com.desktopcat.server.knowledge.dto;

import java.util.List;

/** 知识检索结果；回答只允许基于同时返回的可信原文片段生成。
 * candidateLimitReached 保留供旧版客户端读取，完整分页后固定为 false。
 */
public record KnowledgeSearchResultDto(
        int searchableRecordCount,
        boolean candidateLimitReached,
        String answer,
        boolean answerGenerated,
        List<KnowledgeMatchDto> matches) {
}
