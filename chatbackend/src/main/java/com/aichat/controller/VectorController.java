package com.aichat.controller;

import com.aichat.domain.dto.common.ApiResponse;
import com.aichat.domain.entity.KnowledgeBase;
import com.aichat.domain.entity.VectorDocument;
import com.aichat.security.UserPrincipal;
import com.aichat.service.VectorService;
import com.aichat.service.ingest.DocumentSplitter;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/knowledge")
@RequiredArgsConstructor
public class VectorController {
    
    private final VectorService vectorService;
    
    /**
     * 创建知识库
     */
    @PostMapping("/bases")
    public ApiResponse<KnowledgeBase> createKnowledgeBase(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody CreateKnowledgeBaseRequest request) {
        KnowledgeBase kb = vectorService.createKnowledgeBase(
                userPrincipal.getId(),
                request.getTitle(),
                request.getDescription(),
                request.getSourceType(),
                request.getSourceUrl()
        );
        return ApiResponse.success("创建成功", kb);
    }
    
    /**
     * 获取用户的知识库列表
     */
    @GetMapping("/bases")
    public ApiResponse<Page<KnowledgeBase>> getKnowledgeBases(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<KnowledgeBase> bases = vectorService.getUserKnowledgeBases(userPrincipal.getId(), pageable);
        return ApiResponse.success(bases);
    }
    
    /**
     * 获取知识库详情
     */
    @GetMapping("/bases/{id}")
    public ApiResponse<KnowledgeBase> getKnowledgeBase(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long id) {
        KnowledgeBase kb = vectorService.getKnowledgeBase(id, userPrincipal.getId());
        return ApiResponse.success(kb);
    }
    
    /**
     * 删除知识库
     */
    @DeleteMapping("/bases/{id}")
    public ApiResponse<Void> deleteKnowledgeBase(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long id) {
        vectorService.deleteKnowledgeBase(id, userPrincipal.getId());
        return ApiResponse.success("删除成功", null);
    }
    
    /**
     * 获取知识库的文档列表
     */
    @GetMapping("/bases/{id}/documents")
    public ApiResponse<List<VectorDocument>> getDocuments(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long id) {
        List<VectorDocument> documents = vectorService.getDocumentsByKnowledgeBase(id, userPrincipal.getId());
        return ApiResponse.success(documents);
    }
    
    /**
     * 添加文档到知识库
     */
    @PostMapping("/bases/{id}/documents")
    public ApiResponse<VectorDocument> addDocument(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long id,
            @Valid @RequestBody AddDocumentRequest request) {
        VectorDocument document = vectorService.addDocument(
                id,
                userPrincipal.getId(),
                request.getContent(),
                request.getMetadata()
        );
        return ApiResponse.success("添加成功", document);
    }
    
    /**
     * 批量添加文档
     */
    @PostMapping("/bases/{id}/documents/batch")
    public ApiResponse<Void> addDocuments(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long id,
            @Valid @RequestBody BatchAddDocumentRequest request) {
        vectorService.addDocuments(id, userPrincipal.getId(), request.getContents());
        return ApiResponse.success("批量添加成功", null);
    }
    
    /**
     * 在知识库中搜索相似文档
     */
    @PostMapping("/bases/{id}/search")
    public ApiResponse<List<VectorDocument>> searchDocuments(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long id,
            @Valid @RequestBody SearchRequest request) {
        List<VectorDocument> results = vectorService.searchSimilarDocuments(
                id,
                userPrincipal.getId(),
                request.getQuery(),
                request.getTopK()
        );
        return ApiResponse.success(results);
    }
    
    /**
     * 在多个知识库中搜索
     */
    @PostMapping("/search")
    public ApiResponse<List<VectorDocument>> searchInMultipleBases(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody MultiSearchRequest request) {
        List<VectorDocument> results = vectorService.searchInMultipleKnowledgeBases(
                request.getKnowledgeBaseIds(),
                userPrincipal.getId(),
                request.getQuery(),
                request.getTopK()
        );
        return ApiResponse.success(results);
    }
    
