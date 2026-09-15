package com.desktopcat.server.web;

import java.time.Instant;
import java.util.List;

public record ApiErrorResponse(
        String code,
        String message,
        String requestId,
        Instant timestamp,
        String path,
        List<ApiFieldError> details) {
}
