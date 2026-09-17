package com.desktopcat.server.catprofile.controller;

import com.desktopcat.server.catprofile.dto.CatProfileDto;
import com.desktopcat.server.catprofile.dto.CatProfileUpdateDto;
import com.desktopcat.server.catprofile.service.CatProfileService;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("postgres")
@RequestMapping("/api/cat/profile")
public class CatProfileController {
    private final CatProfileService service;

    public CatProfileController(CatProfileService service) {
        this.service = service;
    }

    @GetMapping
    public CatProfileDto getPrimaryProfile() {
        return service.getPrimaryProfile();
    }

    @PatchMapping("/name")
    public CatProfileDto updatePrimaryName(@RequestBody CatProfileUpdateDto request) {
        return service.updatePrimaryName(request);
    }
}
