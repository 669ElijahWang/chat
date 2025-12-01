package com.aichat.service.deepseek.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmbeddingRequest {
    
    // 使用text-embedding-ada-002或其他兼容的embedding模型
    @Builder.Default
    private String model = "text-embedding-ada-002";
    
    private Object input; // Can be String or List<String>
}

