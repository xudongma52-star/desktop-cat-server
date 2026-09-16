package com.desktopcat.server.record.controller;

import com.desktopcat.server.record.dao.PersonalRecordDO;
import java.time.LocalDate;

public record CreatePersonalRecordRequest(
        String recordType,
        String title,
        String content,
        LocalDate recordDate,
        String mood,
        Boolean recallEnabled,
        Boolean ragEnabled) {

    public PersonalRecordDO toDataObject() {
        return new PersonalRecordDO(
                null, recordType, title, content, recordDate, mood,
                recallEnabled, ragEnabled, null, null, null);
    }
}
