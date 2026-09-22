package com.desktopcat.server.knowledge.dto;

import java.util.List;

/** Python 检索服务的内部响应。 */
public record RagRetrieveResponseDto(
        List<RagMatchDto> matches,
        String answer,
        boolean answerGenerated) {
}
