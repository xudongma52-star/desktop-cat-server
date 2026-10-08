package com.desktopcat.server.record.dao;

/** 正式记录与原始碎片的持久化关系。 */
public class PersonalRecordCaptureSourceDO {
    private Long recordId;
    private String captureId;

    public PersonalRecordCaptureSourceDO() {
    }

    public PersonalRecordCaptureSourceDO(Long recordId, String captureId) {
        this.recordId = recordId;
        this.captureId = captureId;
    }

    public Long getRecordId() { return recordId; }
    public void setRecordId(Long recordId) { this.recordId = recordId; }
    public String getCaptureId() { return captureId; }
    public void setCaptureId(String captureId) { this.captureId = captureId; }
}
