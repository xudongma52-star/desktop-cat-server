package com.desktopcat.server.identity.application;

import com.desktopcat.server.identity.dao.AppUserDO;
import com.desktopcat.server.identity.dao.AppUserDao;
import com.desktopcat.server.identity.dao.AuthDeviceDO;
import com.desktopcat.server.identity.dao.AuthDeviceDao;
import com.desktopcat.server.identity.dto.DesktopAuthorizationDto;
import com.desktopcat.server.identity.dto.DesktopAuthorizationRequestDto;
import com.desktopcat.server.identity.dto.DesktopCodeExchangeRequestDto;
import com.desktopcat.server.identity.dto.DesktopTokenDto;
import com.desktopcat.server.identity.security.DesktopAccessTokenService;
import com.desktopcat.server.identity.security.DesktopAuthStateStore;
import com.desktopcat.server.identity.security.DesktopAuthStateStore.AuthorizationState;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@Profile("postgres")
public class DesktopAuthApplicationService {
    private static final Logger log = LoggerFactory.getLogger(DesktopAuthApplicationService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Duration AUTHORIZATION_CODE_LIFETIME = Duration.ofMinutes(2);
    private static final Duration DEVICE_LIFETIME = Duration.ofDays(90);
    private static final int MAX_DEVICE_NAME_LENGTH = 64;
    private static final int MAX_PLATFORM_LENGTH = 32;
    private static final int MAX_APP_VERSION_LENGTH = 32;

    private final AppUserDao appUserDao;
    private final AuthDeviceDao authDeviceDao;
    private final DesktopAccessTokenService accessTokenService;
    private final DesktopAuthStateStore authStateStore;

    public DesktopAuthApplicationService(
            AppUserDao appUserDao,
            AuthDeviceDao authDeviceDao,
            DesktopAccessTokenService accessTokenService,
            DesktopAuthStateStore authStateStore) {
        this.appUserDao = appUserDao;
        this.authDeviceDao = authDeviceDao;
        this.accessTokenService = accessTokenService;
        this.authStateStore = authStateStore;
    }

    public DesktopAuthorizationDto authorize(
            String username,
            DesktopAuthorizationRequestDto request) {
        AppUserDO user = requireActiveUser(username);
        if (request == null) {
            throw badRequest("Desktop authorization request is required.");
        }

        String redirectUri = validateRedirectUri(request.redirectUri());
        String codeChallenge = validateBase64Url(
                request.codeChallenge(), "PKCE code challenge is invalid.", 43, 43);
        String state = validateBase64Url(request.state(), "Authorization state is invalid.", 22, 128);
        String deviceName = validateText(
                request.deviceName(), "Device name is required.", MAX_DEVICE_NAME_LENGTH);
        String platform = validateText(
                request.platform(), "Device platform is required.", MAX_PLATFORM_LENGTH);
        String appVersion = normalizeOptionalText(
                request.appVersion(), "App version is too long.", MAX_APP_VERSION_LENGTH);

        Instant now = Instant.now();
        String code = randomValue(32);
        authStateStore.saveAuthorization(hash(code), new AuthorizationState(
                user.userId(),
                redirectUri,
                codeChallenge,
                deviceName,
                platform,
                appVersion,
                now.plus(AUTHORIZATION_CODE_LIFETIME)),
                AUTHORIZATION_CODE_LIFETIME);

        String callbackUrl = UriComponentsBuilder.fromUriString(redirectUri)
                .queryParam("code", code)
                .queryParam("state", state)
                .build()
                .encode()
                .toUriString();
        log.info("event=desktop_authorization_issued userId={} platform={}",
                user.userId(), platform);
        return new DesktopAuthorizationDto(callbackUrl);
    }

    @Transactional
    public DesktopTokenDto exchange(DesktopCodeExchangeRequestDto request) {
        if (request == null) {
            throw badRequest("Desktop token request is required.");
        }
        String code = requireText(request.code(), "Authorization code is required.");
        String verifier = validateBase64Url(
                request.codeVerifier(), "PKCE code verifier is invalid.", 43, 128);
        String redirectUri = validateRedirectUri(request.redirectUri());

        String codeHash = hash(code);
        AuthorizationState authorization = authStateStore.takeAuthorization(codeHash)
                .orElse(null);
        Instant now = Instant.now();
        if (authorization == null || !authorization.expiresAt().isAfter(now)) {
            throw unauthorized("Authorization code is invalid or expired.");
        }
        if (!authorization.redirectUri().equals(redirectUri)
                || !constantTimeEquals(authorization.codeChallenge(), pkceChallenge(verifier))) {
            throw unauthorized("Authorization code verification failed.");
        }

        AppUserDO user = appUserDao.selectById(authorization.userId());
        if (!isActive(user)) {
            throw unauthorized("User account is unavailable.");
        }

        UUID deviceId = UUID.randomUUID();
        String secret = randomValue(32);
        Instant deviceExpiresAt = now.plus(DEVICE_LIFETIME);
        AuthDeviceDO device = new AuthDeviceDO(
                deviceId.toString(),
                user.userId(),
                hash(secret),
                "DESKTOP",
                authorization.deviceName(),
                authorization.platform(),
                authorization.appVersion(),
                deviceExpiresAt,
                now,
                null,
                null,
                null);
        if (authDeviceDao.insertDevice(device) != 1) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Desktop device could not be authorized.");
        }

        DesktopAccessTokenService.IssuedAccessToken accessToken =
                accessTokenService.issue(user.userId(), user.username());
        log.info("event=desktop_device_authorized userId={} deviceId={} platform={}",
                user.userId(), deviceId, authorization.platform());
        return tokenResponse(accessToken, deviceId, deviceId + "." + secret, user);
    }

