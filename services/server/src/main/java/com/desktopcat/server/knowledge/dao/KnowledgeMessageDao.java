package com.desktopcat.server.knowledge.dao;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface KnowledgeMessageDao {
    int insertMessage(KnowledgeMessageDO message);

    List<KnowledgeMessageDO> selectRecentByChat(
            @Param("userId") long userId,
            @Param("chatId") long chatId,
            @Param("beforeMessageId") Long beforeMessageId,
            @Param("limit") int limit);
}
