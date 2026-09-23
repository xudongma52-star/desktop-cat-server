package com.desktopcat.server.identity.dto;

public record DesktopCodeExchangeRequestDto(
        String code,
        String codeVerifier,
        String redirectUri) {
}
