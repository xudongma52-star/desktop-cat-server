package com.desktopcat.server.catprofile.controller;

public record UpdateCatNameRequest(Long profileId, String catName, Integer version) {
}
