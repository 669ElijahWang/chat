package com.aichat.service.deepseek.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class EmbeddingResponse {
    
    private String object;
    
    private List<EmbeddingData> data;
    
    private String model;
    
    private Usage usage;
    
    @Data
    public static class EmbeddingData {
        private String object;
        private List<Double> embedding;
        private Integer index;
    }
    
    @Data
    public static class Usage {
        @JsonProperty("prompt_tokens")
        private Integer promptTokens;
        
        @JsonProperty("total_tokens")
        private Integer totalTokens;
    }
}

