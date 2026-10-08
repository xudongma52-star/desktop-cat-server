package com.desktopcat.server.knowledge.dao;

import java.time.Instant;

/** 与 knowledge_chat 表对应的持久化对象。 */
public class KnowledgeChatDO {
    private Long chatId;
    private Long userId;
    private String title;
    private Integer version;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;
    private String memorySummary;
    private Long summaryThroughMessageId;

    public Long getChatId() { return chatId; }
    public void setChatId(Long chatId) { this.chatId = chatId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }
    public String getMemorySummary() { return memorySummary; }
    public void setMemorySummary(String memorySummary) { this.memorySummary = memorySummary; }
    public Long getSummaryThroughMessageId() { return summaryThroughMessageId; }
    public void setSummaryThroughMessageId(Long summaryThroughMessageId) {
        this.summaryThroughMessageId = summaryThroughMessageId;
    }
}
