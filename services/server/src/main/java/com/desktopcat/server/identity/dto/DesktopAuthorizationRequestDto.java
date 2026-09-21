package com.desktopcat.server.identity.dto;

public record DesktopAuthorizationRequestDto(
        String redirectUri,
        String codeChallenge,
        String state,
        String deviceName,
        String platform,
        String appVersion) {
}
