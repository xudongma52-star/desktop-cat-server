package com.desktopcat.server.catprofile;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CatProfileMapper {
    CatProfile selectPrimaryProfile();

    int updatePrimaryName(
            @Param("profileId") long profileId,
            @Param("catName") String catName,
            @Param("version") int version);
}
