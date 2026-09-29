package com.desktopcat.server.capture.dto;

import java.util.List;

public record CaptureItemPageDto(
        List<CaptureItemDto> items,
        int page,
        int pageSize,
        long total,
        long totalPages) {
}
