package com.desktopcat.server.catprofile.controller;

import com.desktopcat.server.catprofile.service.CatProfileService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.context.annotation.Profile;

@RestController
@Profile("postgres")
@RequestMapping("/api/cat/profile")
public class CatProfileController {
    private final CatProfileService service;

    public CatProfileController(CatProfileService service) {
        this.service = service;
    }

    @GetMapping
    public CatProfileResponse getPrimaryProfile() {
        return CatProfileResponse.from(service.getPrimaryProfile());
    }

    @PatchMapping("/name")
    public CatProfileResponse updatePrimaryName(@RequestBody UpdateCatNameRequest request) {
        return CatProfileResponse.from(service.updatePrimaryName(
                request.profileId(), request.catName(), request.version()));
    }
}