    @Transactional
    public DesktopTokenDto refresh(String deviceCredential) {
        ParsedDeviceCredential parsed = parseDeviceCredential(deviceCredential);
        AuthDeviceDO device = authDeviceDao.selectById(parsed.deviceId().toString());
        Instant now = Instant.now();
        if (device == null
                || device.revokedAt() != null
                || !device.expiresAt().isAfter(now)
                || !constantTimeEquals(device.credentialHash(), hash(parsed.secret()))) {
            throw unauthorized("Device credential is invalid or expired.");
        }

        AppUserDO user = appUserDao.selectById(device.userId());
        if (!isActive(user)) {
            throw unauthorized("User account is unavailable.");
        }
        if (authDeviceDao.updateLastUsedIfActive(device.deviceId(), now) != 1) {
            throw unauthorized("Device credential is invalid or expired.");
        }

        DesktopAccessTokenService.IssuedAccessToken accessToken =
                accessTokenService.issue(user.userId(), user.username());
        log.debug("event=desktop_access_token_refreshed userId={} deviceId={}",
                user.userId(), device.deviceId());
        return tokenResponse(accessToken, UUID.fromString(device.deviceId()), null, user);
    }

    @Transactional
    public void revoke(String deviceCredential) {
        ParsedDeviceCredential parsed = parseDeviceCredential(deviceCredential);
        AuthDeviceDO device = authDeviceDao.selectById(parsed.deviceId().toString());
        if (device == null
                || !constantTimeEquals(device.credentialHash(), hash(parsed.secret()))) {
            throw unauthorized("Device credential is invalid or expired.");
        }

        if (authDeviceDao.revokeByIdAndUserId(
                device.deviceId(), device.userId(), Instant.now()) == 1) {
            log.info("event=desktop_device_revoked userId={} deviceId={}",
                    device.userId(), device.deviceId());
        }
    }

    private DesktopTokenDto tokenResponse(
            DesktopAccessTokenService.IssuedAccessToken accessToken,
            UUID deviceId,
            String deviceCredential,
            AppUserDO user) {
        return new DesktopTokenDto(
                accessToken.value(),
                accessToken.expiresAt(),
                deviceId,
                deviceCredential,
                user.userId(),
                user.username());
    }

    private AppUserDO requireActiveUser(String username) {
        AppUserDO user = username == null ? null : appUserDao.selectByUsername(username);
        if (!isActive(user)) {
            throw unauthorized("User account is unavailable.");
        }
        return user;
    }

    private boolean isActive(AppUserDO user) {
        return user != null && "ACTIVE".equals(user.status());
    }

    private ParsedDeviceCredential parseDeviceCredential(String value) {
        String credential = requireText(value, "Device credential is required.");
        int separator = credential.indexOf('.');
        if (separator <= 0 || separator != credential.lastIndexOf('.')) {
            throw unauthorized("Device credential is invalid or expired.");
        }
        try {
            UUID deviceId = UUID.fromString(credential.substring(0, separator));
            String secret = credential.substring(separator + 1);
            if (secret.length() != 43 || !secret.matches("[A-Za-z0-9_-]+")) {
                throw unauthorized("Device credential is invalid or expired.");
            }
            return new ParsedDeviceCredential(deviceId, secret);
        } catch (IllegalArgumentException exception) {
            throw unauthorized("Device credential is invalid or expired.");
        }
    }

    private String validateRedirectUri(String value) {
        String redirectUri = requireText(value, "Redirect URI is required.");
        try {
            URI uri = new URI(redirectUri);
            boolean valid = "http".equalsIgnoreCase(uri.getScheme())
                    && "127.0.0.1".equals(uri.getHost())
                    && uri.getPort() >= 1024
                    && uri.getPort() <= 65535
                    && "/callback".equals(uri.getPath())
                    && uri.getRawQuery() == null
                    && uri.getRawFragment() == null
                    && uri.getUserInfo() == null;
            if (!valid) {
                throw badRequest("Redirect URI is invalid.");
            }
            return uri.toASCIIString();
        } catch (URISyntaxException exception) {
            throw badRequest("Redirect URI is invalid.");
        }
    }

    private String validateBase64Url(
            String value,
            String message,
            int minimumLength,
            int maximumLength) {
        String normalized = requireText(value, message);
        if (normalized.length() < minimumLength
                || normalized.length() > maximumLength
                || !normalized.matches("[A-Za-z0-9_-]+")) {
            throw badRequest(message);
        }
        return normalized;
    }

    private String validateText(String value, String requiredMessage, int maximumLength) {
        String normalized = requireText(value, requiredMessage);
        if (normalized.length() > maximumLength) {
            throw badRequest(requiredMessage.replace("is required", "is too long"));
        }
        return normalized;
    }

    private String normalizeOptionalText(String value, String tooLongMessage, int maximumLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maximumLength) {
            throw badRequest(tooLongMessage);
        }
        return normalized;
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw badRequest(message);
        }
        return value.trim();
    }

    private static String randomValue(int byteCount) {
        byte[] bytes = new byte[byteCount];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String pkceChallenge(String verifier) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest(verifier));
    }

    private static String hash(String value) {
        return HexFormat.of().formatHex(digest(value));
    }

    private static byte[] digest(String value) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException unauthorized(String message) {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, message);
    }

    private record ParsedDeviceCredential(UUID deviceId, String secret) {
    }
}
