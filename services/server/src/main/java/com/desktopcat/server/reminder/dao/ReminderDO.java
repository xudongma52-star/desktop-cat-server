package com.desktopcat.server.reminder.dao;

import java.time.Instant;

/** 与 reminder 表字段对应的数据库对象，仅供 DAO 和 Service 实现层使用。 */
public class ReminderDO {
    private Long reminderId;
    private String content;
    private Instant remindAt;
    private String status;
    private Instant completedAt;
    private Integer version;
    private Instant createdAt;
    private Instant updatedAt;

    public ReminderDO() {
    }

    public ReminderDO(Long reminderId, String content, Instant remindAt, String status,
                      Instant completedAt, Integer version, Instant createdAt, Instant updatedAt) {
        this.reminderId = reminderId;
        this.content = content;
        this.remindAt = remindAt;
        this.status = status;
        this.completedAt = completedAt;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getReminderId() {
        return reminderId;
    }

    public void setReminderId(Long reminderId) {
        this.reminderId = reminderId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Instant getRemindAt() {
        return remindAt;
    }

    public void setRemindAt(Instant remindAt) {
        this.remindAt = remindAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
