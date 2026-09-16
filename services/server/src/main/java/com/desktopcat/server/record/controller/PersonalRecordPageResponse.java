package com.desktopcat.server.record.controller;

import com.desktopcat.server.record.service.PersonalRecordPage;
import java.util.List;

public record PersonalRecordPageResponse(
        List<PersonalRecordListItemResponse> items,
        int page,
        int pageSize,
        long total,
        long totalPages) {

    public static PersonalRecordPageResponse from(PersonalRecordPage result) {
        return new PersonalRecordPageResponse(
                result.items().stream().map(PersonalRecordListItemResponse::from).toList(),
                result.page(), result.pageSize(), result.total(), result.totalPages());
    }
}
