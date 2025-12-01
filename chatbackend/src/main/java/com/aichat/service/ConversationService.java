package com.aichat.service;

import com.aichat.domain.dto.chat.ConversationDTO;
import com.aichat.domain.entity.Conversation;
import com.aichat.exception.BusinessException;
import com.aichat.repository.ConversationRepository;
import com.aichat.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationService {
    
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    
    @Transactional
    public ConversationDTO createConversation(Long userId, String title) {
        Conversation conversation = Conversation.builder()
                .userId(userId)
                .title(title != null && !title.isEmpty() ? title : "新对话")
                .messageCount(0)
                .build();
        
        conversation = conversationRepository.save(conversation);
        log.info("创建新会话: userId={}, conversationId={}", userId, conversation.getId());
        
        return toDTO(conversation);
    }
    
    @Transactional(readOnly = true)
    public Page<ConversationDTO> getUserConversations(Long userId, Pageable pageable) {
        return conversationRepository.findByUserIdOrderByLastMessageAtDesc(userId, pageable)
                .map(this::toDTO);
    }
    
    @Transactional(readOnly = true)
    public ConversationDTO getConversation(Long conversationId, Long userId) {
        Conversation conversation = conversationRepository.findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new BusinessException("会话不存在或无权访问"));
        
        return toDTO(conversation);
    }
    
    @Transactional
    public ConversationDTO updateConversationTitle(Long conversationId, Long userId, String title) {
        Conversation conversation = conversationRepository.findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new BusinessException("会话不存在或无权访问"));
        
        conversation.setTitle(title);
        conversation = conversationRepository.save(conversation);
        
        log.info("更新会话标题: conversationId={}, title={}", conversationId, title);
        
        return toDTO(conversation);
    }
    
    @Transactional
    public void updateLastMessageTime(Long conversationId) {
        conversationRepository.findById(conversationId).ifPresent(conversation -> {
            conversation.setLastMessageAt(LocalDateTime.now());
            conversation.setMessageCount(conversation.getMessageCount() + 1);
            conversationRepository.save(conversation);
        });
    }
    
    @Transactional
    public void deleteConversation(Long conversationId, Long userId) {
        Conversation conversation = conversationRepository.findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new BusinessException("会话不存在或无权访问"));
        
        // 删除会话及其所有消息（级联删除）
        conversationRepository.delete(conversation);
        
        log.info("删除会话: conversationId={}, userId={}", conversationId, userId);
    }
    
    private ConversationDTO toDTO(Conversation conversation) {
        return ConversationDTO.builder()
                .id(conversation.getId())
                .title(conversation.getTitle())
                .summary(conversation.getSummary())
                .messageCount(conversation.getMessageCount())
                .createdAt(conversation.getCreatedAt())
                .lastMessageAt(conversation.getLastMessageAt())
                .build();
    }
}

