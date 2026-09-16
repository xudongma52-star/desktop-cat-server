package com.desktopcat.server.record.dto;

import java.time.LocalDate;

/** 修改文章时由 Controller 传给 Service 的数据，version 用于乐观锁。 */
public record PersonalRecordUpdateDto(
        String recordType,
        String title,
        String content,
        LocalDate recordDate,
        String mood,
        Boolean recallEnabled,
        Boolean ragEnabled,
        Integer version) {
}
