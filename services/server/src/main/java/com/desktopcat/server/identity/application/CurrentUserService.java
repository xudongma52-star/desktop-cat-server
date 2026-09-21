package com.desktopcat.server.identity.application;

import com.desktopcat.server.identity.dao.AppUserDO;
import com.desktopcat.server.identity.dao.AppUserDao;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Resolves the authenticated account to the stable database tenant key. */
@Service
@Profile("postgres")
public class CurrentUserService {
    private final AppUserDao appUserDao;

    public CurrentUserService(AppUserDao appUserDao) {
        this.appUserDao = appUserDao;
    }

    public long requireUserId(String username) {
        if (username == null || username.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required.");
        }

        AppUserDO user = appUserDao.selectByUsername(username);
        if (user == null || !"ACTIVE".equals(user.status())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User account is unavailable.");
        }
        return user.userId();
    }
}
