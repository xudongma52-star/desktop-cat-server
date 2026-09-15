package com.desktopcat.server.catprofile;

import java.time.Instant;

public record CatProfile(
        long profileId,
        String profileKey,
        String catName,
        int version,
        Instant createdAt,
        Instant updatedAt) {
}
