package com.desktopcat.server.identity.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public interface DesktopAuthStateStore {
    void saveAuthorization(
            String codeHash,
            AuthorizationState authorization,
            Duration lifetime);

    Optional<AuthorizationState> takeAuthorization(String codeHash);

    void saveAccessToken(
            String tokenHash,
            AccessIdentity identity,
            Duration lifetime);

    Optional<AccessIdentity> findAccessToken(String tokenHash);

    void deleteAccessToken(String tokenHash);

    record AuthorizationState(
            long userId,
            String redirectUri,
            String codeChallenge,
            String deviceName,
            String platform,
            String appVersion,
            Instant expiresAt) {
    }

    record AccessIdentity(long userId, String username, Instant expiresAt) {
    }
}
