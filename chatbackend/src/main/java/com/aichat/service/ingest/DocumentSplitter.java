package com.aichat.service.ingest;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文档切分服务
 * 支持多种切分策略：重叠token、按段落、按句子
 */
@Service
@Slf4j
public class DocumentSplitter {
    
    // 默认配置
    private static final int DEFAULT_CHUNK_SIZE = 500;
    private static final int DEFAULT_OVERLAP_SIZE = 50;
    
    /**
     * 切分策略
     */
    public enum SplitStrategy {
        /**
         * 按固定token大小切分，支持重叠
         */
        TOKEN_OVERLAP,
        /**
         * 按段落切分（以双换行符或多个换行符为分隔）
         */
        PARAGRAPH,
        /**
         * 按句子切分（以句号、问号、感叹号为分隔）
         */
        SENTENCE,
        PARAGRAPH_TOKEN_OVERLAP
    }
    
    /**
     * 使用默认配置切分文档
     */
    public List<String> split(String text, SplitStrategy strategy) {
        return split(text, strategy, DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP_SIZE);
    }
    
    /**
     * 切分文档
     * 
     * @param text 原始文本
     * @param strategy 切分策略
     * @param chunkSize 每个块的目标大小（字符数）
     * @param overlapSize 重叠大小（仅对TOKEN_OVERLAP策略有效）
     * @return 切分后的文本块列表
     */
    public List<String> split(String text, SplitStrategy strategy, int chunkSize, int overlapSize) {
        if (text == null || text.trim().isEmpty()) {
            return List.of();
        }
        
        text = text.trim();
        
        switch (strategy) {
            case TOKEN_OVERLAP:
                return splitByTokenWithOverlap(text, chunkSize, overlapSize);
            case PARAGRAPH:
                return splitByParagraph(text, chunkSize);
            case SENTENCE:
                return splitBySentence(text, chunkSize);
            case PARAGRAPH_TOKEN_OVERLAP:
                return splitByParagraphWithTokenOverlap(text, chunkSize, overlapSize);
            default:
                throw new IllegalArgumentException("Unknown split strategy: " + strategy);
        }
    }
    
    /**
     * 按固定token大小切分，支持重叠
     */
    private List<String> splitByTokenWithOverlap(String text, int chunkSize, int overlapSize) {
        List<String> chunks = new ArrayList<>();
        
        if (text.length() <= chunkSize) {
            chunks.add(text);
            return chunks;
        }
        
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            
            // 尝试在单词边界处切分（避免切断单词）
            if (end < text.length()) {
                int lastSpace = text.lastIndexOf(' ', end);
                if (lastSpace > start && lastSpace - start > chunkSize / 2) {
                    end = lastSpace;
                }
            }
            
            String chunk = text.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }
            
            // 计算下一个起始位置（考虑重叠）
            start = end - overlapSize;
            if (start < 0) start = 0;
            
            // 避免无限循环
            if (start + chunkSize >= text.length() && end >= text.length()) {
                break;
            }
            if (end - start < overlapSize && end >= text.length()) {
                break;
            }
        }
        
        log.debug("Token overlap split: original length={}, chunks={}, chunkSize={}, overlapSize={}", 
                 text.length(), chunks.size(), chunkSize, overlapSize);
        
        return chunks;
    }
    
    /**
     * 按段落切分
     */
    private List<String> splitByParagraph(String text, int maxChunkSize) {
        List<String> chunks = new ArrayList<>();
        
        // 以多个换行符为段落分隔符，仅按段落边界切分
        String[] paragraphs = text.split("\\r?\\n");
        for (String paragraph : paragraphs) {
            String p = paragraph.trim();
            if (!p.isEmpty()) {
                chunks.add(p);
            }
        }
        
        log.debug("Paragraph split: original length={}, paragraphs={}, chunks={}", 
                 text.length(), paragraphs.length, chunks.size());
        
        return chunks;
    }
    
    /**
     * 按句子切分
     */
    private List<String> splitBySentence(String text, int maxChunkSize) {
        // 匹配中英文句子结束符，仅按句子边界切分
        Pattern sentencePattern = Pattern.compile("[^.!?。！？]+[.!?。！？]+");
        Matcher matcher = sentencePattern.matcher(text);
        
        List<String> sentences = new ArrayList<>();
        while (matcher.find()) {
            String s = matcher.group().trim();
            if (!s.isEmpty()) {
                sentences.add(s);
            }
        }
        
        // 如果没有匹配到句子，按段落切分
        if (sentences.isEmpty()) {
            return splitByParagraph(text, maxChunkSize);
        }
        
        log.debug("Sentence split: original length={}, sentences={}, chunks={}", 
                 text.length(), sentences.size(), sentences.size());
        
        return sentences;
    }

    private List<String> splitByParagraphWithTokenOverlap(String text, int chunkSize, int overlapSize) {
        String[] paragraphs = text.split("\\r?\\n");
        List<String> result = new ArrayList<>();
        String prevTail = null;
        for (String paragraph : paragraphs) {
            String p = paragraph.trim();
            if (p.isEmpty()) {
                continue;
            }
            List<String> chunks = splitByTokenWithOverlap(p, chunkSize, overlapSize);
            if (prevTail != null && !chunks.isEmpty()) {
                String merged = (prevTail + " " + chunks.get(0)).trim();
                String bridge = merged.length() > chunkSize ? merged.substring(0, chunkSize) : merged;
                result.add(bridge);
            }
            result.addAll(chunks);
            prevTail = p.length() > overlapSize ? p.substring(p.length() - overlapSize) : p;
        }
        log.debug("Paragraph+Token overlap split: original length={}, chunks={}", text.length(), result.size());
        return result;
    }
}

