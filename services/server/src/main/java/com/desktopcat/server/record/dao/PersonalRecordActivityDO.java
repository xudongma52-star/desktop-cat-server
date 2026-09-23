package com.desktopcat.server.record.dao;

import java.time.LocalDate;

/** 按记录日期聚合的写作数量，只承载 DAO 查询结果。 */
public class PersonalRecordActivityDO {
    private LocalDate recordDate;
    private Long recordCount;

    public PersonalRecordActivityDO() {
    }

    public PersonalRecordActivityDO(LocalDate recordDate, Long recordCount) {
        this.recordDate = recordDate;
        this.recordCount = recordCount;
    }

    public LocalDate getRecordDate() {
        return recordDate;
    }

    public void setRecordDate(LocalDate recordDate) {
        this.recordDate = recordDate;
    }

    public Long getRecordCount() {
        return recordCount;
    }

    public void setRecordCount(Long recordCount) {
        this.recordCount = recordCount;
    }
}
