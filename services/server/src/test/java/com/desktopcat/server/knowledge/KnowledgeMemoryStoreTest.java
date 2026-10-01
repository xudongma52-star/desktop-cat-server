package com.desktopcat.server.knowledge;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.desktopcat.server.knowledge.service.KnowledgeMemoryStore;
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
                "desktop-cat:knowledge-memory:7:12:messages",
                "desktop-cat:knowledge-memory:7:12:version"));
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
