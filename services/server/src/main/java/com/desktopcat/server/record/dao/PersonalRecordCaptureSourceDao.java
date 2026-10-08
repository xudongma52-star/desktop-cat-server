package com.desktopcat.server.record.dao;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PersonalRecordCaptureSourceDao {
    int insert(PersonalRecordCaptureSourceDO source);

    List<String> selectCaptureIdsByRecordId(@Param("recordId") long recordId);
}
