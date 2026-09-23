package com.desktopcat.server.catprofile.dao;

import java.time.Instant;

public record CatProfileDO(
        long profileId,
        String profileKey,
        String catName,
        int version,
        Instant createdAt,
        Instant updatedAt) {
}
