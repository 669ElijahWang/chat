package com.aichat.repository;

import com.aichat.domain.entity.VectorDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VectorDocumentRepository extends JpaRepository<VectorDocument, Long> {
    
    List<VectorDocument> findByKnowledgeBaseId(Long knowledgeBaseId);
    
    void deleteByKnowledgeBaseId(Long knowledgeBaseId);
    
    /**
     * 插入向量文档（使用显式类型转换）
     */
    @Modifying
    @Query(value = "INSERT INTO vector_documents (knowledge_base_id, content, embedding, metadata, token_count, created_at) " +
                   "VALUES (:knowledgeBaseId, :content, CAST(:embedding AS vector), CAST(:metadata AS jsonb), :tokenCount, CURRENT_TIMESTAMP)",
           nativeQuery = true)
    void insertVectorDocument(
            @Param("knowledgeBaseId") Long knowledgeBaseId,
            @Param("content") String content,
            @Param("embedding") String embedding,
            @Param("metadata") String metadata,
            @Param("tokenCount") Integer tokenCount);
    
    /**
     * 向量相似度搜索（余弦相似度）
     * 使用pgvector的<=>操作符进行余弦距离计算
     */
    @Query(value = "SELECT *, 1 - (embedding <=> CAST(:embedding AS vector)) as similarity " +
                   "FROM vector_documents " +
                   "WHERE knowledge_base_id = :knowledgeBaseId " +
                   "ORDER BY embedding <=> CAST(:embedding AS vector) " +
                   "LIMIT :limit", 
           nativeQuery = true)
    List<VectorDocument> findSimilarDocuments(
            @Param("knowledgeBaseId") Long knowledgeBaseId,
            @Param("embedding") String embedding,
            @Param("limit") int limit);
    
    /**
     * 在多个知识库中搜索相似文档
     */
    @Query(value = "SELECT *, 1 - (embedding <=> CAST(:embedding AS vector)) as similarity " +
                   "FROM vector_documents " +
                   "WHERE knowledge_base_id IN :knowledgeBaseIds " +
                   "ORDER BY embedding <=> CAST(:embedding AS vector) " +
                   "LIMIT :limit", 
           nativeQuery = true)
    List<VectorDocument> findSimilarDocumentsInMultipleKBs(
            @Param("knowledgeBaseIds") List<Long> knowledgeBaseIds,
            @Param("embedding") String embedding,
            @Param("limit") int limit);
}

