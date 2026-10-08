package com.desktopcat.server.knowledge.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public record RagCompressRequestDto(
        JsonNode previousSummary, List<RagConversationMessageDto> turns) {
}
