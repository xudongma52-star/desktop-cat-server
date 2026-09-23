package com.desktopcat.server.reminder.dto;

import java.time.Instant;

public record ReminderCreateDto(String content, Instant remindAt) {
}
