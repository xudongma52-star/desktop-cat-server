package com.desktopcat.server.record.controller;

import com.desktopcat.server.record.dao.PersonalRecordDO;
import java.time.Instant;
import java.time.LocalDate;

public record PersonalRecordResponse(
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

    public static PersonalRecordResponse from(PersonalRecordDO record) {
        return new PersonalRecordResponse(
                record.getRecordId(),
                record.getRecordType(),
                record.getTitle(),
                record.getContent(),
                record.getRecordDate(),
                record.getMood(),
                Boolean.TRUE.equals(record.getRecallEnabled()),
                Boolean.TRUE.equals(record.getRagEnabled()),
                record.getVersion(),
                record.getCreatedAt(),
                record.getUpdatedAt());
    }
}
