package com.desktopcat.server.capture.dto;

import java.time.Instant;

public record CaptureItemDto(
        String captureId,
        String content,
        String imageUrl,
        Instant capturedAt,
        int version,
        Instant createdAt,
        Instant updatedAt) {
}
