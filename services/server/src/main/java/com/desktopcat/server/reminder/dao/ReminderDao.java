package com.desktopcat.server.reminder.dao;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ReminderDao {
    int insertReminder(ReminderDO reminder);

    ReminderDO selectActiveById(@Param("reminderId") long reminderId);

    List<ReminderDO> selectActiveList(
            @Param("status") String status,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime);

    int updateReminder(ReminderDO reminder);

    int completeReminder(
            @Param("reminderId") long reminderId,
            @Param("version") int version);

    int logicalDelete(
            @Param("reminderId") long reminderId,
            @Param("version") int version);
}
