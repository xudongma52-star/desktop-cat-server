package com.desktopcat.server.record.controller;

import com.desktopcat.server.capture.dto.CaptureItemDto;
import com.desktopcat.server.capture.service.CaptureOrganizationService;
import com.desktopcat.server.identity.application.CurrentUserService;
import java.security.Principal;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("postgres")
@RequestMapping("/api/records")
public class PersonalRecordSourceController {
    private final CaptureOrganizationService captureOrganizationService;
    private final CurrentUserService currentUserService;

    public PersonalRecordSourceController(
            CaptureOrganizationService captureOrganizationService,
            CurrentUserService currentUserService) {
        this.captureOrganizationService = captureOrganizationService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/{recordId}/sources")
    public List<CaptureItemDto> listSources(
            @PathVariable Long recordId, Principal principal) {
        long userId = currentUserService.requireUserId(
                principal == null ? null : principal.getName());
        return captureOrganizationService.listRecordSources(userId, recordId);
    }
}
