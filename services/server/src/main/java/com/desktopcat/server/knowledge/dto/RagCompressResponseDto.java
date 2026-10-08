package com.desktopcat.server.knowledge.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record RagCompressResponseDto(JsonNode summary) {
}
