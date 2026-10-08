package com.desktopcat.server.capture.dto;

public record CaptureAiItemDto(
        String captureId,
        String content,
        String imageMimeType,
        String imageBase64) {
}
