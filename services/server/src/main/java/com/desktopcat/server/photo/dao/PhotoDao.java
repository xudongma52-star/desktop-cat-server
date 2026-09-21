package com.desktopcat.server.photo.dao;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PhotoDao {

    int insertPhoto(PhotoDO photo);

    List<PhotoDO> selectActiveByUserId(@Param("userId") long userId);

    PhotoDO selectActiveByIdAndUserId(
            @Param("photoId") long photoId, @Param("userId") long userId);

    int logicalDelete(@Param("photoId") long photoId, @Param("userId") long userId);
}
