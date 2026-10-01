package com.desktopcat.server.knowledge.controller;

import com.desktopcat.server.identity.application.CurrentUserService;
import com.desktopcat.server.knowledge.dto.KnowledgeChatRequestDto;
import com.desktopcat.server.knowledge.dto.KnowledgeChatResponseDto;
import com.desktopcat.server.knowledge.dto.KnowledgeChatSummaryDto;
import com.desktopcat.server.knowledge.dto.KnowledgeChatTitleRequestDto;
import com.desktopcat.server.knowledge.dto.KnowledgeMessagePageDto;
import com.desktopcat.server.knowledge.service.KnowledgeChatService;
import java.security.Principal;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;

/** 当前登录用户的知识库多轮对话接口。 */
@RestController
@Profile("postgres")
@RequestMapping("/api/knowledge")
public class KnowledgeChatController {
    private final KnowledgeChatService chatService;
    private final CurrentUserService currentUserService;

    public KnowledgeChatController(
            KnowledgeChatService chatService,
            CurrentUserService currentUserService) {
        this.chatService = chatService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/chats")
    public List<KnowledgeChatSummaryDto> listChats(Principal principal) {
        return chatService.listChats(userId(principal));
    }

    @GetMapping("/chats/{chatId}/messages")
    public KnowledgeMessagePageDto listMessages(
            @PathVariable long chatId,
            @RequestParam(required = false) Long beforeMessageId,
            Principal principal) {
        return chatService.listMessages(userId(principal), chatId, beforeMessageId);
    }

    @PostMapping("/chat")
    public KnowledgeChatResponseDto chat(
            @RequestBody(required = false) KnowledgeChatRequestDto request,
            Principal principal) {
        return chatService.chat(userId(principal), request);
    }

    @PatchMapping("/chats/{chatId}/title")
    public KnowledgeChatSummaryDto renameChat(
            @PathVariable long chatId,
            @RequestBody(required = false) KnowledgeChatTitleRequestDto request,
            Principal principal) {
        return chatService.renameChat(userId(principal), chatId, request);
    }

    @DeleteMapping("/chats/{chatId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteChat(@PathVariable long chatId, Principal principal) {
        chatService.deleteChat(userId(principal), chatId);
    }

    private long userId(Principal principal) {
        return currentUserService.requireUserId(
                principal == null ? null : principal.getName());
    }
}
