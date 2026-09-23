package com.desktopcat.server.record.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;
import java.util.List;

/** 指定日期范围内的写作足迹汇总。 */
public record PersonalRecordActivityDto(
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        LocalDate startDate,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        LocalDate endDate,
        long totalRecords,
        int activeDays,
        List<PersonalRecordActivityDayDto> days) {
}
