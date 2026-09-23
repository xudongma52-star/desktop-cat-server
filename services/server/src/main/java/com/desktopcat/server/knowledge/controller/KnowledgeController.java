package com.desktopcat.server.knowledge.controller;

import com.desktopcat.server.identity.application.CurrentUserService;
import com.desktopcat.server.knowledge.dto.KnowledgeRetrieveRequestDto;
import com.desktopcat.server.knowledge.dto.KnowledgeSearchResultDto;
import com.desktopcat.server.knowledge.service.KnowledgeRetrievalService;
import java.security.Principal;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 当前登录用户的个人知识检索接口。 */
@RestController
@Profile("postgres")
@RequestMapping("/api/knowledge")
public class KnowledgeController {
    private final KnowledgeRetrievalService knowledgeRetrievalService;
    private final CurrentUserService currentUserService;

    public KnowledgeController(
            KnowledgeRetrievalService knowledgeRetrievalService,
            CurrentUserService currentUserService) {
        this.knowledgeRetrievalService = knowledgeRetrievalService;
        this.currentUserService = currentUserService;
    }

    @PostMapping("/retrieve")
    public KnowledgeSearchResultDto retrieve(
            @RequestBody(required = false) KnowledgeRetrieveRequestDto request,
            Principal principal) {
        long userId = currentUserService.requireUserId(
                principal == null ? null : principal.getName());
        return knowledgeRetrievalService.retrieve(userId, request);
    }
}
