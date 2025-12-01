package com.aichat.domain.dto.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class ChatRequest {
    
    @NotNull(message = "会话ID不能为空")
    private Long conversationId;
    
    @NotBlank(message = "消息内容不能为空")
    private String content;
    
    private Boolean stream = false;
    
    private String model;
    
    private Double temperature;

    /**
     * 可选：限制/提升模型最大输出 token，默认后端使用 3500
     */
    private Integer maxTokens;
    
    /**
     * 知识库ID列表，用于RAG检索
     */
    private List<Long> knowledgeBaseIds;
    
    /**
     * RAG检索时返回的文档数量，默认为3
     */
    private Integer ragTopK = 3;
}

