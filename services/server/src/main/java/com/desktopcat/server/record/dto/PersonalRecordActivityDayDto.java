package com.desktopcat.server.record.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;

/** 写作足迹中一天的记录数量。 */
public record PersonalRecordActivityDayDto(
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        LocalDate recordDate,
        long recordCount) {
}
