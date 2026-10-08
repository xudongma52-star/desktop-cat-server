package com.desktopcat.server.record.dto;

import java.time.LocalDate;

/** 创建文章时由 Controller 传给 Service 的数据。 */
public record PersonalRecordCreateDto(
        String recordType,
        String title,
        String content,
        LocalDate recordDate,
        String mood,
        Boolean recallEnabled,
        Boolean ragEnabled,
        String coverImageKey,
        String homeExcerpt) {
    /** 随手记录归档等既有调用不携带首页配图。 */
    public PersonalRecordCreateDto(String recordType, String title, String content,
            LocalDate recordDate, String mood, Boolean recallEnabled, Boolean ragEnabled) {
        this(recordType, title, content, recordDate, mood, recallEnabled, ragEnabled, null, null);
    }
}
