package com.desktopcat.server.identity.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AppUserDao {

    AppUserDO selectById(@Param("userId") long userId);

    AppUserDO selectByUsername(@Param("username") String username);

    int insertUser(AppUserDO user);
}
