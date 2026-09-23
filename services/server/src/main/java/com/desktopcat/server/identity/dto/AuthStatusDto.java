package com.desktopcat.server.identity.dto;

public record AuthStatusDto(
        boolean authenticated,
        Long userId,
        String username) {

    public static AuthStatusDto anonymous() {
        return new AuthStatusDto(false, null, null);
    }
}
