package com.desktopcat.server.identity.dto;

public record LoginRequestDto(
        String username,
        String password) {
}
