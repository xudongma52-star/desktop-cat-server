package com.desktopcat.server.reminder.dto;

import java.time.Instant;

public record ReminderUpdateDto(String content, Instant remindAt, Integer version) {
}
