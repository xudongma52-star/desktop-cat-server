package com.desktopcat.server.emotion.dao;

import java.time.Instant;
import java.time.LocalDate;

public class EmotionDO {
    private Long emotionId;
    private Long userId;
    private String content;
    private LocalDate recordDate;
    private Instant createdAt;

    public EmotionDO() {
    }

    public EmotionDO(
            Long emotionId, Long userId, String content, LocalDate recordDate, Instant createdAt) {
        this.emotionId = emotionId;
        this.userId = userId;
        this.content = content;
        this.recordDate = recordDate;
        this.createdAt = createdAt;
    }

    public Long getEmotionId() {
        return emotionId;
    }

    public void setEmotionId(Long emotionId) {
        this.emotionId = emotionId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public LocalDate getRecordDate() {
        return recordDate;
    }

    public void setRecordDate(LocalDate recordDate) {
        this.recordDate = recordDate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
