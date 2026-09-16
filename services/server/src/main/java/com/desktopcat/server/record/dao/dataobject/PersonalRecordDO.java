package com.desktopcat.server.record.dao.dataobject;

import java.time.Instant;
import java.time.LocalDate;

/** 与 personal_record 表字段对应的数据库对象，仅供 DAO 和 Service 实现层使用。 */
public class PersonalRecordDO {
    private Long recordId;
    private String recordType;
    private String title;
    private String content;
    private LocalDate recordDate;
    private String mood;
    private Boolean recallEnabled;
    private Boolean ragEnabled;
    private Integer version;
    private Instant createdAt;
    private Instant updatedAt;

    public PersonalRecordDO() {
    }

    public PersonalRecordDO(
            Long recordId,
            String recordType,
            String title,
            String content,
            LocalDate recordDate,
            String mood,
            Boolean recallEnabled,
            Boolean ragEnabled,
            Integer version,
            Instant createdAt,
            Instant updatedAt) {
        this.recordId = recordId;
        this.recordType = recordType;
        this.title = title;
        this.content = content;
        this.recordDate = recordDate;
        this.mood = mood;
        this.recallEnabled = recallEnabled;
        this.ragEnabled = ragEnabled;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getRecordId() {
        return recordId;
    }

    public void setRecordId(Long recordId) {
        this.recordId = recordId;
    }

    public String getRecordType() {
        return recordType;
    }

    public void setRecordType(String recordType) {
        this.recordType = recordType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
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

    public String getMood() {
        return mood;
    }

    public void setMood(String mood) {
        this.mood = mood;
    }

    public Boolean getRecallEnabled() {
        return recallEnabled;
    }

    public void setRecallEnabled(Boolean recallEnabled) {
        this.recallEnabled = recallEnabled;
    }

    public Boolean getRagEnabled() {
        return ragEnabled;
    }

    public void setRagEnabled(Boolean ragEnabled) {
        this.ragEnabled = ragEnabled;
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
