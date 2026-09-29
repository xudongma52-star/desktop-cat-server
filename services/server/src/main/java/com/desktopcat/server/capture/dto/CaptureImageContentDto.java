package com.desktopcat.server.capture.dto;

import java.nio.file.Path;

public record CaptureImageContentDto(Path path, String contentType, long contentLength) {
}
