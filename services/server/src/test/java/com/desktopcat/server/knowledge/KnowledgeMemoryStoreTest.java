package com.desktopcat.server.knowledge;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.desktopcat.server.knowledge.service.KnowledgeMemoryStore;
import com.desktopcat.server.knowledge.dao.KnowledgeChatDO;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class KnowledgeMemoryStoreTest {

    @Test
    void restoresDurableSummaryWhenRedisIsUnavailable() throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.executePipelined(any(SessionCallback.class)))
                .thenThrow(new IllegalStateException("Redis unavailable"));
        var chat = new KnowledgeChatDO();
        chat.setChatId(12L);
        chat.setMemorySummary("{\"topic\":\"项目A\"}");
        chat.setSummaryThroughMessageId(80L);
        var store = new KnowledgeMemoryStore(redis, new ObjectMapper());

        assertThat(store.loadSummary(7, chat).path("topic").asText()).isEqualTo("项目A");
    }

    @Test
    void staleCachedSummaryCannotReplaceDatabaseCheckpoint() throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.executePipelined(any(SessionCallback.class))).thenReturn(List.of(
                "{\"throughMessageId\":20,\"content\":{\"topic\":\"旧主题\"}}", true));
        var chat = new KnowledgeChatDO();
        chat.setChatId(12L);
        chat.setMemorySummary("{\"topic\":\"新主题\"}");
        chat.setSummaryThroughMessageId(80L);
        var store = new KnowledgeMemoryStore(redis, new ObjectMapper());

        assertThat(store.loadSummary(7, chat).path("topic").asText()).isEqualTo("新主题");
    }

    @Test
    @SuppressWarnings("unchecked")
    void invalidatesOnlyOwnedConversationKeysAndAddsShortMissingMarker() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        RedisOperations<String, String> operations = mock(RedisOperations.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(operations.opsForValue()).thenReturn(values);
        when(redis.executePipelined(any(SessionCallback.class))).thenAnswer(invocation -> {
            SessionCallback<Object> callback = invocation.getArgument(0);
            callback.execute(operations);
            return List.of();
        });
        KnowledgeMemoryStore store = new KnowledgeMemoryStore(redis, new ObjectMapper());

        store.invalidateDeleted(7L, 12L);

        verify(operations).delete(List.of(
                "desktop-cat:knowledge-memory:7:12:messages:v2",
                "desktop-cat:knowledge-memory:7:12:version:v2",
                "desktop-cat:knowledge-memory:7:12:summary"));
        verify(values).set("desktop-cat:knowledge-memory:7:12:missing", "1", Duration.ofSeconds(30));
    }

    @Test
    void redisFailureDoesNotUndoSuccessfulDatabaseDeletion() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.executePipelined(any(SessionCallback.class)))
                .thenThrow(new IllegalStateException("Redis unavailable"));
        KnowledgeMemoryStore store = new KnowledgeMemoryStore(redis, new ObjectMapper());

        assertThatCode(() -> store.invalidateDeleted(7L, 12L)).doesNotThrowAnyException();
    }
}
