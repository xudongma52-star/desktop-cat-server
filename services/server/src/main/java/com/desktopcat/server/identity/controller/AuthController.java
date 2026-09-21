package com.desktopcat.server.identity.controller;

import com.desktopcat.server.identity.application.AuthApplicationService;
import com.desktopcat.server.identity.dto.AuthStatusDto;
import com.desktopcat.server.identity.dto.AuthUserDto;
import com.desktopcat.server.identity.dto.LoginRequestDto;
import com.desktopcat.server.identity.dto.RegisterRequestDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.security.Principal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.authentication.RememberMeServices;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Profile("postgres")
@RequestMapping("/api/auth")
public class AuthController {
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthApplicationService authApplicationService;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final RememberMeServices rememberMeServices;

    public AuthController(
            AuthApplicationService authApplicationService,
            AuthenticationManager authenticationManager,
            SecurityContextRepository securityContextRepository,
            RememberMeServices rememberMeServices) {
        this.authApplicationService = authApplicationService;
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.rememberMeServices = rememberMeServices;
    }

    @GetMapping("/status")
    public AuthStatusDto getStatus(Principal principal) {
        String username = principal == null ? null : principal.getName();
        return authApplicationService.getStatus(username);
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthUserDto register(
            @RequestBody RegisterRequestDto request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        AuthUserDto created = authApplicationService.register(request);
        return authenticate(
                new LoginRequestDto(created.username(), request.password()),
                servletRequest,
                servletResponse);
    }

    @PostMapping("/login")
    public AuthUserDto login(
            @RequestBody LoginRequestDto request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        return authenticate(request, servletRequest, servletResponse);
    }

    private AuthUserDto authenticate(
            LoginRequestDto request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        String username = request == null || request.username() == null
                ? ""
                : request.username().trim();
        String password = request == null || request.password() == null
                ? ""
                : request.password();

        try {
            Authentication authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(username, password));
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            securityContextRepository.saveContext(context, servletRequest, servletResponse);
            rememberMeServices.loginSuccess(servletRequest, servletResponse, authentication);

            AuthStatusDto status = authApplicationService.getStatus(authentication.getName());
            if (!status.authenticated()) {
                throw new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Invalid username or password.");
            }

            log.info("event=user_logged_in userId={}", status.userId());
            return new AuthUserDto(status.userId(), status.username());
        } catch (AuthenticationException exception) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Invalid username or password.");
        }
    }
}
