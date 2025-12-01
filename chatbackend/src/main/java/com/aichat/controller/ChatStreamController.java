package com.aichat.controller;

import com.aichat.domain.dto.chat.ChatRequest;
import com.aichat.domain.entity.Message;
import com.aichat.domain.entity.VectorDocument;
import com.aichat.exception.BusinessException;
import com.aichat.repository.ConversationRepository;
import com.aichat.repository.MessageRepository;
import com.aichat.security.UserPrincipal;
import com.aichat.service.ConversationService;
import com.aichat.service.VectorService;
import com.aichat.service.ingest.FileIngestionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.util.retry.Retry;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.time.Duration;

@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
@Slf4j
public class ChatStreamController {
    private static final int MAX_TOKENS_UPPER_BOUND = 32768; // 安全上限，避免 400
    
    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationService conversationService;
    private final ObjectMapper objectMapper;
    private final WebClient.Builder webClientBuilder;
    private final FileIngestionService fileIngestionService;
    private final VectorService vectorService;
    
    @Value("${deepseek.api.api-key}")
    private String deepSeekApiKey;
    
    @Value("${deepseek.api.base-url}")
    private String deepSeekBaseUrl;

    @Value("${deepseek.api.max-retries:3}")
    private int deepSeekMaxRetries;
    
    @Value("${dashscope.api-key}")
    private String qwenApiKey;
    
    @Value("${qwen.api.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}")
    private String qwenBaseUrl;
    
    @Value("${zhipu.api.api-key:}")
    private String zhipuApiKey;
    
    @Value("${zhipu.api.base-url:https://open.bigmodel.cn/api/paas/v4}")
    private String zhipuBaseUrl;
    
    // 后端默认的最大输出 token，当前端未显式传递时使用；可在 application.yml 中通过 app.chat.max-tokens-default 配置
    @Value("${app.chat.max-tokens-default:3500}")
    private int defaultMaxTokens;
    
