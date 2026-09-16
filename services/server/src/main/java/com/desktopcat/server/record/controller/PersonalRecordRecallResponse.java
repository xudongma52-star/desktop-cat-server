package com.desktopcat.server.record.controller;

import com.desktopcat.server.record.service.PersonalRecordRecall;
import java.time.LocalDate;

public record PersonalRecordRecallResponse(
        long recordId,
        String recordType,
        String title,
        String excerpt,
        String mood,
        LocalDate recordDate) {

    public static PersonalRecordRecallResponse from(PersonalRecordRecall recall) {
        return new PersonalRecordRecallResponse(
                recall.recordId(), recall.recordType(), recall.title(),
                recall.excerpt(), recall.mood(), recall.recordDate());
    }
}
