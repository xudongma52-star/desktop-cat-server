package com.desktopcat.server.identity.controller;

import com.desktopcat.server.identity.application.DesktopAuthApplicationService;
import com.desktopcat.server.identity.dto.DesktopAuthorizationDto;
import com.desktopcat.server.identity.dto.DesktopAuthorizationRequestDto;
import com.desktopcat.server.identity.dto.DesktopCodeExchangeRequestDto;
import com.desktopcat.server.identity.dto.DesktopDeviceCredentialDto;
import com.desktopcat.server.identity.dto.DesktopTokenDto;
import java.security.Principal;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("postgres")
@RequestMapping("/api/auth/desktop")
public class DesktopAuthController {
    private final DesktopAuthApplicationService desktopAuthApplicationService;

    public DesktopAuthController(DesktopAuthApplicationService desktopAuthApplicationService) {
        this.desktopAuthApplicationService = desktopAuthApplicationService;
    }

    @PostMapping("/authorize")
    public DesktopAuthorizationDto authorize(
            @RequestBody DesktopAuthorizationRequestDto request,
            Principal principal) {
        return desktopAuthApplicationService.authorize(principal.getName(), request);
    }

    @PostMapping("/exchange")
    public DesktopTokenDto exchange(@RequestBody DesktopCodeExchangeRequestDto request) {
        return desktopAuthApplicationService.exchange(request);
    }

    @PostMapping("/refresh")
    public DesktopTokenDto refresh(@RequestBody DesktopDeviceCredentialDto request) {
        return desktopAuthApplicationService.refresh(
                request == null ? null : request.deviceCredential());
    }

    @PostMapping("/revoke")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@RequestBody DesktopDeviceCredentialDto request) {
        desktopAuthApplicationService.revoke(
                request == null ? null : request.deviceCredential());
    }
}
