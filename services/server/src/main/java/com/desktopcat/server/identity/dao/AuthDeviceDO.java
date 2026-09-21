package com.desktopcat.server.identity.dao;

import java.time.Instant;

/**
 * 与 auth_device 表字段对应的数据库对象。
 */
public record AuthDeviceDO(
        String deviceId,
        Long userId,
        String credentialHash,
        String clientId,
        String deviceName,
        String platform,
        String appVersion,
        Instant expiresAt,
        Instant lastUsedAt,
        Instant revokedAt,
        Instant createdAt,
        Instant updatedAt) {
}
