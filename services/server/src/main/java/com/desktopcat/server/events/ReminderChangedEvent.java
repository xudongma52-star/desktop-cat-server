package com.desktopcat.server.events;

/** 提醒事务提交后广播给客户端的变更摘要。 */
public record ReminderChangedEvent(long reminderId, int version, String action) {
}
