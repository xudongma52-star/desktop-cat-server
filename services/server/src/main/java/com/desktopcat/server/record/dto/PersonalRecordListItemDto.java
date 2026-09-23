package com.desktopcat.server.record.dto;

import java.time.Instant;
import java.time.LocalDate;

/** 文章列表中的单条摘要数据，不包含完整正文。 */
public record PersonalRecordListItemDto(
        long recordId,
        String recordType,
        String title,
        String excerpt,
        LocalDate recordDate,
        String mood,
        boolean recallEnabled,
        boolean ragEnabled,
        int version,
        Instant createdAt,
        Instant updatedAt) {
}
