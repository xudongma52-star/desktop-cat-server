package com.desktopcat.server.capture.dto;

import java.time.LocalDate;
import java.util.List;

public record CaptureArticleDraftDto(
        String title,
        String content,
        LocalDate recordDate,
        List<String> captureIds) {
}
