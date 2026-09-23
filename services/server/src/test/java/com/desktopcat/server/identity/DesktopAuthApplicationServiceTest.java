package com.desktopcat.server.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.desktopcat.server.identity.application.DesktopAuthApplicationService;
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
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class DesktopAuthApplicationServiceTest {
    private static final String VERIFIER =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_";

    private final AppUserDao appUserDao = mock(AppUserDao.class);
    private final AuthDeviceDao authDeviceDao = mock(AuthDeviceDao.class);
    private final AtomicReference<AuthDeviceDO> storedDevice = new AtomicReference<>();
    private DesktopAuthApplicationService service;

    @BeforeEach
    void setUp() {
        AppUserDO user = new AppUserDO(
                17L,
                "MaxCat",
                "{noop}not-used",
                "ACTIVE",
                Instant.now(),
                Instant.now());
        when(appUserDao.selectByUsername("MaxCat")).thenReturn(user);
        when(appUserDao.selectById(17L)).thenReturn(user);
        when(authDeviceDao.insertDevice(any())).thenAnswer(invocation -> {
            storedDevice.set(invocation.getArgument(0));
            return 1;
        });
        when(authDeviceDao.selectById(any())).thenAnswer(invocation -> {
            AuthDeviceDO device = storedDevice.get();
            return device != null && device.deviceId().equals(invocation.getArgument(0))
                    ? device
                    : null;
        });
        when(authDeviceDao.updateLastUsedIfActive(any(), any())).thenReturn(1);
        when(authDeviceDao.revokeByIdAndUserId(any(), anyLong(), any())).thenReturn(1);

        DesktopAuthStateStore authStateStore = new InMemoryDesktopAuthStateStore();
        service = new DesktopAuthApplicationService(
                appUserDao,
                authDeviceDao,
                new DesktopAccessTokenService(authStateStore),
                authStateStore);
    }

    @Test
    void exchangesPkceCodeAndRefreshesWithStoredDeviceCredential() throws Exception {
        String redirectUri = "http://127.0.0.1:49152/callback";
        DesktopAuthorizationDto authorization = service.authorize(
                "MaxCat",
                new DesktopAuthorizationRequestDto(
                        redirectUri,
                        pkceChallenge(VERIFIER),
                        "abcdefghijklmnopqrstuv",
                        "MAX-PC",
                        "win32",
                        "0.3.0"));
        String code = queryParameter(authorization.callbackUrl(), "code");

        DesktopTokenDto firstToken = service.exchange(
                new DesktopCodeExchangeRequestDto(code, VERIFIER, redirectUri));

        assertThat(firstToken.accessToken()).startsWith("dcat_");
        assertThat(firstToken.deviceCredential()).startsWith(firstToken.deviceId() + ".");
        assertThat(firstToken.username()).isEqualTo("MaxCat");
        assertThat(storedDevice.get().credentialHash()).hasSize(64);

        DesktopTokenDto refreshed = service.refresh(firstToken.deviceCredential());
        assertThat(refreshed.accessToken()).startsWith("dcat_").isNotEqualTo(firstToken.accessToken());
        assertThat(refreshed.deviceCredential()).isNull();
        assertThat(refreshed.deviceId()).isEqualTo(firstToken.deviceId());
    }

    @Test
    void rejectsNonLoopbackRedirectAndWrongPkceVerifier() throws Exception {
        assertThatThrownBy(() -> service.authorize(
                "MaxCat",
                new DesktopAuthorizationRequestDto(
                        "https://example.com/callback",
                        pkceChallenge(VERIFIER),
                        "abcdefghijklmnopqrstuv",
                        "MAX-PC",
                        "win32",
                        "0.3.0")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Redirect URI is invalid");

        String redirectUri = "http://127.0.0.1:49153/callback";
        DesktopAuthorizationDto authorization = service.authorize(
                "MaxCat",
                new DesktopAuthorizationRequestDto(
                        redirectUri,
                        pkceChallenge(VERIFIER),
                        "abcdefghijklmnopqrstuv",
                        "MAX-PC",
                        "win32",
                        "0.3.0"));
        String code = queryParameter(authorization.callbackUrl(), "code");

        assertThatThrownBy(() -> service.exchange(new DesktopCodeExchangeRequestDto(
                code,
                "0123456789012345678901234567890123456789012",
                redirectUri)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("verification failed");
    }

    private String pkceChallenge(String verifier) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(verifier.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }

    private String queryParameter(String uri, String name) {
        String query = URI.create(uri).getRawQuery();
        for (String parameter : query.split("&")) {
            String[] parts = parameter.split("=", 2);
            if (parts[0].equals(name)) {
                return parts[1];
            }
        }
        throw new AssertionError("Missing query parameter: " + name);
    }

    private static final class InMemoryDesktopAuthStateStore implements DesktopAuthStateStore {
        private final ConcurrentHashMap<String, AuthorizationState> authorizations =
                new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, AccessIdentity> accessTokens =
                new ConcurrentHashMap<>();

        @Override
        public void saveAuthorization(
                String codeHash,
                AuthorizationState authorization,
                Duration lifetime) {
            authorizations.put(codeHash, authorization);
        }

        @Override
        public Optional<AuthorizationState> takeAuthorization(String codeHash) {
            return Optional.ofNullable(authorizations.remove(codeHash));
        }

        @Override
        public void saveAccessToken(
                String tokenHash,
                AccessIdentity identity,
                Duration lifetime) {
            accessTokens.put(tokenHash, identity);
        }

        @Override
        public Optional<AccessIdentity> findAccessToken(String tokenHash) {
            return Optional.ofNullable(accessTokens.get(tokenHash));
        }

        @Override
        public void deleteAccessToken(String tokenHash) {
            accessTokens.remove(tokenHash);
        }
    }
}
