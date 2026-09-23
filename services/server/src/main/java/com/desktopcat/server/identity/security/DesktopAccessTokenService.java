package com.desktopcat.server.identity.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("postgres")
public class DesktopAccessTokenService {
    private static final Duration TOKEN_LIFETIME = Duration.ofMinutes(15);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final DesktopAuthStateStore authStateStore;

    public DesktopAccessTokenService(DesktopAuthStateStore authStateStore) {
        this.authStateStore = authStateStore;
    }

    public IssuedAccessToken issue(long userId, String username) {
        Instant now = Instant.now();

        String token = "dcat_" + randomValue(32);
        Instant expiresAt = now.plus(TOKEN_LIFETIME);
        authStateStore.saveAccessToken(
                hash(token),
                new DesktopAuthStateStore.AccessIdentity(userId, username, expiresAt),
                TOKEN_LIFETIME);
        return new IssuedAccessToken(token, expiresAt);
    }

    public Optional<AccessIdentity> authenticate(String token) {
        if (token == null || !token.startsWith("dcat_")) {
            return Optional.empty();
        }

        String tokenHash = hash(token);
        DesktopAuthStateStore.AccessIdentity identity = authStateStore
                .findAccessToken(tokenHash)
                .orElse(null);
        Instant now = Instant.now();
        if (identity == null) {
            return Optional.empty();
        }
        if (!identity.expiresAt().isAfter(now)) {
            authStateStore.deleteAccessToken(tokenHash);
            return Optional.empty();
        }
        return Optional.of(new AccessIdentity(
                identity.userId(), identity.username(), identity.expiresAt()));
    }

    private static String randomValue(int byteCount) {
        byte[] bytes = new byte[byteCount];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    public record IssuedAccessToken(String value, Instant expiresAt) {
    }

    public record AccessIdentity(long userId, String username, Instant expiresAt) {
    }
}
