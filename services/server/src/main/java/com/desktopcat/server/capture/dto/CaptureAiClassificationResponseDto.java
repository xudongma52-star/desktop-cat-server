package com.desktopcat.server.capture.dto;

import java.util.List;

public record CaptureAiClassificationResponseDto(List<CaptureAiDecisionDto> decisions) {
}
