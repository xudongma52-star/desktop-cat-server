package com.desktopcat.server.reminder.dto;

import java.time.Instant;

public record ReminderDto(
        long reminderId,
        String content,
        Instant remindAt,
        String status,
        Instant completedAt,
        int version,
        Instant createdAt,
        Instant updatedAt) {
}
