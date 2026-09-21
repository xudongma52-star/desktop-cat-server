package com.desktopcat.server.identity.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.web.authentication.RememberMeServices;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices;

@Configuration(proxyBeanMethods = false)
@Profile("postgres")
public class RememberMeConfig {
    private static final int REMEMBER_ME_SECONDS = 30 * 24 * 60 * 60;

    @Bean
    public RememberMeServices rememberMeServices(
            AppUserDetailsService userDetailsService,
            @Value("${desktop-cat.auth.remember-me-key}") String rememberMeKey,
            @Value("${desktop-cat.auth.secure-cookies:false}") boolean secureCookies) {
        TokenBasedRememberMeServices services =
                new TokenBasedRememberMeServices(rememberMeKey, userDetailsService);
        services.setAlwaysRemember(true);
        services.setTokenValiditySeconds(REMEMBER_ME_SECONDS);
        services.setCookieName("DESKTOP_CAT_REMEMBER_ME");
        services.setUseSecureCookie(secureCookies);
        return services;
    }
}
