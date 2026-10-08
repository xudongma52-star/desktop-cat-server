package com.desktopcat.server.knowledge.dao;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface KnowledgeChatDao {
    int insertChat(KnowledgeChatDO chat);

    KnowledgeChatDO selectActiveById(
            @Param("userId") long userId,
            @Param("chatId") long chatId);

    List<KnowledgeChatDO> selectRecent(
            @Param("userId") long userId,
            @Param("limit") int limit);

    int updateTitle(
            @Param("userId") long userId,
            @Param("chatId") long chatId,
            @Param("title") String title);

    int softDelete(
            @Param("userId") long userId,
            @Param("chatId") long chatId,
            @Param("deletedAt") Instant deletedAt);

    int updateAfterMessage(
            @Param("userId") long userId,
            @Param("chatId") long chatId,
            @Param("version") int version,
            @Param("updatedAt") Instant updatedAt,
            @Param("memory") KnowledgeChatDO memory);
}
