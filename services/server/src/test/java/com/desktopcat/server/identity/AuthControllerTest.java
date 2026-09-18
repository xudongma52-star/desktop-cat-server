package com.desktopcat.server.identity;

import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.desktopcat.server.identity.application.AuthApplicationService;
import com.desktopcat.server.identity.controller.AuthController;
import com.desktopcat.server.identity.dao.AppUserDO;
import com.desktopcat.server.identity.dao.AppUserDao;
import com.desktopcat.server.identity.security.AppUserDetailsService;
import com.desktopcat.server.web.GlobalExceptionHandler;
import com.desktopcat.server.web.RequestIdFilter;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuthControllerTest {
    private final AppUserDao appUserDao = mock(AppUserDao.class);
    private final AtomicReference<AppUserDO> storedUser = new AtomicReference<>();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        storedUser.set(null);
        when(appUserDao.selectByUsername(any())).thenAnswer(invocation -> {
            String username = invocation.getArgument(0);
            AppUserDO user = storedUser.get();
            return user != null && user.username().equals(username) ? user : null;
        });
        when(appUserDao.insertUser(any())).thenAnswer(invocation -> {
            AppUserDO requested = invocation.getArgument(0);
            storedUser.set(new AppUserDO(
                    1L,
                    requested.username(),
                    requested.passwordHash(),
                    requested.status(),
                    Instant.now(),
                    Instant.now()));
            return 1;
        });

        PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        AuthApplicationService service = new AuthApplicationService(
                appUserDao, passwordEncoder);
        DaoAuthenticationProvider authenticationProvider = new DaoAuthenticationProvider(
                new AppUserDetailsService(appUserDao));
        authenticationProvider.setPasswordEncoder(passwordEncoder);
        mvc = MockMvcBuilders.standaloneSetup(new AuthController(
                        service,
                        new ProviderManager(authenticationProvider),
                        new HttpSessionSecurityContextRepository()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestIdFilter())
                .build();
    }

    @Test
    void registersUserAndKeepsUsernameCase() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"  MaxCat  ","password":"cat123"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Request-Id", matchesPattern("[a-z0-9]{32}")))
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.username").value("MaxCat"))
                .andExpect(request().sessionAttribute(
                        HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                        org.hamcrest.Matchers.notNullValue()));

        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"MaxCat","password":"another-pass"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USERNAME_ALREADY_EXISTS"));
    }

    @Test
    void logsInWithValidPasswordAndHidesAuthenticationFailures() throws Exception {
        PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        storedUser.set(new AppUserDO(
                7L,
                "MaxCat",
                passwordEncoder.encode("cat-pass-123"),
                "ACTIVE",
                Instant.now(),
                Instant.now()));

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"  MaxCat  ","password":"cat-pass-123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(7))
                .andExpect(jsonPath("$.username").value("MaxCat"))
                .andExpect(request().sessionAttribute(
                        HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                        org.hamcrest.Matchers.notNullValue()));

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"MaxCat","password":"wrong-password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"MissingUser","password":"cat-pass-123"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void rejectsDisabledUserLogin() throws Exception {
        PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        storedUser.set(new AppUserDO(
                8L,
                "DisabledCat",
                passwordEncoder.encode("cat-pass-123"),
                "DISABLED",
                Instant.now(),
                Instant.now()));

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"DisabledCat","password":"cat-pass-123"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void rejectsInvalidRegistrationData() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"ab","password":"cat-pass-123"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("USERNAME_TOO_SHORT"));

        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"MaxCat","password":"short"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_TOO_SHORT"));
    }

    @Test
    void reportsAnonymousStatusBeforeSecurityIsConnected() throws Exception {
        mvc.perform(get("/api/auth/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false))
                .andExpect(jsonPath("$.userId").doesNotExist())
                .andExpect(jsonPath("$.username").doesNotExist());
    }
}
