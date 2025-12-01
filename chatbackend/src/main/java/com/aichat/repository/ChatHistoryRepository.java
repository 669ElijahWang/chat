package com.aichat.repository;

import com.aichat.domain.document.ChatHistory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "elasticsearch.enabled", havingValue = "true", matchIfMissing = false)
public interface ChatHistoryRepository extends ElasticsearchRepository<ChatHistory, String> {
    
    Page<ChatHistory> findByUserId(Long userId, Pageable pageable);
    
    Page<ChatHistory> findByUserIdAndContentContaining(Long userId, String content, Pageable pageable);
    
    Page<ChatHistory> findByConversationId(Long conversationId, Pageable pageable);
}

