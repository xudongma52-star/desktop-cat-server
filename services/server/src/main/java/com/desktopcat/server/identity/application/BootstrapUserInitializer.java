package com.desktopcat.server.identity.application;

import com.desktopcat.server.identity.dao.AppUserDO;
import com.desktopcat.server.identity.dao.AppUserDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("bootstrap-user")
public class BootstrapUserInitializer implements ApplicationRunner {
    private static final Logger log =
        LoggerFactory.getLogger(BootstrapUserInitializer.class);

    private static final int MIN_USERNAME_LENGTH = 3;
    private static final int MAX_USERNAME_LENGTH = 32;
    private static final int MIN_PASSWORD_LENGTH = 6;

    private final AppUserDao appUserDao;
    private final PasswordEncoder passwordEncoder;
    private final String configuredUsername;
    private final String configuredPassword;

    public BootstrapUserInitializer(
        AppUserDao appUserDao,
        PasswordEncoder passwordEncoder,
        @Value("${BOOTSTRAP_USER_USERNAME:}") String configuredUsername,
        @Value("${BOOTSTRAP_USER_PASSWORD:}") String configuredPassword) {
        this.appUserDao = appUserDao;
        this.passwordEncoder = passwordEncoder;
        this.configuredUsername = configuredUsername;
        this.configuredPassword = configuredPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String username = normalizeUsername(configuredUsername);
        validateUsername(username);
        validatePassword(configuredPassword);

        AppUserDO existingUser = appUserDao.selectByUsername(username);
        if (existingUser != null) {
            log.info(
                "event=bootstrap_user_skipped reason=user_already_exists userId={}",
                existingUser.userId());
            return;
        }

        String passwordHash = passwordEncoder.encode(configuredPassword);

        AppUserDO user = new AppUserDO(
            null,
            username,
            passwordHash,
            "ACTIVE",
            null,
            null);

        int insertedRows = appUserDao.insertUser(user);
        if (insertedRows != 1) {
            throw new IllegalStateException("Bootstrap user was not created.");
        }

        log.info("event=bootstrap_user_created");
    }

    private String normalizeUsername(String username) {
        if (username == null) {
            return "";
        }
        return username.trim();
    }

    private void validateUsername(String username) {
        if (username.isEmpty()) {
            throw new IllegalStateException(
                "BOOTSTRAP_USER_USERNAME is required.");
        }

        int length = username.codePointCount(0, username.length());

        if (length < MIN_USERNAME_LENGTH) {
            throw new IllegalStateException(
                "Bootstrap username must contain at least 3 characters.");
        }

        if (length > MAX_USERNAME_LENGTH) {
            throw new IllegalStateException(
                "Bootstrap username must not exceed 32 characters.");
        }
    }

    private void validatePassword(String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalStateException(
                "BOOTSTRAP_USER_PASSWORD is required.");
        }

        int length = password.codePointCount(0, password.length());
        if (length < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException(
                "Bootstrap password must contain at least 6 characters.");
        }
    }
}
