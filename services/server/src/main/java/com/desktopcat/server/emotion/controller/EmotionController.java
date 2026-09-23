package com.desktopcat.server.emotion.controller;

import com.desktopcat.server.emotion.dto.EmotionCreateDto;
import com.desktopcat.server.emotion.dto.EmotionDto;
import com.desktopcat.server.emotion.service.EmotionService;
import com.desktopcat.server.identity.application.CurrentUserService;
import java.security.Principal;
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
    private final CurrentUserService currentUserService;

    public EmotionController(
            EmotionService emotionService,
            CurrentUserService currentUserService) {
        this.emotionService = emotionService;
        this.currentUserService = currentUserService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EmotionDto create(
            @RequestBody(required = false) EmotionCreateDto request,
            Principal principal) {
        return emotionService.create(userId(principal), request);
    }

    @GetMapping
    public List<EmotionDto> listByDate(
            @RequestParam(required = false) LocalDate date,
            Principal principal) {
        return emotionService.listByDate(userId(principal), date);
    }

    private long userId(Principal principal) {
        return currentUserService.requireUserId(principal == null ? null : principal.getName());
    }
}
