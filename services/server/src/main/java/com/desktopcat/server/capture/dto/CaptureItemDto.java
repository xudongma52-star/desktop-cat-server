package com.desktopcat.server.capture.dto;

import java.time.Instant;

public record CaptureItemDto(
        String captureId,
        String content,
        String imageUrl,
        String classificationTarget,
        String classificationOrigin,
        String recordResolution,
        Instant capturedAt,
        int version,
        Instant createdAt,
        Instant updatedAt) {
}
