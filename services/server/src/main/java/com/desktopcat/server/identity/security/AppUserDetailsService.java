package com.desktopcat.server.identity.security;

import com.desktopcat.server.identity.dao.AppUserDO;
import com.desktopcat.server.identity.dao.AppUserDao;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@Profile("postgres")
public class AppUserDetailsService implements UserDetailsService {
    private final AppUserDao appUserDao;

    public AppUserDetailsService(AppUserDao appUserDao) {
        this.appUserDao = appUserDao;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUserDO user = appUserDao.selectByUsername(username);
        if (user == null) {
            throw new UsernameNotFoundException("User was not found.");
        }

        boolean active = "ACTIVE".equals(user.status());
        return User.withUsername(user.username())
                .password(user.passwordHash())
                .disabled(!active)
                .authorities("ROLE_USER")
                .build();
    }
}
