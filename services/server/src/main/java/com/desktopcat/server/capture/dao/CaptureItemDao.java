package com.desktopcat.server.capture.dao;

import java.time.Instant;
import java.time.LocalDate;
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

    List<LocalDate> selectActiveDates(
            @Param("userId") long userId,
            @Param("offset") int offset,
            @Param("limit") int limit);

    long countActiveDates(@Param("userId") long userId);

    List<CaptureItemDO> selectActiveByTimeRange(
            @Param("userId") long userId,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime,
            @Param("classificationTarget") String classificationTarget,
            @Param("unclassifiedOnly") boolean unclassifiedOnly);

    int classifyIfUnclassified(
            @Param("userId") long userId,
            @Param("captureId") String captureId,
            @Param("classificationTarget") String classificationTarget,
            @Param("classificationOrigin") String classificationOrigin,
            @Param("version") int version);

    int resolveRecord(
            @Param("userId") long userId,
            @Param("captureId") String captureId,
            @Param("recordResolution") String recordResolution,
            @Param("version") int version);

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
