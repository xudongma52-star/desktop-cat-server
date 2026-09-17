package com.desktopcat.server.catprofile.dto;

import java.time.Instant;

public record CatProfileDto(
        long profileId,
        String catName,
        int version,
        Instant updatedAt) {
}