    /**
     * 从文件添加文档到知识库
     */
    @PostMapping("/bases/{id}/documents/file")
    public ApiResponse<Void> addDocumentFromFile(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "splitStrategy", defaultValue = "PARAGRAPH") String splitStrategyStr,
            @RequestParam(value = "chunkSize", required = false) Integer chunkSize,
            @RequestParam(value = "overlapSize", required = false) Integer overlapSize) {
        
        DocumentSplitter.SplitStrategy splitStrategy;
        try {
            splitStrategy = DocumentSplitter.SplitStrategy.valueOf(splitStrategyStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            splitStrategy = DocumentSplitter.SplitStrategy.PARAGRAPH;
        }
        
        vectorService.addDocumentFromFile(
                id,
                userPrincipal.getId(),
                file,
                splitStrategy,
                chunkSize,
                overlapSize
        );
        
        return ApiResponse.success("文件上传成功", null);
    }
    
    /**
     * 从URL添加文档到知识库
     */
    @PostMapping("/bases/{id}/documents/url")
    public ApiResponse<Void> addDocumentFromUrl(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long id,
            @Valid @RequestBody AddFromUrlRequest request) {
        
        vectorService.addDocumentFromUrl(
                id,
                userPrincipal.getId(),
                request.getUrl(),
                request.getSplitStrategy(),
                request.getChunkSize(),
                request.getOverlapSize()
        );
        
        return ApiResponse.success("URL内容添加成功", null);
    }
    
    /**
     * 预览文本切分结果（不保存）
     */
    @PostMapping("/bases/{id}/documents/text/preview")
    public ApiResponse<List<String>> previewTextSplit(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long id,
            @Valid @RequestBody AddFromTextRequest request) {
        
        // 验证知识库所有权
        vectorService.getKnowledgeBase(id, userPrincipal.getId());
        
        // 只返回切分结果，不保存
        List<String> chunks = vectorService.previewDocumentSplit(
                request.getText(),
                request.getSplitStrategy(),
                request.getChunkSize(),
                request.getOverlapSize()
        );
        
        return ApiResponse.success("切分预览成功", chunks);
    }
    
    /**
     * 从文本添加文档到知识库（支持切分）
     */
    @PostMapping("/bases/{id}/documents/text")
    public ApiResponse<Void> addDocumentFromText(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long id,
            @Valid @RequestBody AddFromTextRequest request) {
        
        vectorService.addDocumentFromText(
                id,
                userPrincipal.getId(),
                request.getText(),
                request.getSplitStrategy(),
                request.getChunkSize(),
                request.getOverlapSize()
        );
        
        return ApiResponse.success("文本添加成功", null);
    }
    
    /**
     * 预览URL切分结果
     */
    @PostMapping("/bases/{id}/documents/url/preview")
    public ApiResponse<List<String>> previewUrlSplit(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long id,
            @Valid @RequestBody AddFromUrlRequest request) {
        
        // 验证知识库所有权
        vectorService.getKnowledgeBase(id, userPrincipal.getId());
        
        // 获取URL内容并切分预览
        List<String> chunks = vectorService.previewUrlSplit(
                request.getUrl(),
                request.getSplitStrategy(),
                request.getChunkSize(),
                request.getOverlapSize()
        );
        
        return ApiResponse.success("URL切分预览成功", chunks);
    }
    
    /**
     * 预览文件切分结果
     */
    @PostMapping("/bases/{id}/documents/file/preview")
    public ApiResponse<List<String>> previewFileSplit(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "splitStrategy", defaultValue = "PARAGRAPH") String splitStrategyStr,
            @RequestParam(value = "chunkSize", required = false) Integer chunkSize,
            @RequestParam(value = "overlapSize", required = false) Integer overlapSize) {
        
        // 验证知识库所有权
        vectorService.getKnowledgeBase(id, userPrincipal.getId());
        
        DocumentSplitter.SplitStrategy splitStrategy;
        try {
            splitStrategy = DocumentSplitter.SplitStrategy.valueOf(splitStrategyStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            splitStrategy = DocumentSplitter.SplitStrategy.PARAGRAPH;
        }
        
        // 获取文件内容并切分预览
        List<String> chunks = vectorService.previewFileSplit(
                file,
                splitStrategy,
                chunkSize,
                overlapSize
        );
        
        return ApiResponse.success("文件切分预览成功", chunks);
    }
    
    @Data
    static class CreateKnowledgeBaseRequest {
        @NotBlank(message = "标题不能为空")
        private String title;
        
        private String description;
        
        @NotNull(message = "来源类型不能为空")
        private KnowledgeBase.SourceType sourceType;
        
        private String sourceUrl;
    }
    
    @Data
    static class AddDocumentRequest {
        @NotBlank(message = "文档内容不能为空")
        private String content;
        
        private Map<String, Object> metadata;
    }
    
    @Data
    static class BatchAddDocumentRequest {
        @NotNull(message = "文档列表不能为空")
        private List<String> contents;
    }
    
    @Data
    static class SearchRequest {
        @NotBlank(message = "搜索查询不能为空")
        private String query;
        
        private Integer topK;
    }
    
    @Data
    static class MultiSearchRequest {
        @NotNull(message = "知识库ID列表不能为空")
        private List<Long> knowledgeBaseIds;
        
        @NotBlank(message = "搜索查询不能为空")
        private String query;
        
        private Integer topK;
    }
    
    @Data
    static class AddFromUrlRequest {
        @NotBlank(message = "URL不能为空")
        private String url;
        
        private DocumentSplitter.SplitStrategy splitStrategy = DocumentSplitter.SplitStrategy.PARAGRAPH;
        
        private Integer chunkSize;
        
        private Integer overlapSize;
    }
    
    @Data
    static class AddFromTextRequest {
        @NotBlank(message = "文本不能为空")
        private String text;
        
        private DocumentSplitter.SplitStrategy splitStrategy = DocumentSplitter.SplitStrategy.PARAGRAPH;
        
        private Integer chunkSize;
        
        private Integer overlapSize;
    }
}

