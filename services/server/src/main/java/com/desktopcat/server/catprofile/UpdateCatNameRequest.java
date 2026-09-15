package com.desktopcat.server.catprofile;

public record UpdateCatNameRequest(Long profileId, String catName, Integer version) {
}
