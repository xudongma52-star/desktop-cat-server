package com.desktopcat.server.photo.dto;

import java.time.Instant;

public record PhotoDto(Long photoId, String contentUrl, Instant createdAt) {
}
