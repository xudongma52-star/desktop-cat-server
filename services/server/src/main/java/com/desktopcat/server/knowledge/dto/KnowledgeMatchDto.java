package com.desktopcat.server.knowledge.dto;

import java.time.LocalDate;

/** 一段与问题相关的个人记录正文及其来源。 */
public record KnowledgeMatchDto(
        long recordId,
        String recordType,
        String title,
        String content,
        LocalDate recordDate,
        double score) {
}
