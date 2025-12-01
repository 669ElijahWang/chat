package com.aichat.service.embedding;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 简单的文本嵌入服务
 * 使用TF-IDF和词频向量作为降级方案
 */
@Service
@Slf4j
public class SimpleEmbeddingService {
    
    private static final int EMBEDDING_DIM = 1536; // 保持与DeepSeek相同的维度
    
    /**
     * 生成文本的嵌入向量
     * 使用简单的词频向量 + 哈希映射到固定维度
     */
    public List<Double> generateEmbedding(String text) {
        if (text == null || text.trim().isEmpty()) {
            return getZeroVector();
        }
        
        text = text.toLowerCase();
        
        // 计算词频
        Map<String, Integer> wordFreq = new HashMap<>();
        String[] words = text.split("\\s+");
        
        for (String word : words) {
            if (word.length() > 0) {
                wordFreq.put(word, wordFreq.getOrDefault(word, 0) + 1);
            }
        }
        
        // 生成固定维度的向量
        List<Double> embedding = new ArrayList<>(EMBEDDING_DIM);
        for (int i = 0; i < EMBEDDING_DIM; i++) {
            embedding.add(0.0);
        }
        
        // 将词频映射到向量的不同位置
        for (Map.Entry<String, Integer> entry : wordFreq.entrySet()) {
            String word = entry.getKey();
            int freq = entry.getValue();
            
            // 使用哈希将单词映射到多个维度
            int[] indices = hashToIndices(word, 3);
            for (int idx : indices) {
                double currentValue = embedding.get(idx);
                embedding.set(idx, currentValue + freq);
            }
        }
        
        // 归一化向量
        return normalizeVector(embedding);
    }
    
    /**
     * 批量生成嵌入向量
     */
    public List<List<Double>> generateEmbeddings(List<String> texts) {
        List<List<Double>> embeddings = new ArrayList<>();
        for (String text : texts) {
            embeddings.add(generateEmbedding(text));
        }
        return embeddings;
    }
    
    /**
     * 将字符串哈希到多个索引位置
     */
    private int[] hashToIndices(String word, int count) {
        int[] indices = new int[count];
        int baseHash = word.hashCode();
        
        for (int i = 0; i < count; i++) {
            int hash = baseHash + i * 31;
            indices[i] = Math.abs(hash % EMBEDDING_DIM);
        }
        
        return indices;
    }
    
    /**
     * 向量归一化（L2范数）
     */
    private List<Double> normalizeVector(List<Double> vector) {
        double sumSquares = 0.0;
        for (Double val : vector) {
            sumSquares += val * val;
        }
        
        if (sumSquares == 0.0) {
            return vector;
        }
        
        double norm = Math.sqrt(sumSquares);
        List<Double> normalized = new ArrayList<>();
        for (Double val : vector) {
            normalized.add(val / norm);
        }
        
        return normalized;
    }
    
    /**
     * 获取零向量
     */
    private List<Double> getZeroVector() {
        List<Double> zero = new ArrayList<>(EMBEDDING_DIM);
        for (int i = 0; i < EMBEDDING_DIM; i++) {
            zero.add(0.0);
        }
        return zero;
    }
}

