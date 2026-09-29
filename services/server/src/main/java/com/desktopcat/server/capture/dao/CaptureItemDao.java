package com.desktopcat.server.capture.dao;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CaptureItemDao {
    int insert(CaptureItemDO item);

    CaptureItemDO selectById(@Param("userId") long userId, @Param("captureId") String captureId);

    List<CaptureItemDO> selectActivePage(
            @Param("userId") long userId,
            @Param("query") String query,
            @Param("offset") int offset,
            @Param("limit") int limit);

    long countActive(@Param("userId") long userId, @Param("query") String query);

    int updateContent(
            @Param("userId") long userId,
            @Param("captureId") String captureId,
            @Param("content") String content,
            @Param("version") int version);

    int logicalDelete(
            @Param("userId") long userId,
            @Param("captureId") String captureId,
            @Param("version") int version);
}
