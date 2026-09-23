package com.desktopcat.server.identity.application;

import com.desktopcat.server.identity.dao.AppUserDO;
import com.desktopcat.server.identity.dao.AppUserDao;
import com.desktopcat.server.identity.dto.AuthStatusDto;
import com.desktopcat.server.identity.dto.AuthUserDto;
import com.desktopcat.server.identity.dto.RegisterRequestDto;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Profile("postgres")
public class AuthApplicationService {
    private static final Logger log = LoggerFactory.getLogger(AuthApplicationService.class);
    private static final int MIN_USERNAME_LENGTH = 3;
    private static final int MAX_USERNAME_LENGTH = 32;
    private static final int MIN_PASSWORD_LENGTH = 6;
    private static final int MAX_BCRYPT_PASSWORD_BYTES = 72;

    private final AppUserDao appUserDao;
    private final PasswordEncoder passwordEncoder;

    public AuthApplicationService(AppUserDao appUserDao, PasswordEncoder passwordEncoder) {
        this.appUserDao = appUserDao;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public AuthUserDto register(RegisterRequestDto request) {
        if (request == null) {
            throw badRequest("Request body is required.");
        }

        String username = normalizeAndValidateUsername(request.username());
        String password = validatePassword(request.password());

        if (appUserDao.selectByUsername(username) != null) {
            throw conflict("Username already exists.");
        }

        AppUserDO user = new AppUserDO(
                null,
                username,
                passwordEncoder.encode(password),
                "ACTIVE",
                null,
                null);

        try {
            int insertedRows = appUserDao.insertUser(user);
            if (insertedRows != 1) {
                throw new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR, "User could not be created.");
            }
        } catch (DuplicateKeyException exception) {
            // 查询和插入之间仍可能出现并发注册，唯一约束负责最后一道保护。
            throw conflict("Username already exists.");
        }

        AppUserDO created = appUserDao.selectByUsername(username);
        if (created == null) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "User could not be created.");
        }

        log.info("event=user_registered userId={}", created.userId());
        return toUserDto(created);
    }

    public AuthStatusDto getStatus(String username) {
        if (username == null || username.isBlank()) {
            return AuthStatusDto.anonymous();
        }

        AppUserDO user = appUserDao.selectByUsername(username);
        if (user == null) {
            return AuthStatusDto.anonymous();
        }
        return new AuthStatusDto(true, user.userId(), user.username());
    }

    private String normalizeAndValidateUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw badRequest("Username is required.");
        }

        String normalizedUsername = username.trim();
        int length = normalizedUsername.codePointCount(0, normalizedUsername.length());
        if (length < MIN_USERNAME_LENGTH) {
            throw badRequest("Username must contain at least 3 characters.");
        }
        if (length > MAX_USERNAME_LENGTH) {
            throw badRequest("Username must not exceed 32 characters.");
        }
        return normalizedUsername;
    }

    private String validatePassword(String password) {
        if (password == null || password.isBlank()) {
            throw badRequest("Password is required.");
        }

        int length = password.codePointCount(0, password.length());
        if (length < MIN_PASSWORD_LENGTH) {
            throw badRequest("Password must contain at least 6 characters.");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BCRYPT_PASSWORD_BYTES) {
            throw badRequest("Password must not exceed 72 UTF-8 bytes.");
        }
        return password;
    }

    private AuthUserDto toUserDto(AppUserDO user) {
        return new AuthUserDto(user.userId(), user.username());
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
