package com.aichat.controller;

import com.aichat.domain.dto.chat.ChatRequest;
import com.aichat.domain.dto.chat.ChatResponse;
import com.aichat.domain.dto.chat.ConversationDTO;
import com.aichat.domain.dto.common.ApiResponse;
import com.aichat.security.UserPrincipal;
import com.aichat.service.ChatService;
import com.aichat.service.ConversationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {
    
    private final ChatService chatService;
    private final ConversationService conversationService;
    
    /**
     * 创建新会话
     */
    @PostMapping("/conversations")
    public ApiResponse<ConversationDTO> createConversation(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestParam(required = false) String title) {
        ConversationDTO conversation = conversationService.createConversation(userPrincipal.getId(), title);
        return ApiResponse.success("创建成功", conversation);
    }
    
    /**
     * 获取用户的会话列表
     */
    @GetMapping("/conversations")
    public ApiResponse<Page<ConversationDTO>> getConversations(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PageableDefault(size = 20, sort = "lastMessageAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<ConversationDTO> conversations = conversationService.getUserConversations(userPrincipal.getId(), pageable);
        return ApiResponse.success(conversations);
    }
    
    /**
     * 获取会话详情
     */
    @GetMapping("/conversations/{conversationId}")
    public ApiResponse<ConversationDTO> getConversation(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long conversationId) {
        ConversationDTO conversation = conversationService.getConversation(conversationId, userPrincipal.getId());
        return ApiResponse.success(conversation);
    }
    
    /**
     * 更新会话标题
     */
    @PutMapping("/conversations/{conversationId}/title")
    public ApiResponse<ConversationDTO> updateConversationTitle(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long conversationId,
            @RequestParam String title) {
        ConversationDTO conversation = conversationService.updateConversationTitle(
                conversationId, userPrincipal.getId(), title);
        return ApiResponse.success("更新成功", conversation);
    }
    
    /**
     * 删除会话
     */
    @DeleteMapping("/conversations/{conversationId}")
    public ApiResponse<Void> deleteConversation(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long conversationId) {
        conversationService.deleteConversation(conversationId, userPrincipal.getId());
        return ApiResponse.success("删除成功", null);
    }
    
    /**
     * 发送聊天消息
     */
    @PostMapping(value = "/messages", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ApiResponse<ChatResponse>> sendMessage(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody ChatRequest request) {
        return chatService.chat(userPrincipal.getId(), request)
                .map(response -> ApiResponse.success("发送成功", response));
    }
    
    /**
     * 获取会话的消息列表
     */
    @GetMapping("/conversations/{conversationId}/messages")
    public ApiResponse<List<ChatResponse>> getMessages(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long conversationId) {
        List<ChatResponse> messages = chatService.getConversationMessages(conversationId, userPrincipal.getId());
        return ApiResponse.success(messages);
    }
}

