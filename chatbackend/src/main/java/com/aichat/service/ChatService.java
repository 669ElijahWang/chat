package com.aichat.service;

import com.aichat.config.RabbitMQConfig;
import com.aichat.domain.dto.chat.ChatRequest;
import com.aichat.domain.dto.chat.ChatResponse;
import com.aichat.domain.entity.Message;
import com.aichat.domain.entity.VectorDocument;
import com.aichat.exception.BusinessException;
import com.aichat.repository.ConversationRepository;
import com.aichat.repository.MessageRepository;
import com.aichat.service.deepseek.DeepSeekService;
import com.aichat.service.deepseek.dto.ChatCompletionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {
    
    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationService conversationService;
    private final DeepSeekService deepSeekService;
    private final ChatHistoryService chatHistoryService;
    private final RabbitTemplate rabbitTemplate;
    private final VectorService vectorService;
    
    @Transactional
    public Mono<ChatResponse> chat(Long userId, ChatRequest request) {
        // 验证会话是否存在且属于当前用户
        return Mono.fromCallable(() -> {
            conversationRepository.findByIdAndUserId(request.getConversationId(), userId)
                    .orElseThrow(() -> new BusinessException("会话不存在或无权访问"));
            
            // RAG检索相关文档（如果有知识库）
            List<VectorDocument> ragDocs = new ArrayList<>();
            if (request.getKnowledgeBaseIds() != null && !request.getKnowledgeBaseIds().isEmpty()) {
                try {
                    Integer topK = request.getRagTopK() != null ? request.getRagTopK() : 3;
                    ragDocs = vectorService.searchInMultipleKnowledgeBases(
                            request.getKnowledgeBaseIds(),
                            userId,
                            request.getContent(),
                            topK
                    );
                    log.info("RAG检索成功: knowledgeBaseIds={}, foundDocs={}", 
                             request.getKnowledgeBaseIds(), ragDocs.size());
                } catch (Exception e) {
                    log.warn("RAG检索失败，继续普通对话: {}", e.getMessage());
                }
            }
            return ragDocs;
        })
        .flatMap(ragDocs -> {
            // 保存用户消息
            saveUserMessage(userId, request.getConversationId(), request.getContent());
            
            // 获取历史消息
            List<Message> historyMessages = messageRepository
                    .findByConversationIdOrderByCreatedAtAsc(request.getConversationId());
            
            // 构建DeepSeek请求
            ChatCompletionRequest deepSeekRequest = buildDeepSeekRequestWithRag(
                    historyMessages, request, userId, ragDocs);
            
            // 调用DeepSeek API（异步通过RabbitMQ）
            if (request.getStream()) {
                // 流式响应
                return handleStreamResponse(userId, request.getConversationId(), deepSeekRequest, ragDocs);
            } else {
                // 同步响应
                return handleSyncResponse(userId, request.getConversationId(), deepSeekRequest, ragDocs);
            }
        });
    }
    
    private Message saveUserMessage(Long userId, Long conversationId, String content) {
        Message message = Message.builder()
                .conversationId(conversationId)
                .userId(userId)
                .role(Message.MessageRole.USER)
                .content(content)
                .status(Message.MessageStatus.COMPLETED)
                .build();
        
        message = messageRepository.save(message);
        
        // 更新会话最后消息时间
        conversationService.updateLastMessageTime(conversationId);
        
        // 保存到ES搜索索引
        chatHistoryService.indexMessage(message);
        
        log.info("保存用户消息: messageId={}, conversationId={}", message.getId(), conversationId);
        
        return message;
    }
    
    private Message saveAssistantMessage(Long userId, Long conversationId, String content, Integer tokens) {
        return saveAssistantMessage(userId, conversationId, content, tokens, null);
    }
    
    private Message saveAssistantMessage(Long userId, Long conversationId, String content, Integer tokens, 
                                        List<VectorDocument> ragDocs) {
        Message message = Message.builder()
                .conversationId(conversationId)
                .userId(userId)
                .role(Message.MessageRole.ASSISTANT)
                .content(content)
                .tokens(tokens)
                .status(Message.MessageStatus.COMPLETED)
                .build();
        
        // 如果有RAG文档，存储到metadata
        if (ragDocs != null && !ragDocs.isEmpty()) {
            message.setMetadata(buildRagMetadata(ragDocs));
        }
        
        message = messageRepository.save(message);
        
        // 更新会话最后消息时间
        conversationService.updateLastMessageTime(conversationId);
        
        // 保存到ES搜索索引
        chatHistoryService.indexMessage(message);
        
        log.info("保存助手消息: messageId={}, conversationId={}, tokens={}, ragDocs={}", 
                 message.getId(), conversationId, tokens, ragDocs != null ? ragDocs.size() : 0);
        
        return message;
    }
    
    /**
     * 构建RAG元数据JSON字符串
     */
    private String buildRagMetadata(List<VectorDocument> docs) {
        if (docs == null || docs.isEmpty()) {
            return null;
        }
        
        StringBuilder json = new StringBuilder("{\"ragDocs\":[");
        for (int i = 0; i < docs.size(); i++) {
            if (i > 0) json.append(",");
            VectorDocument doc = docs.get(i);
            String content = doc.getContent().length() > 200 
                    ? doc.getContent().substring(0, 200) + "..." 
                    : doc.getContent();
            content = content.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
            
            String kbTitle = doc.getMetadata() != null && doc.getMetadata().containsKey("knowledgeBaseTitle") 
                    ? doc.getMetadata().get("knowledgeBaseTitle").toString() 
                    : "未知知识库";
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
    
    private ChatCompletionRequest buildDeepSeekRequestWithRag(List<Message> historyMessages, 
                                                               ChatRequest request, Long userId, 
                                                               List<VectorDocument> ragDocs) {
        List<ChatCompletionRequest.Message> messages = new ArrayList<>();
        
        // 如果有RAG文档，添加系统消息作为上下文
        if (!ragDocs.isEmpty()) {
            String context = buildContextFromDocuments(ragDocs);
            messages.add(ChatCompletionRequest.Message.builder()
                    .role("system")
                    .content("以下是相关的知识库内容，请基于这些内容回答用户的问题：\n\n" + context)
                    .build());
        }
        
        // 添加历史消息
        messages.addAll(historyMessages.stream()
                .map(msg -> ChatCompletionRequest.Message.builder()
                        .role(msg.getRole().name().toLowerCase())
                        .content(msg.getContent())
                        .build())
                .collect(Collectors.toList()));
        
        return ChatCompletionRequest.builder()
                .model(request.getModel())
                .messages(messages)
                .temperature(request.getTemperature() != null ? request.getTemperature() : 0.7)
                .maxTokens(2000)
                .stream(request.getStream())
                .build();
    }
    
    /**
     * 从检索到的文档构建上下文
     */
    private String buildContextFromDocuments(List<VectorDocument> documents) {
        StringBuilder context = new StringBuilder();
        
        for (int i = 0; i < documents.size(); i++) {
            VectorDocument doc = documents.get(i);
            context.append("【文档").append(i + 1).append("】\n");
            context.append(doc.getContent());
            context.append("\n\n");
        }
        
        return context.toString().trim();
    }
    
    private Mono<ChatResponse> handleSyncResponse(Long userId, Long conversationId, 
                                                    ChatCompletionRequest request,
                                                    List<VectorDocument> ragDocs) {
        return deepSeekService.chatCompletion(request)
                .map(response -> {
                    String content = response.getChoices().get(0).getMessage().getContent();
                    Integer tokens = response.getUsage() != null ? response.getUsage().getTotalTokens() : null;
                    
                    Message assistantMessage = saveAssistantMessage(userId, conversationId, content, tokens, ragDocs);
                    
                    return ChatResponse.builder()
                            .messageId(assistantMessage.getId())
                            .conversationId(conversationId)
                            .role(assistantMessage.getRole().name())
                            .content(content)
                            .tokens(tokens)
                            .createdAt(assistantMessage.getCreatedAt())
                            .ragDocs(convertToRagDocInfo(ragDocs))
                            .build();
                })
                .doOnError(error -> {
                    log.error("Chat completion error: ", error);
                    throw new BusinessException("AI响应失败，请稍后重试");
                });
    }
    
    private Mono<ChatResponse> handleStreamResponse(Long userId, Long conversationId, 
                                                      ChatCompletionRequest request,
                                                      List<VectorDocument> ragDocs) {
        // 发送到RabbitMQ队列异步处理流式响应
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.CHAT_EXCHANGE,
                RabbitMQConfig.CHAT_ROUTING_KEY,
                request
        );
        
        // 返回pending状态
        return Mono.just(ChatResponse.builder()
                .conversationId(conversationId)
                .role("assistant")
                .content("")
                .ragDocs(convertToRagDocInfo(ragDocs))
                .build());
    }
    
    /**
     * 将VectorDocument转换为RAG文档信息
     */
    private List<ChatResponse.RagDocumentInfo> convertToRagDocInfo(List<VectorDocument> docs) {
        if (docs == null || docs.isEmpty()) {
            return null;
        }
        
        return docs.stream()
                .map(doc -> {
                    // 获取知识库标题
                    String kbTitle = doc.getMetadata() != null && doc.getMetadata().containsKey("knowledgeBaseTitle") 
                            ? doc.getMetadata().get("knowledgeBaseTitle").toString() 
                            : "未知知识库";
                    
                    return ChatResponse.RagDocumentInfo.builder()
                            .documentId(doc.getId())
                            .content(doc.getContent().length() > 200 
                                    ? doc.getContent().substring(0, 200) + "..." 
                                    : doc.getContent())
                            .knowledgeBaseTitle(kbTitle)
                            .build();
                })
                .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public List<ChatResponse> getConversationMessages(Long conversationId, Long userId) {
        // 验证会话所有权
        conversationRepository.findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new BusinessException("会话不存在或无权访问"));
        
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }
    
    private ChatResponse toResponse(Message message) {
        ChatResponse.ChatResponseBuilder builder = ChatResponse.builder()
                .messageId(message.getId())
                .conversationId(message.getConversationId())
                .role(message.getRole().name())
                .content(message.getContent())
                .tokens(message.getTokens())
                .createdAt(message.getCreatedAt());
        
        // 解析metadata中的ragDocs信息
        if (message.getMetadata() != null && !message.getMetadata().isEmpty()) {
            List<ChatResponse.RagDocumentInfo> ragDocs = parseRagDocsFromMetadata(message.getMetadata());
            if (ragDocs != null && !ragDocs.isEmpty()) {
                builder.ragDocs(ragDocs);
            }
        }
        
        return builder.build();
    }
    
    /**
     * 从metadata JSON字符串解析RAG文档信息
     */
    private List<ChatResponse.RagDocumentInfo> parseRagDocsFromMetadata(String metadata) {
        try {
            if (!metadata.contains("\"ragDocs\"")) {
                return null;
            }
            
            List<ChatResponse.RagDocumentInfo> ragDocs = new ArrayList<>();
            
            // 简单的JSON解析（避免引入Jackson依赖）
            int ragDocsStart = metadata.indexOf("\"ragDocs\":[");
            if (ragDocsStart == -1) return null;
            
            int arrayStart = metadata.indexOf('[', ragDocsStart);
            int arrayEnd = metadata.indexOf(']', arrayStart);
            if (arrayStart == -1 || arrayEnd == -1) return null;
            
            String docsArray = metadata.substring(arrayStart + 1, arrayEnd);
            
            // 分割各个文档对象
            int depth = 0;
            int start = 0;
            for (int i = 0; i < docsArray.length(); i++) {
                char c = docsArray.charAt(i);
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        String docJson = docsArray.substring(start, i + 1);
                        ChatResponse.RagDocumentInfo doc = parseRagDocInfo(docJson);
                        if (doc != null) {
                            ragDocs.add(doc);
                        }
                        start = i + 2; // 跳过逗号
                    }
                }
            }
            
            return ragDocs;
        } catch (Exception e) {
            log.warn("Failed to parse ragDocs from metadata: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * 解析单个RAG文档JSON对象
     */
    private ChatResponse.RagDocumentInfo parseRagDocInfo(String json) {
        try {
            Long docId = extractLongValue(json, "documentId");
            String content = extractStringValue(json, "content");
            String kbTitle = extractStringValue(json, "knowledgeBaseTitle");
            
            return ChatResponse.RagDocumentInfo.builder()
                    .documentId(docId)
                    .content(content)
                    .knowledgeBaseTitle(kbTitle)
                    .build();
        } catch (Exception e) {
            log.warn("Failed to parse rag doc info: {}", e.getMessage());
            return null;
        }
    }
    
    private Long extractLongValue(String json, String key) {
        String pattern = "\"" + key + "\":";
        int start = json.indexOf(pattern);
        if (start == -1) return null;
        start += pattern.length();
        int end = json.indexOf(',', start);
        if (end == -1) end = json.indexOf('}', start);
        String value = json.substring(start, end).trim();
        return Long.parseLong(value);
    }
    
    private String extractStringValue(String json, String key) {
        String pattern = "\"" + key + "\":\"";
        int start = json.indexOf(pattern);
        if (start == -1) return null;
        start += pattern.length();
        int end = start;
        while (end < json.length()) {
            if (json.charAt(end) == '"' && (end == 0 || json.charAt(end - 1) != '\\')) {
                break;
            }
            end++;
        }
        String value = json.substring(start, end);
        // 反转义
        return value.replace("\\\"", "\"").replace("\\n", "\n");
    }
}

