package com.desktopcat.server.capture.dto;

import java.time.LocalDate;
import java.util.List;

public record CaptureArticleCreateDto(
        List<String> captureIds,
        String title,
        String content,
        LocalDate recordDate,
        Boolean ragEnabled) {
}
