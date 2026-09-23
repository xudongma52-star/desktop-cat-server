package com.desktopcat.server.photo.dto;

import java.nio.file.Path;

public record PhotoContentDto(Path path, long contentLength) {
}