    /**
     * 流式聊天接口
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestBody ChatRequest request,
            jakarta.servlet.http.HttpServletResponse response) {
        
        Long userId = userPrincipal.getId();
        
        // 验证会话
        conversationRepository.findByIdAndUserId(request.getConversationId(), userId)
                .orElseThrow(() -> new BusinessException("会话不存在或无权访问"));
        
        // 保存用户消息
        Message userMessage = Message.builder()
                .conversationId(request.getConversationId())
                .userId(userId)
                .role(Message.MessageRole.USER)
                .content(request.getContent())
                .status(Message.MessageStatus.COMPLETED)
                .build();
        messageRepository.save(userMessage);
        
        // 更新会话时间
        conversationService.updateLastMessageTime(request.getConversationId());
        
        // 获取历史消息（最近10条）
        List<Message> historyMessages = messageRepository
                .findByConversationIdOrderByCreatedAtAsc(request.getConversationId())
                .stream()
                .skip(Math.max(0, messageRepository.countByConversationId(request.getConversationId()) - 10))
                .toList();
        
        // 构建DeepSeek请求
        Map<String, Object> requestBody = new HashMap<>();
        String selectedModel = request.getModel() != null ? request.getModel() : "deepseek-chat";
        requestBody.put("model", selectedModel);
        requestBody.put("stream", true);
        requestBody.put("temperature", request.getTemperature() != null ? request.getTemperature() : 0.7);
        // 支持前端指定最大tokens，未传时使用可配置的后端默认值
        int maxTokens = request.getMaxTokens() != null ? clampMaxTokens(request.getMaxTokens()) : clampMaxTokens(defaultMaxTokens);
        requestBody.put("max_tokens", maxTokens);
        
        List<VectorDocument> ragDocsLocal = new ArrayList<>();
        if (request.getKnowledgeBaseIds() != null && !request.getKnowledgeBaseIds().isEmpty()) {
            try {
                Integer topK = request.getRagTopK() != null ? request.getRagTopK() : 3;
                ragDocsLocal = vectorService.searchInMultipleKnowledgeBases(
                        request.getKnowledgeBaseIds(), userId, request.getContent(), topK);
            } catch (Exception e) {
                log.warn("RAG检索失败，继续普通对话: {}", e.getMessage());
            }
        }
        final List<VectorDocument> ragDocs = ragDocsLocal;

        List<Map<String, String>> messages = new ArrayList<>();
        if (!ragDocs.isEmpty()) {
            String context = buildContextFromDocuments(ragDocs);
            Map<String, String> sys = new HashMap<>();
            sys.put("role", "system");
            sys.put("content", "以下是相关的知识库内容，请基于这些内容回答用户的问题：\n\n" + context);
            messages.add(sys);
        }
        messages.addAll(historyMessages.stream()
                .map(msg -> {
                    Map<String, String> m = new HashMap<>();
                    m.put("role", msg.getRole().name().toLowerCase());
                    m.put("content", msg.getContent());
                    return m;
                })
                .collect(Collectors.toList()));
        requestBody.put("messages", messages);
        
        // 累积完整响应
        AtomicReference<StringBuilder> fullContent = new AtomicReference<>(new StringBuilder());
        
        // 创建WebClient
        ProviderConfig providerConfig = resolveProvider(selectedModel);
        WebClient webClient = webClientBuilder
                .baseUrl(providerConfig.baseUrl)
                .defaultHeader("Authorization", "Bearer " + providerConfig.apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
        
        // 设置 SSE 友好响应头，避免容器/代理缓冲
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Connection", "keep-alive");

        // 使用 SseEmitter 以在 Servlet 环境下稳定推送 SSE
        SseEmitter emitter = new SseEmitter(0L); // 不超时，交由代理与客户端控制

        webClient.post()
                .uri(providerConfig.uriPath)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToFlux(String.class)
                .retryWhen(Retry
                        .backoff(Math.max(0, deepSeekMaxRetries), Duration.ofSeconds(2))
                        .filter(err -> err instanceof WebClientRequestException)
                        .onRetryExhaustedThrow((spec, signal) -> signal.failure()))
                .filter(line -> line != null && !line.isBlank())
                .map(line -> line.startsWith("data: ") ? line.substring(6).trim() : line.trim())
                .subscribe(jsonData -> {
                    try {
                        if ("[DONE]".equals(jsonData)) {
                            String content = fullContent.get().toString();
                            if (!content.isEmpty()) {
                                Message assistantMessage = Message.builder()
                                        .conversationId(request.getConversationId())
                                        .userId(userId)
                                        .role(Message.MessageRole.ASSISTANT)
                                        .content(content)
                                        .metadata(ragDocs != null && !ragDocs.isEmpty() ? buildRagMetadata(ragDocs) : null)
                                        .status(Message.MessageStatus.COMPLETED)
                                        .build();
                                messageRepository.save(assistantMessage);
                                conversationService.updateLastMessageTime(request.getConversationId());
                                log.info("流式响应完成: conversationId={}, length={}", request.getConversationId(), content.length());
                                // 发送最终完整内容事件，前端可一次性渲染预览
                                emitter.send(SseEmitter.event().name("final").data(content));
                            }
                            emitter.send(SseEmitter.event().data("[DONE]"));
                            emitter.complete();
                            return;
                        }

                        JsonNode node = objectMapper.readTree(jsonData);
                        JsonNode choices = node.get("choices");
                        if (choices != null && choices.isArray() && !choices.isEmpty()) {
                            JsonNode delta = choices.get(0).get("delta");
                            if (delta != null && delta.has("content")) {
                                String content = delta.get("content").asText();
                                fullContent.get().append(content);
                                emitter.send(SseEmitter.event().data(content));
                            }
                        }
                    } catch (Exception e) {
                        log.error("解析流式响应失败: {}", jsonData, e);
                    }
                }, error -> {
                    try {
                        String errorMsg;
                        String body = null;
                        Integer status = null;
                        if (error instanceof org.springframework.web.reactive.function.client.WebClientResponseException wcre) {
                            status = wcre.getStatusCode().value();
                            errorMsg = "HTTP " + status + " " + wcre.getStatusText();
                            body = wcre.getResponseBodyAsString();
                        } else {
                            errorMsg = error.getMessage();
                        }
                        if (errorMsg != null && errorMsg.contains("Connection timed out")) {
                            errorMsg = "连接AI服务超时，请检查网络连接或稍后重试";
                        } else if (errorMsg != null && errorMsg.contains("Connection refused")) {
                            errorMsg = "无法连接到AI服务，请检查网络配置";
                        }
                        Map<String, Object> payload = new HashMap<>();
                        payload.put("type", status != null && status >= 400 && status < 500 ? "http" : "network");
                        if (status != null) payload.put("status", status);
                        payload.put("message", errorMsg);
                        if (body != null && !body.isBlank()) payload.put("body", body);
                        emitter.send(SseEmitter.event().name("error").data(objectMapper.writeValueAsString(payload)));
                        emitter.complete();
                    } catch (Exception e) {
                        log.error("发送错误消息失败", e);
                    }
                });

        return emitter;
    }
    
    /**
     * 带文件上传的流式聊天接口
     */
    @PostMapping(value = "/messages/with-file", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStreamWithFile(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestParam("conversationId") Long conversationId,
            @RequestParam("content") String content,
            @RequestParam(value = "model", defaultValue = "deepseek-chat") String model,
            @RequestParam(value = "temperature", defaultValue = "0.7") Double temperature,
            @RequestParam(value = "maxTokens", required = false) Integer maxTokens,
            @RequestParam(value = "knowledgeBaseIds", required = false) List<Long> knowledgeBaseIds,
            @RequestParam(value = "ragTopK", required = false) Integer ragTopK,
            @RequestParam("file") MultipartFile file,
            jakarta.servlet.http.HttpServletResponse response) {
        
        Long userId = userPrincipal.getId();
        
        // 验证会话
        conversationRepository.findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new BusinessException("会话不存在或无权访问"));
        
        // 提取文件内容
        String fileContent;
        String fileName = file.getOriginalFilename();
        try {
            fileContent = fileIngestionService.extractText(file);
            log.info("从文件提取文本成功: filename={}, contentLength={}", fileName, fileContent.length());
        } catch (Exception e) {
            log.error("文件提取失败: {}", fileName, e);
            throw new BusinessException("文件处理失败: " + e.getMessage());
        }
        
        // 保存用户消息（只保存原始问题，不包含文件内容）
        String userDisplayContent = content;
        if (content.equals("请帮我分析这个文件") || content.trim().isEmpty()) {
            userDisplayContent = "上传了文件: " + fileName;
        }
        
        Message userMessage = Message.builder()
                .conversationId(conversationId)
                .userId(userId)
                .role(Message.MessageRole.USER)
                .content(userDisplayContent)
                .status(Message.MessageStatus.COMPLETED)
                .build();
        messageRepository.save(userMessage);
        
        // 将文件内容与用户问题结合用于AI处理
        String combinedContent = "【用户上传了文件: " + fileName + "】\n\n"
                + "【文件内容】：\n" + fileContent + "\n\n"
                + "【用户问题】：\n" + content;
        
        // 更新会话时间
        conversationService.updateLastMessageTime(conversationId);
        
        // 获取历史消息（最近10条）
        List<Message> historyMessages = messageRepository
                .findByConversationIdOrderByCreatedAtAsc(conversationId)
                .stream()
                .skip(Math.max(0, messageRepository.countByConversationId(conversationId) - 10))
                .toList();
        
        // 构建DeepSeek请求
        Map<String, Object> requestBody = new HashMap<>();
        String selectedModel = model;
        requestBody.put("model", selectedModel);
        requestBody.put("stream", true);
        requestBody.put("temperature", temperature);
        requestBody.put("max_tokens", clampMaxTokens(maxTokens != null ? maxTokens : defaultMaxTokens));
        
        // 构建消息历史，排除刚保存的用户消息（因为我们要用包含文件内容的版本）
        List<VectorDocument> ragDocsLocal = new ArrayList<>();
        if (knowledgeBaseIds != null && !knowledgeBaseIds.isEmpty()) {
            try {
                Integer topK = ragTopK != null ? ragTopK : 3;
                ragDocsLocal = vectorService.searchInMultipleKnowledgeBases(knowledgeBaseIds, userId, content, topK);
            } catch (Exception e) {
                log.warn("RAG检索失败，继续普通对话: {}", e.getMessage());
            }
        }
        final List<VectorDocument> ragDocs = ragDocsLocal;

        List<Map<String, String>> messages = historyMessages.stream()
                .limit(Math.max(0, historyMessages.size() - 1))
                .map(msg -> {
                    Map<String, String> m = new HashMap<>();
                    m.put("role", msg.getRole().name().toLowerCase());
                    m.put("content", msg.getContent());
                    return m;
                })
                .collect(Collectors.toList());
        
        if (!ragDocs.isEmpty()) {
            String context = buildContextFromDocuments(ragDocs);
            Map<String, String> sys = new HashMap<>();
            sys.put("role", "system");
            sys.put("content", "以下是相关的知识库内容，请基于这些内容回答用户的问题：\n\n" + context);
            messages.add(0, sys);
        }
        
        Map<String, String> currentMsg = new HashMap<>();
        currentMsg.put("role", "user");
        currentMsg.put("content", combinedContent);
        messages.add(currentMsg);
        
        requestBody.put("messages", messages);
        
        // 累积完整响应
        AtomicReference<StringBuilder> fullContent = new AtomicReference<>(new StringBuilder());
        
        // 创建WebClient
        ProviderConfig providerConfig = resolveProvider(selectedModel);
        WebClient webClient = webClientBuilder
                .baseUrl(providerConfig.baseUrl)
                .defaultHeader("Authorization", "Bearer " + providerConfig.apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
        
        // 设置 SSE 友好响应头
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Connection", "keep-alive");

        SseEmitter emitter = new SseEmitter(0L);

        webClient.post()
                .uri(providerConfig.uriPath)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToFlux(String.class)
                .filter(line -> line != null && !line.isBlank())
                .map(line -> line.startsWith("data: ") ? line.substring(6).trim() : line.trim())
                .subscribe(jsonData -> {
                    try {
                        if ("[DONE]".equals(jsonData)) {
                            String responseContent = fullContent.get().toString();
                            if (!responseContent.isEmpty()) {
                                Message assistantMessage = Message.builder()
                                        .conversationId(conversationId)
                                        .userId(userId)
                                        .role(Message.MessageRole.ASSISTANT)
                                        .content(responseContent)
                                        .metadata(ragDocs != null && !ragDocs.isEmpty() ? buildRagMetadata(ragDocs) : null)
                                        .status(Message.MessageStatus.COMPLETED)
                                        .build();
                                messageRepository.save(assistantMessage);
                                conversationService.updateLastMessageTime(conversationId);
                                log.info("文件聊天流式响应完成: conversationId={}, fileName={}, length={}", 
                                         conversationId, fileName, responseContent.length());
                            }
                            emitter.send(SseEmitter.event().data("[DONE]"));
                            emitter.complete();
                            return;
                        }

                        JsonNode node = objectMapper.readTree(jsonData);
                        JsonNode choices = node.get("choices");
                        if (choices != null && choices.isArray() && !choices.isEmpty()) {
                            JsonNode delta = choices.get(0).get("delta");
                            if (delta != null && delta.has("content")) {
                                String chunk = delta.get("content").asText();
                                fullContent.get().append(chunk);
                                emitter.send(SseEmitter.event().data(chunk));
                            }
                        }
                    } catch (Exception e) {
                        log.error("解析流式响应失败: {}", jsonData, e);
                    }
                }, error -> {
                    try {
                        String errorMsg;
                        String body = null;
                        Integer status = null;
                        if (error instanceof org.springframework.web.reactive.function.client.WebClientResponseException wcre) {
                            status = wcre.getStatusCode().value();
                            errorMsg = "HTTP " + status + " " + wcre.getStatusText();
                            body = wcre.getResponseBodyAsString();
                        } else {
                            errorMsg = error.getMessage();
                        }
                        if (errorMsg != null && errorMsg.contains("Connection timed out")) {
                            errorMsg = "连接AI服务超时，请检查网络连接或稍后重试";
                        } else if (errorMsg != null && errorMsg.contains("Connection refused")) {
                            errorMsg = "无法连接到AI服务，请检查网络配置";
                        }
                        Map<String, Object> payload = new HashMap<>();
                        payload.put("type", status != null && status >= 400 && status < 500 ? "http" : "network");
                        if (status != null) payload.put("status", status);
                        payload.put("message", errorMsg);
                        if (body != null && !body.isBlank()) payload.put("body", body);
                        emitter.send(SseEmitter.event().name("error").data(objectMapper.writeValueAsString(payload)));
                        emitter.complete();
                    } catch (Exception e) {
                        log.error("发送错误消息失败", e);
                    }
                });

        return emitter;
    }

    private String buildRagMetadata(List<VectorDocument> docs) {
        if (docs == null || docs.isEmpty()) return null;
        StringBuilder json = new StringBuilder("{\"ragDocs\":[");
        for (int i = 0; i < docs.size(); i++) {
            if (i > 0) json.append(",");
            VectorDocument doc = docs.get(i);
            String content = doc.getContent().length() > 200 ? doc.getContent().substring(0, 200) + "..." : doc.getContent();
            content = content.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
            String kbTitle = doc.getMetadata() != null && doc.getMetadata().containsKey("knowledgeBaseTitle")
                    ? doc.getMetadata().get("knowledgeBaseTitle").toString() : "未知知识库";
            kbTitle = kbTitle.replace("\"", "\\\"");
            json.append("{")
                .append("\"documentId\":").append(doc.getId()).append(",")
                .append("\"content\":\"").append(content).append("\",")
                .append("\"knowledgeBaseTitle\":\"").append(kbTitle).append("\"")
                .append("}");
        }
        json.append("]}");
        return json.toString();
    }

    private String buildContextFromDocuments(List<VectorDocument> documents) {
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < documents.size(); i++) {
            VectorDocument doc = documents.get(i);
            context.append("【文档").append(i + 1).append("】\n");
            context.append(doc.getContent()).append("\n\n");
        }
        return context.toString().trim();
    }

    private int clampMaxTokens(int tokens) {
        return Math.min(Math.max(1, tokens), MAX_TOKENS_UPPER_BOUND);
    }

    private ProviderConfig resolveProvider(String model) {
        String m = model == null ? "" : model.toLowerCase();
        if (m.contains("qwen")) {
            return new ProviderConfig(qwenBaseUrl, qwenApiKey, "/chat/completions");
        }
        if (m.contains("glm") || m.contains("zhipu")) {
            return new ProviderConfig(zhipuBaseUrl, zhipuApiKey, "/chat/completions");
        }
        return new ProviderConfig(deepSeekBaseUrl, deepSeekApiKey, "/v1/chat/completions");
    }

    private static class ProviderConfig {
        final String baseUrl;
        final String apiKey;
        final String uriPath;
        ProviderConfig(String baseUrl, String apiKey, String uriPath) {
            this.baseUrl = baseUrl;
            this.apiKey = apiKey;
            this.uriPath = uriPath;
        }
    }
}
