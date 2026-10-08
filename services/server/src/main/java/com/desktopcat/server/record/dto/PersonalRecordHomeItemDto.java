package com.desktopcat.server.record.dto;

import java.time.Instant;

/** 首页一组文字与配图，不传输文章全文。 */
public record PersonalRecordHomeItemDto(
        long recordId, String title, String excerpt, String imageUrl, Instant createdAt) {
}
