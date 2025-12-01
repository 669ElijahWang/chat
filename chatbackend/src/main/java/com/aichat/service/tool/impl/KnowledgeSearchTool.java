package com.aichat.service.tool.impl;

import com.aichat.domain.entity.VectorDocument;
import com.aichat.service.VectorService;
import com.aichat.service.tool.ToolExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component("knowledge_search")
@RequiredArgsConstructor
@Slf4j
public class KnowledgeSearchTool implements ToolExecutor {
    
    private final VectorService vectorService;
    
    @Override
    public Object execute(Map<String, Object> params) {
        String query = (String) params.get("query");
        Long knowledgeBaseId = ((Number) params.get("knowledge_base_id")).longValue();
        Long userId = params.containsKey("user_id") ? ((Number) params.get("user_id")).longValue() : null;
        Integer topK = params.containsKey("top_k") ? ((Number) params.get("top_k")).intValue() : 5;
        
        if (query == null || query.trim().isEmpty()) {
            throw new IllegalArgumentException("搜索查询不能为空");
        }
        
        if (knowledgeBaseId == null || userId == null) {
            throw new IllegalArgumentException("知识库ID和用户ID不能为空");
        }
        
        try {
            List<VectorDocument> results = vectorService.searchSimilarDocuments(
                knowledgeBaseId, userId, query, topK
            );
            
            List<Map<String, Object>> documents = results.stream()
                .map(doc -> Map.of(
                    "id", doc.getId(),
                    "content", doc.getContent(),
                    "metadata", doc.getMetadata() != null ? doc.getMetadata() : Map.of()
                ))
                .collect(Collectors.toList());
            
            log.info("知识库搜索完成: knowledgeBaseId={}, results={}", knowledgeBaseId, documents.size());
            
            return Map.of(
                "query", query,
                "knowledge_base_id", knowledgeBaseId,
                "documents", documents,
                "success", true
            );
        } catch (Exception e) {
            log.error("知识库搜索失败", e);
            return Map.of(
                "query", query,
                "error", e.getMessage(),
                "success", false
            );
        }
    }
    
    @Override
    public String getToolName() {
        return "knowledge_search";
    }
}

