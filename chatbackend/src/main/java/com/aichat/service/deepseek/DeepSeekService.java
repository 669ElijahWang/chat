package com.aichat.service.deepseek;

import com.aichat.service.deepseek.dto.ChatCompletionRequest;
import com.aichat.service.deepseek.dto.ChatCompletionResponse;
import com.aichat.service.deepseek.dto.EmbeddingRequest;
import com.aichat.service.deepseek.dto.EmbeddingResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;


@Service
@Slf4j
public class DeepSeekService {
    
    private final WebClient webClient;
    
    @Value("${deepseek.api.model}")
    private String defaultModel;

    @Value("${deepseek.api.max-retries:3}")
    private int maxRetries;
    
    public DeepSeekService(WebClient.Builder webClientBuilder,
                           @Value("${deepseek.api.base-url}") String baseUrl,
                           @Value("${deepseek.api.api-key}") String apiKey) {
        this.webClient = webClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        log.info("DeepSeek API 已配置: baseUrl={}", baseUrl);
    }
    
    /**
     * 同步聊天补全
     */
    public Mono<ChatCompletionResponse> chatCompletion(ChatCompletionRequest request) {
        if (request.getModel() == null) {
            request.setModel(defaultModel);
        }
        request.setStream(false);
        
        log.debug("Sending chat completion request: {}", request);
        
        return webClient.post()
                .uri("/v1/chat/completions")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(ChatCompletionResponse.class)
                .retryWhen(Retry
                        .backoff(Math.max(0, maxRetries), Duration.ofSeconds(2))
                        .filter(err -> err instanceof WebClientRequestException)
                        .onRetryExhaustedThrow((spec, signal) -> signal.failure()))
                .doOnSuccess(response -> log.debug("Received chat completion response: {}", response))
                .doOnError(error -> log.error("Chat completion error: ", error));
    }
    
    /**
     * 流式聊天补全
     */
    public Flux<ChatCompletionResponse> chatCompletionStream(ChatCompletionRequest request) {
        if (request.getModel() == null) {
            request.setModel(defaultModel);
        }
        request.setStream(true);
        
        log.debug("Sending streaming chat completion request: {}", request);
        
        return webClient.post()
                .uri("/v1/chat/completions")
                .bodyValue(request)
                .retrieve()
                .bodyToFlux(ChatCompletionResponse.class)
                .retryWhen(Retry
                        .backoff(Math.max(0, maxRetries), Duration.ofSeconds(2))
                        .filter(err -> err instanceof WebClientRequestException)
                        .onRetryExhaustedThrow((spec, signal) -> signal.failure()))
                .doOnNext(response -> log.trace("Received stream chunk: {}", response))
                .doOnError(error -> log.error("Streaming chat completion error: ", error))
                .doOnComplete(() -> log.debug("Streaming chat completion completed"));
    }
    
    /**
     * 生成文本嵌入向量
     */
    public Mono<EmbeddingResponse> createEmbedding(EmbeddingRequest request) {
        log.debug("Creating embedding for text: {}", request.getInput());
        
        return webClient.post()
                .uri("/v1/embeddings")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(EmbeddingResponse.class)
                .doOnSuccess(response -> log.debug("Embedding created successfully"))
                .doOnError(error -> log.error("Embedding creation error: ", error));
    }
    
    /**
     * 批量生成嵌入向量
     */
    public Mono<EmbeddingResponse> createEmbeddings(EmbeddingRequest request) {
        log.debug("Creating embeddings for {} texts", 
                  request.getInput() instanceof String ? 1 : 
                  ((java.util.List<?>) request.getInput()).size());
        
        return webClient.post()
                .uri("/v1/embeddings")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(EmbeddingResponse.class)
                .doOnSuccess(response -> log.debug("Embeddings created successfully"))
                .doOnError(error -> log.error("Embeddings creation error: ", error));
    }
}

