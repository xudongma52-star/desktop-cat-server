package com.desktopcat.server.catprofile.dto;

public record CatProfileUpdateDto(Long profileId, String catName, Integer version) {
}
