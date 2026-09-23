package com.desktopcat.server.identity.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("postgres")
public class RedisDesktopAuthStateStore implements DesktopAuthStateStore {
    private static final String AUTHORIZATION_PREFIX = "desktop-cat:auth:authorization-code:";
    private static final String ACCESS_TOKEN_PREFIX = "desktop-cat:auth:access-token:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisDesktopAuthStateStore(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void saveAuthorization(
            String codeHash,
            AuthorizationState authorization,
            Duration lifetime) {
        save(AUTHORIZATION_PREFIX + codeHash, authorization, lifetime);
    }

    @Override
    public Optional<AuthorizationState> takeAuthorization(String codeHash) {
        String payload = redisTemplate.opsForValue()
                .getAndDelete(AUTHORIZATION_PREFIX + codeHash);
        return read(payload, AuthorizationState.class);
    }

    @Override
    public void saveAccessToken(
            String tokenHash,
            AccessIdentity identity,
            Duration lifetime) {
        save(ACCESS_TOKEN_PREFIX + tokenHash, identity, lifetime);
    }

    @Override
    public Optional<AccessIdentity> findAccessToken(String tokenHash) {
        String payload = redisTemplate.opsForValue().get(ACCESS_TOKEN_PREFIX + tokenHash);
        return read(payload, AccessIdentity.class);
    }

    @Override
    public void deleteAccessToken(String tokenHash) {
        redisTemplate.delete(ACCESS_TOKEN_PREFIX + tokenHash);
    }

    private void save(String key, Object value, Duration lifetime) {
        try {
            redisTemplate.opsForValue().set(
                    key,
                    objectMapper.writeValueAsString(value),
                    lifetime);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Desktop authentication state could not be serialized.", exception);
        }
    }

    private <T> Optional<T> read(String payload, Class<T> type) {
        if (payload == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(payload, type));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Desktop authentication state could not be read.", exception);
        }
    }
}
