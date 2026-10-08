package com.desktopcat.server.capture.dto;

import java.time.LocalDate;
import java.util.List;

public record CaptureDailyPageDto(
        LocalDate date,
        List<CaptureItemDto> items,
        int page,
        long totalDays) {
}
