package com.desktopcat.server.identity.dao;

import java.time.Instant;

/**
 * 与 app_user 表字段对应的数据库对象。
 */
public record AppUserDO(
    Long userId,
    String username,
    String passwordHash,
    String status,
    Instant createdAt,
    Instant updatedAt) {
}
