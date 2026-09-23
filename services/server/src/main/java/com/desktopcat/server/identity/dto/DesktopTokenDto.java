package com.desktopcat.server.identity.dto;

import java.time.Instant;
import java.util.UUID;

public record DesktopTokenDto(
        String accessToken,
        Instant accessTokenExpiresAt,
        UUID deviceId,
        String deviceCredential,
        Long userId,
        String username) {
}
