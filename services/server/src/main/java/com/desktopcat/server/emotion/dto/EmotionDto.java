package com.desktopcat.server.emotion.dto;

import java.time.Instant;
import java.time.LocalDate;

public record EmotionDto(
        long emotionId,
        String content,
        LocalDate recordDate,
        Instant createdAt) {
}
