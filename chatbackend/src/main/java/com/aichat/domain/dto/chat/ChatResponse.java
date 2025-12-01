package com.aichat.domain.dto.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {
    
    private Long messageId;
    
    private Long conversationId;
    
    private String role;
    
    private String content;
    
    private Integer tokens;
    
    private LocalDateTime createdAt;
    
    /**
     * RAG检索使用的文档信息
     */
    private List<RagDocumentInfo> ragDocs;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RagDocumentInfo {
        private Long documentId;
        private String content;
        private String knowledgeBaseTitle;
    }
}

