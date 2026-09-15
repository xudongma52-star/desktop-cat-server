package com.desktopcat.server.catprofile.controller;

import com.desktopcat.server.catprofile.dao.CatProfileDO;
import java.time.Instant;

public record CatProfileResponse(
        long profileId,
        String catName,
        int version,
        Instant updatedAt) {
    public static CatProfileResponse from(CatProfileDO profile) {
        return new CatProfileResponse(
                profile.profileId(), profile.catName(), profile.version(), profile.updatedAt());
    }
}
