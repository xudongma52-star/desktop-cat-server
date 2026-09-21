package com.desktopcat.server.emotion.service;

import com.desktopcat.server.emotion.dto.EmotionCreateDto;
import com.desktopcat.server.emotion.dto.EmotionDto;
import java.time.LocalDate;
import java.util.List;

public interface EmotionService {
    EmotionDto create(long userId, EmotionCreateDto request);

    List<EmotionDto> listByDate(long userId, LocalDate recordDate);
}
