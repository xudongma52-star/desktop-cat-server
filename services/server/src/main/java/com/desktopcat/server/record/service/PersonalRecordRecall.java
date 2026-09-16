package com.desktopcat.server.record.service;

import java.time.LocalDate;

public record PersonalRecordRecall(
        long recordId,
        String recordType,
        String title,
        String excerpt,
        String mood,
        LocalDate recordDate) {
}
