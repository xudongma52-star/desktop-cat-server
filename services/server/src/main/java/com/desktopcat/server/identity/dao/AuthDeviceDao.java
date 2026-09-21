package com.desktopcat.server.identity.dao;

import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AuthDeviceDao {

    int insertDevice(AuthDeviceDO device);

    AuthDeviceDO selectById(@Param("deviceId") String deviceId);

    int updateLastUsedIfActive(
            @Param("deviceId") String deviceId,
            @Param("usedAt") Instant usedAt);

    int revokeByIdAndUserId(
            @Param("deviceId") String deviceId,
            @Param("userId") long userId,
            @Param("revokedAt") Instant revokedAt);
}
