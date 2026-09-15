package com.desktopcat.server.catprofile;

import java.time.Instant;

public record CatProfileResponse(
        long profileId,
        String catName,
        int version,
        Instant updatedAt) {
    public static CatProfileResponse from(CatProfile profile) {
        return new CatProfileResponse(
                profile.profileId(), profile.catName(), profile.version(), profile.updatedAt());
    }
}
