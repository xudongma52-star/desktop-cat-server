package com.desktopcat.server.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.desktopcat.server.identity.security.DesktopAuthStateStore.AccessIdentity;
import com.desktopcat.server.identity.security.DesktopAuthStateStore.AuthorizationState;
import com.desktopcat.server.identity.security.RedisDesktopAuthStateStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RedisDesktopAuthStateStoreTest {
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private RedisDesktopAuthStateStore store;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        store = new RedisDesktopAuthStateStore(
                redisTemplate,
                new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void storesAndAtomicallyConsumesAuthorizationCode() {
        String codeHash = "code-hash";
        Duration lifetime = Duration.ofMinutes(2);
        AuthorizationState authorization = new AuthorizationState(
                17L,
                "http://127.0.0.1:49152/callback",
                "challenge",
                "MAX-PC",
                "win32",
                "0.3.0",
                Instant.parse("2026-09-20T04:00:00Z"));
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);

        store.saveAuthorization(codeHash, authorization, lifetime);
        verify(valueOperations).set(
                eq("desktop-cat:auth:authorization-code:" + codeHash),
                payload.capture(),
                eq(lifetime));

        when(valueOperations.getAndDelete("desktop-cat:auth:authorization-code:" + codeHash))
                .thenReturn(payload.getValue());
        assertThat(store.takeAuthorization(codeHash)).contains(authorization);
    }

    @Test
    void storesFindsAndDeletesAccessToken() {
        String tokenHash = "token-hash";
        Duration lifetime = Duration.ofMinutes(15);
        AccessIdentity identity = new AccessIdentity(
                17L,
                "MaxCat",
                Instant.parse("2026-09-20T04:15:00Z"));
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);

        store.saveAccessToken(tokenHash, identity, lifetime);
        verify(valueOperations).set(
                eq("desktop-cat:auth:access-token:" + tokenHash),
                payload.capture(),
                eq(lifetime));

        when(valueOperations.get("desktop-cat:auth:access-token:" + tokenHash))
                .thenReturn(payload.getValue());
        assertThat(store.findAccessToken(tokenHash)).contains(identity);

        store.deleteAccessToken(tokenHash);
        verify(redisTemplate).delete("desktop-cat:auth:access-token:" + tokenHash);
    }
}
