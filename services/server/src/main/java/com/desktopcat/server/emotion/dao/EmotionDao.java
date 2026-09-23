package com.desktopcat.server.emotion.dao;

import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface EmotionDao {
    int insert(EmotionDO emotion);

    List<EmotionDO> selectByDate(
            @Param("userId") long userId,
            @Param("recordDate") LocalDate recordDate);
}
