package com.desktopcat.server.record.dto;

import java.time.Instant;
import java.time.LocalDate;

/** 创建、详情和修改接口共用的完整文章数据。 */
public record PersonalRecordDetailDto(
        long recordId,
        String recordType,
        String title,
        String content,
        LocalDate recordDate,
        String mood,
        boolean recallEnabled,
        boolean ragEnabled,
        int version,
        Instant createdAt,
        Instant updatedAt) {
}
