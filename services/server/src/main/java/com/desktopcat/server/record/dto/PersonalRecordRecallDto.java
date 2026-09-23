package com.desktopcat.server.record.dto;

import java.time.LocalDate;

/** 首页温馨回忆轮播使用的文章摘要数据。 */
public record PersonalRecordRecallDto(
        long recordId,
        String recordType,
        String title,
        String excerpt,
        String mood,
        LocalDate recordDate) {
}
