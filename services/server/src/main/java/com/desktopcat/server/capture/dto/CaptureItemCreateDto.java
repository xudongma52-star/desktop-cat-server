package com.desktopcat.server.capture.dto;

import java.time.Instant;

public record CaptureItemCreateDto(String captureId, String content, Instant capturedAt) {
}
