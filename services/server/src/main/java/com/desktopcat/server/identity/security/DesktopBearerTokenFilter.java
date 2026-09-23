package com.desktopcat.server.identity.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Profile("postgres")
public class DesktopBearerTokenFilter extends OncePerRequestFilter {
    private static final String BEARER_PREFIX = "Bearer ";

    private final DesktopAccessTokenService accessTokenService;

    public DesktopBearerTokenFilter(DesktopAccessTokenService accessTokenService) {
        this.accessTokenService = accessTokenService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            String authorization = request.getHeader("Authorization");
            if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
                accessTokenService.authenticate(authorization.substring(BEARER_PREFIX.length()))
                        .ifPresent(identity -> {
                            UsernamePasswordAuthenticationToken authentication =
                                    UsernamePasswordAuthenticationToken.authenticated(
                                            identity.username(),
                                            null,
                                            List.of(new SimpleGrantedAuthority("ROLE_USER")));
                            authentication.setDetails(
                                    new WebAuthenticationDetailsSource().buildDetails(request));
                            SecurityContextHolder.getContext().setAuthentication(authentication);
                        });
            }
        }
        filterChain.doFilter(request, response);
    }
}
