package com.desktopcat.server.capture.dao;

import java.time.Instant;

/** 与 capture_item 表对应的持久化对象。 */
public class CaptureItemDO {
    private String captureId;
    private Long userId;
    private String content;
    private String imageStorageKey;
    private Instant capturedAt;
    private Integer version;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;

    public String getCaptureId() { return captureId; }
    public void setCaptureId(String captureId) { this.captureId = captureId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getImageStorageKey() { return imageStorageKey; }
    public void setImageStorageKey(String imageStorageKey) { this.imageStorageKey = imageStorageKey; }
    public Instant getCapturedAt() { return capturedAt; }
    public void setCapturedAt(Instant capturedAt) { this.capturedAt = capturedAt; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }
}
