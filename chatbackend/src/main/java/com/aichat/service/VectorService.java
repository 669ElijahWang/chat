package com.aichat.service;

import com.aichat.domain.entity.KnowledgeBase;
import com.aichat.domain.entity.VectorDocument;
import com.aichat.exception.BusinessException;
import com.aichat.repository.KnowledgeBaseRepository;
import com.aichat.repository.VectorDocumentRepository;
import com.aichat.service.deepseek.DeepSeekService;
import com.aichat.service.deepseek.dto.EmbeddingRequest;
import com.aichat.service.deepseek.dto.EmbeddingResponse;
import com.aichat.service.embedding.SimpleEmbeddingService;
import com.aichat.service.ingest.DocumentSplitter;
import com.aichat.service.ingest.FileIngestionService;
import com.aichat.service.ingest.UrlIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class VectorService {
    
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final VectorDocumentRepository vectorDocumentRepository;
    private final DeepSeekService deepSeekService;
    private final SimpleEmbeddingService simpleEmbeddingService;
    private final DocumentSplitter documentSplitter;
    private final FileIngestionService fileIngestionService;
    private final UrlIngestionService urlIngestionService;
    
    @Value("${app.vector.top-k:5}")
    private int defaultTopK;
    
    @Value("${app.vector.use-simple-embedding:true}")
    private boolean useSimpleEmbedding;
    
    @Transactional
    public KnowledgeBase createKnowledgeBase(Long userId, String title, String description, 
                                              KnowledgeBase.SourceType sourceType, String sourceUrl) {
        KnowledgeBase knowledgeBase = KnowledgeBase.builder()
                .userId(userId)
                .title(title)
                .description(description)
                .sourceType(sourceType)
                .sourceUrl(sourceUrl)
                .status(KnowledgeBase.Status.ACTIVE)
                .build();
        
        knowledgeBase = knowledgeBaseRepository.save(knowledgeBase);
        log.info("创建知识库: id={}, userId={}, title={}", knowledgeBase.getId(), userId, title);
        
        return knowledgeBase;
    }
    
    @Transactional(readOnly = true)
    public Page<KnowledgeBase> getUserKnowledgeBases(Long userId, Pageable pageable) {
        return knowledgeBaseRepository.findByUserId(userId, pageable);
    }
    
    @Transactional(readOnly = true)
    public KnowledgeBase getKnowledgeBase(Long id, Long userId) {
        return knowledgeBaseRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BusinessException("知识库不存在或无权访问"));
    }
    
    @Transactional
    public void deleteKnowledgeBase(Long id, Long userId) {
        KnowledgeBase kb = getKnowledgeBase(id, userId);
        
        // 删除所有相关文档
        vectorDocumentRepository.deleteByKnowledgeBaseId(id);
        
        // 删除知识库
        knowledgeBaseRepository.delete(kb);
        
        log.info("删除知识库: id={}, userId={}", id, userId);
    }
    
    /**
     * 获取知识库的所有文档
     */
    @Transactional(readOnly = true)
    public List<VectorDocument> getDocumentsByKnowledgeBase(Long knowledgeBaseId, Long userId) {
        // 验证知识库所有权
        getKnowledgeBase(knowledgeBaseId, userId);
        
        return vectorDocumentRepository.findByKnowledgeBaseId(knowledgeBaseId);
    }
    
    @Transactional
    public VectorDocument addDocument(Long knowledgeBaseId, Long userId, String content, 
                                      Map<String, Object> metadata) {
        // 验证知识库所有权
        getKnowledgeBase(knowledgeBaseId, userId);
        
        // 生成嵌入向量
        List<Double> embedding = generateEmbedding(content);
        
        // 创建文档
        VectorDocument document = VectorDocument.builder()
                .knowledgeBaseId(knowledgeBaseId)
                .content(content)
                .metadata(metadata != null ? metadata : new HashMap<>())
                .tokenCount(estimateTokens(content))
                .build();
        
        document.setEmbeddingFromList(embedding);
        document = vectorDocumentRepository.save(document);
        
        log.info("添加文档到知识库: documentId={}, knowledgeBaseId={}", document.getId(), knowledgeBaseId);
        
        return document;
    }
    
    @Transactional
    public void addDocuments(Long knowledgeBaseId, Long userId, List<String> contents) {
        // 验证知识库所有权
        getKnowledgeBase(knowledgeBaseId, userId);
        
        // 批量生成嵌入向量
        List<List<Double>> embeddings = generateEmbeddings(contents);
        
        for (int i = 0; i < contents.size(); i++) {
            String content = contents.get(i);
            List<Double> embedding = embeddings.get(i);
            
            VectorDocument document = VectorDocument.builder()
                    .knowledgeBaseId(knowledgeBaseId)
                    .content(content)
                    .metadata(new HashMap<>())
                    .tokenCount(estimateTokens(content))
                    .build();
            
            document.setEmbeddingFromList(embedding);
            vectorDocumentRepository.save(document);
        }
        
        log.info("批量添加文档到知识库: count={}, knowledgeBaseId={}", contents.size(), knowledgeBaseId);
    }
    
    /**
     * 从文件添加文档（支持切分）
     */
    @Transactional
    public void addDocumentFromFile(Long knowledgeBaseId, Long userId, MultipartFile file, 
                                    DocumentSplitter.SplitStrategy splitStrategy, 
                                    Integer chunkSize, Integer overlapSize) {
        // 验证知识库所有权
        KnowledgeBase kb = getKnowledgeBase(knowledgeBaseId, userId);
        
        // 提取文件文本
        String text = fileIngestionService.extractText(file);
        
        // 切分文本
        List<String> chunks = splitDocument(text, splitStrategy, chunkSize, overlapSize);
        
        // 添加元数据
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", "file");
        metadata.put("filename", file.getOriginalFilename());
        metadata.put("splitStrategy", splitStrategy.name());
        metadata.put("knowledgeBaseTitle", kb.getTitle());
        
        // 批量添加文档
        addDocumentsWithMetadata(knowledgeBaseId, chunks, metadata);
        
        log.info("从文件添加文档到知识库: filename={}, chunks={}, knowledgeBaseId={}", 
                 file.getOriginalFilename(), chunks.size(), knowledgeBaseId);
    }
    
    /**
     * 从URL添加文档（支持切分）
     */
    @Transactional
    public void addDocumentFromUrl(Long knowledgeBaseId, Long userId, String url, 
                                   DocumentSplitter.SplitStrategy splitStrategy, 
                                   Integer chunkSize, Integer overlapSize) {
        // 验证知识库所有权
        KnowledgeBase kb = getKnowledgeBase(knowledgeBaseId, userId);
        
        // 提取URL文本
        String text = urlIngestionService.extractTextFromUrl(url);
        
        // 切分文本
        List<String> chunks = splitDocument(text, splitStrategy, chunkSize, overlapSize);
        
        // 添加元数据
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", "url");
        metadata.put("url", url);
        metadata.put("splitStrategy", splitStrategy.name());
        metadata.put("knowledgeBaseTitle", kb.getTitle());
        
        // 批量添加文档
        addDocumentsWithMetadata(knowledgeBaseId, chunks, metadata);
        
        log.info("从URL添加文档到知识库: url={}, chunks={}, knowledgeBaseId={}", 
                 url, chunks.size(), knowledgeBaseId);
    }
    
    /**
     * 从文本添加文档（支持切分）
     */
    @Transactional
    public void addDocumentFromText(Long knowledgeBaseId, Long userId, String text, 
                                    DocumentSplitter.SplitStrategy splitStrategy, 
                                    Integer chunkSize, Integer overlapSize) {
        // 验证知识库所有权
        KnowledgeBase kb = getKnowledgeBase(knowledgeBaseId, userId);
        
        // 切分文本
        List<String> chunks = splitDocument(text, splitStrategy, chunkSize, overlapSize);
        
        // 添加元数据
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", "text");
        metadata.put("splitStrategy", splitStrategy.name());
        metadata.put("knowledgeBaseTitle", kb.getTitle());
        
        // 批量添加文档
        addDocumentsWithMetadata(knowledgeBaseId, chunks, metadata);
        
        log.info("从文本添加文档到知识库: chunks={}, knowledgeBaseId={}", 
                 chunks.size(), knowledgeBaseId);
    }
    
    /**
     * 预览文本切分结果（不保存到数据库）
     */
    public List<String> previewDocumentSplit(String text, DocumentSplitter.SplitStrategy splitStrategy,
                                             Integer chunkSize, Integer overlapSize) {
        return splitDocument(text, splitStrategy, chunkSize, overlapSize);
    }
    
    /**
     * 预览URL切分结果
     */
    public List<String> previewUrlSplit(String url, DocumentSplitter.SplitStrategy splitStrategy,
                                        Integer chunkSize, Integer overlapSize) {
        String text = urlIngestionService.extractTextFromUrl(url);
        return splitDocument(text, splitStrategy, chunkSize, overlapSize);
    }
    
    /**
     * 预览文件切分结果
     */
    public List<String> previewFileSplit(MultipartFile file, DocumentSplitter.SplitStrategy splitStrategy,
                                         Integer chunkSize, Integer overlapSize) {
        String text = fileIngestionService.extractText(file);
        return splitDocument(text, splitStrategy, chunkSize, overlapSize);
    }
    
    /**
     * 切分文档
     */
    private List<String> splitDocument(String text, DocumentSplitter.SplitStrategy splitStrategy, 
                                      Integer chunkSize, Integer overlapSize) {
        if (splitStrategy == null) {
            splitStrategy = DocumentSplitter.SplitStrategy.PARAGRAPH;
        }
        
        if (chunkSize == null) {
            chunkSize = 500;
        }
        
        if (overlapSize == null) {
            overlapSize = 50;
        }
        
        return documentSplitter.split(text, splitStrategy, chunkSize, overlapSize);
    }
    
    /**
     * 批量添加文档（带元数据）
     */
    private void addDocumentsWithMetadata(Long knowledgeBaseId, List<String> contents, 
                                          Map<String, Object> baseMetadata) {
        // 批量生成嵌入向量
        List<List<Double>> embeddings = generateEmbeddings(contents);
        
        for (int i = 0; i < contents.size(); i++) {
            String content = contents.get(i);
            List<Double> embedding = embeddings.get(i);
            
            // 为每个chunk添加索引
            Map<String, Object> metadata = new HashMap<>(baseMetadata);
            metadata.put("chunkIndex", i);
            metadata.put("totalChunks", contents.size());
            
            // 格式化embedding为字符串 [1.0,2.0,3.0,...]
            String embeddingStr = "[" + String.join(",", 
                embedding.stream()
                    .map(String::valueOf)
                    .toArray(String[]::new)) + "]";
            
            // 格式化metadata为JSON字符串
            String metadataJson = convertMapToJson(metadata);
            
            // 使用原生SQL插入（支持vector类型转换）
            vectorDocumentRepository.insertVectorDocument(
                knowledgeBaseId,
                content,
                embeddingStr,
                metadataJson,
                estimateTokens(content)
            );
        }
    }
    
    /**
     * 将Map转换为JSON字符串
     */
    private String convertMapToJson(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return "{}";
        }
        
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) {
                json.append(",");
            }
            json.append("\"").append(entry.getKey()).append("\":");
            
            Object value = entry.getValue();
            if (value instanceof String) {
                json.append("\"").append(value.toString().replace("\"", "\\\"")).append("\"");
            } else if (value instanceof Number || value instanceof Boolean) {
                json.append(value);
            } else {
                json.append("\"").append(value).append("\"");
            }
            first = false;
        }
        json.append("}");
        return json.toString();
    }
    
    /**
     * 在知识库中搜索相似文档
     */
    @Transactional(readOnly = true)
    public List<VectorDocument> searchSimilarDocuments(Long knowledgeBaseId, Long userId, 
                                                       String query, Integer topK) {
        // 验证知识库所有权
        getKnowledgeBase(knowledgeBaseId, userId);
        
        // 生成查询向量
        List<Double> queryEmbedding = generateEmbedding(query);
        String embeddingStr = formatEmbeddingForQuery(queryEmbedding);
        
        // 执行向量搜索
        int limit = topK != null ? topK : defaultTopK;
        List<VectorDocument> results = vectorDocumentRepository.findSimilarDocuments(
                knowledgeBaseId, embeddingStr, limit);
        
        log.info("向量搜索: knowledgeBaseId={}, query={}, results={}", 
                 knowledgeBaseId, query, results.size());
        
        return results;
    }
    
    /**
     * 在多个知识库中搜索相似文档
     */
    @Transactional(readOnly = true)
    public List<VectorDocument> searchInMultipleKnowledgeBases(List<Long> knowledgeBaseIds, 
                                                                Long userId, String query, Integer topK) {
        // 验证所有知识库的所有权
        for (Long kbId : knowledgeBaseIds) {
            getKnowledgeBase(kbId, userId);
        }
        
        // 生成查询向量
        List<Double> queryEmbedding = generateEmbedding(query);
        String embeddingStr = formatEmbeddingForQuery(queryEmbedding);
        
        // 执行向量搜索
        int limit = topK != null ? topK : defaultTopK;
        List<VectorDocument> results = vectorDocumentRepository.findSimilarDocumentsInMultipleKBs(
                knowledgeBaseIds, embeddingStr, limit);
        
        log.info("多知识库向量搜索: knowledgeBaseIds={}, query={}, results={}", 
                 knowledgeBaseIds, query, results.size());
        
        return results;
    }
    
    private List<Double> generateEmbedding(String text) {
        // 优先使用简单嵌入服务（本地计算，不需要API）
        if (useSimpleEmbedding) {
            log.debug("Using simple embedding service for text");
            return simpleEmbeddingService.generateEmbedding(text);
        }
        
        // 尝试使用DeepSeek API（降级方案）
        try {
            EmbeddingRequest request = EmbeddingRequest.builder()
                    .input(text)
                    .build();
            
            EmbeddingResponse response = deepSeekService.createEmbedding(request).block();
            
            if (response == null || response.getData().isEmpty()) {
                throw new BusinessException("生成嵌入向量失败");
            }
            
            return response.getData().get(0).getEmbedding();
        } catch (Exception e) {
            log.warn("DeepSeek embedding API failed, falling back to simple embedding: {}", e.getMessage());
            return simpleEmbeddingService.generateEmbedding(text);
        }
    }
    
    private List<List<Double>> generateEmbeddings(List<String> texts) {
        // 优先使用简单嵌入服务
        if (useSimpleEmbedding) {
            log.debug("Using simple embedding service for {} texts", texts.size());
            return simpleEmbeddingService.generateEmbeddings(texts);
        }
        
        // 尝试使用DeepSeek API（降级方案）
        try {
            log.info("Creating embeddings for {} texts", texts.size());
            EmbeddingRequest request = EmbeddingRequest.builder()
                    .input(texts)
                    .build();
            
            EmbeddingResponse response = deepSeekService.createEmbeddings(request).block();
            
            if (response == null || response.getData().isEmpty()) {
                throw new BusinessException("批量生成嵌入向量失败");
            }
            
            return response.getData().stream()
                    .map(EmbeddingResponse.EmbeddingData::getEmbedding)
                    .toList();
        } catch (Exception e) {
            log.warn("DeepSeek embedding API failed, falling back to simple embedding: {}", e.getMessage());
            return simpleEmbeddingService.generateEmbeddings(texts);
        }
    }
    
    private String formatEmbeddingForQuery(List<Double> embedding) {
        return "[" + String.join(",", 
                embedding.stream()
                    .map(String::valueOf)
                    .toArray(String[]::new)) + "]";
    }
    
    private int estimateTokens(String text) {
        // 简单估算：中文约1.5字符/token，英文约4字符/token
        return (int) (text.length() / 2.5);
    }
}

