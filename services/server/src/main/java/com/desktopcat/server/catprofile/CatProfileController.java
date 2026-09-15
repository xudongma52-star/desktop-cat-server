package com.desktopcat.server.catprofile;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cat/profile")
public class CatProfileController {
    private final CatProfileService service;

    public CatProfileController(CatProfileService service) {
        this.service = service;
    }

    @GetMapping
    public CatProfileResponse getPrimaryProfile() {
        return service.getPrimaryProfile();
    }

    @PatchMapping("/name")
    public CatProfileResponse updatePrimaryName(@RequestBody UpdateCatNameRequest request) {
        return service.updatePrimaryName(request);
    }
}
