package com.desktopcat.server.emotion.controller;

import com.desktopcat.server.emotion.dto.EmotionCreateDto;
import com.desktopcat.server.emotion.dto.EmotionDto;
import com.desktopcat.server.emotion.service.EmotionService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("postgres")
@RequestMapping("/api/emotions")
public class EmotionController {
    private final EmotionService emotionService;

    public EmotionController(EmotionService emotionService) {
        this.emotionService = emotionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EmotionDto create(@RequestBody(required = false) EmotionCreateDto request) {
        return emotionService.create(request);
    }

    @GetMapping
    public List<EmotionDto> listByDate(@RequestParam(required = false) LocalDate date) {
        return emotionService.listByDate(date);
    }
}
