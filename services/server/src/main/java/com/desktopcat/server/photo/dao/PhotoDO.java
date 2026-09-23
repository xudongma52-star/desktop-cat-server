package com.desktopcat.server.photo.dao;

import java.time.Instant;

/** 与 photo 表字段对应的数据库对象。 */
public class PhotoDO {
    private Long photoId;
    private Long userId;
    private String storageKey;
    private Instant createdAt;
    private Instant deletedAt;

    public PhotoDO() {
    }

    public PhotoDO(Long photoId, Long userId, String storageKey, Instant createdAt, Instant deletedAt) {
        this.photoId = photoId;
        this.userId = userId;
        this.storageKey = storageKey;
        this.createdAt = createdAt;
        this.deletedAt = deletedAt;
    }

    public Long getPhotoId() {
        return photoId;
    }

    public void setPhotoId(Long photoId) {
        this.photoId = photoId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public void setStorageKey(String storageKey) {
        this.storageKey = storageKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }
}
