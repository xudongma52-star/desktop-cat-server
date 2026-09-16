package com.desktopcat.server.record.controller;

import com.desktopcat.server.record.dao.PersonalRecordDO;
import java.time.Instant;
import java.time.LocalDate;

public record PersonalRecordListItemResponse(
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
    private static final int EXCERPT_LENGTH = 160;

    public static PersonalRecordListItemResponse from(PersonalRecordDO record) {
        return new PersonalRecordListItemResponse(
                record.getRecordId(),
                record.getRecordType(),
                record.getTitle(),
                createExcerpt(record.getContent()),
                record.getRecordDate(),
                record.getMood(),
                Boolean.TRUE.equals(record.getRecallEnabled()),
                Boolean.TRUE.equals(record.getRagEnabled()),
                record.getVersion(),
                record.getCreatedAt(),
                record.getUpdatedAt());
    }

    private static String createExcerpt(String content) {
        String normalized = content.strip().replaceAll("\\s+", " ");
        int length = normalized.codePointCount(0, normalized.length());
        if (length <= EXCERPT_LENGTH) {
            return normalized;
        }
        int endIndex = normalized.offsetByCodePoints(0, EXCERPT_LENGTH);
        return normalized.substring(0, endIndex) + "…";
    }
}
