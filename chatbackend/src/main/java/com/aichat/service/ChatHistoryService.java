package com.aichat.service;

import com.aichat.domain.document.ChatHistory;
import com.aichat.domain.entity.Message;
import com.aichat.repository.ChatHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
@Slf4j
public class ChatHistoryService {
    
    @Autowired(required = false)
    private ChatHistoryRepository chatHistoryRepository;
    
    @Async
    public void indexMessage(Message message) {
        if (chatHistoryRepository == null) {
            log.debug("Elasticsearch未启用，跳过索引");
            return;
        }
        
        try {
            ChatHistory chatHistory = ChatHistory.builder()
                    .id(message.getId().toString())
                    .messageId(message.getId())
                    .conversationId(message.getConversationId())
                    .userId(message.getUserId())
                    .role(message.getRole().name())
                    .content(message.getContent())
                    .createdAt(message.getCreatedAt())
                    .build();
            
            chatHistoryRepository.save(chatHistory);
            log.debug("消息已索引到ES: messageId={}", message.getId());
        } catch (Exception e) {
            log.error("索引消息到ES失败: messageId={}", message.getId(), e);
        }
    }
    
    public Page<ChatHistory> searchHistory(Long userId, String query, Pageable pageable) {
        if (chatHistoryRepository == null) {
            log.warn("Elasticsearch未启用，无法搜索历史");
            return new PageImpl<>(Collections.emptyList(), pageable, 0);
        }
        
        if (query == null || query.trim().isEmpty()) {
            return chatHistoryRepository.findByUserId(userId, pageable);
        }
        return chatHistoryRepository.findByUserIdAndContentContaining(userId, query, pageable);
    }
    
    public Page<ChatHistory> getConversationHistory(Long conversationId, Pageable pageable) {
        if (chatHistoryRepository == null) {
            log.warn("Elasticsearch未启用，无法获取对话历史");
            return new PageImpl<>(Collections.emptyList(), pageable, 0);
        }
        
        return chatHistoryRepository.findByConversationId(conversationId, pageable);
    }
}

