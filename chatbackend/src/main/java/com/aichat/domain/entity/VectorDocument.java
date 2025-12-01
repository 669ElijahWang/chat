package com.aichat.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "vector_documents", indexes = {
    @Index(name = "idx_vector_documents_knowledge_id", columnList = "knowledge_base_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VectorDocument {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private Long knowledgeBaseId;
    
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;
    
    @Column(columnDefinition = "vector(1536)")
    private String embedding;  // 存储为字符串，实际是向量
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;
    
    @Column
    private Integer tokenCount;
    
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    // Helper methods for embedding conversion
    public void setEmbeddingFromList(List<Double> embeddingList) {
        if (embeddingList != null && !embeddingList.isEmpty()) {
            this.embedding = "[" + String.join(",", 
                embeddingList.stream()
                    .map(String::valueOf)
                    .toArray(String[]::new)) + "]";
        }
    }
}

