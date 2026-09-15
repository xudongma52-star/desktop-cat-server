package com.desktopcat.server.catprofile.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CatProfileDao {
    CatProfileDO selectPrimaryProfile();

    int updatePrimaryName(
            @Param("profileId") long profileId,
            @Param("catName") String catName,
            @Param("version") int version);
}
