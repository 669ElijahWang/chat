package com.aichat.service.ingest;

import com.aichat.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * URL内容摄取服务
 * 从网页URL中提取文本内容
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UrlIngestionService {
    
    private static final int TIMEOUT_MS = 30000; // 30秒超时
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";
    
    /**
     * 从URL提取文本内容
     */
    public String extractTextFromUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            throw new BusinessException("URL不能为空");
        }
        
        // 验证URL格式
        if (!isValidUrl(url)) {
            throw new BusinessException("无效的URL格式");
        }
        
        log.info("Fetching content from URL: {}", url);
        
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .get();
            
            String text = extractMainContent(doc);
            log.info("Extracted text from URL: {}, textLength={}", url, text.length());
            
            if (text.trim().isEmpty()) {
                throw new BusinessException("未能从URL提取到有效内容");
            }
            
            return text;
        } catch (IOException e) {
            log.error("Failed to fetch URL: {}", url, e);
            throw new BusinessException("无法访问URL: " + e.getMessage());
        }
    }
    
    /**
     * 从HTML文档中提取主要内容
     */
    private String extractMainContent(Document doc) {
        StringBuilder content = new StringBuilder();
        
        // 移除脚本和样式
        doc.select("script, style, nav, header, footer, aside, .advertisement, .ad, .sidebar").remove();
        
        // 获取标题
        String title = doc.title();
        if (title != null && !title.isEmpty()) {
            content.append(title).append("\n\n");
        }
        
        // 尝试提取主要内容区域
        Element mainContent = findMainContent(doc);
        
        if (mainContent != null) {
            extractTextFromElement(mainContent, content);
        } else {
            // 如果没有找到主要内容区域，提取body中的所有文本
            extractTextFromElement(doc.body(), content);
        }
        
        return cleanText(content.toString());
    }
    
    /**
     * 查找主要内容区域
     */
    private Element findMainContent(Document doc) {
        // 尝试常见的主要内容选择器
        String[] selectors = {
            "main",
            "article",
            "[role=main]",
            ".main-content",
            ".content",
            ".post-content",
            ".article-content",
            "#content",
            "#main"
        };
        
        for (String selector : selectors) {
            Element element = doc.selectFirst(selector);
            if (element != null) {
                log.debug("Found main content with selector: {}", selector);
                return element;
            }
        }
        
        return null;
    }
    
    /**
     * 从元素中提取文本
     */
    private void extractTextFromElement(Element element, StringBuilder content) {
        // 提取段落
        Elements paragraphs = element.select("p");
        for (Element p : paragraphs) {
            String text = p.text().trim();
            if (!text.isEmpty() && text.length() > 20) { // 过滤太短的段落
                content.append(text).append("\n\n");
            }
        }
        
        // 提取列表
        Elements lists = element.select("li");
        for (Element li : lists) {
            String text = li.text().trim();
            if (!text.isEmpty()) {
                content.append("• ").append(text).append("\n");
            }
        }
        
        // 提取标题
        Elements headings = element.select("h1, h2, h3, h4, h5, h6");
        for (Element heading : headings) {
            String text = heading.text().trim();
            if (!text.isEmpty()) {
                content.append("\n").append(text).append("\n");
            }
        }
        
        // 如果以上都没有提取到内容，直接获取文本
        if (content.length() == 0) {
            content.append(element.text());
        }
    }
    
    /**
     * 清理文本
     */
    private String cleanText(String text) {
        if (text == null) {
            return "";
        }
        
        // 移除多余的空行
        text = text.replaceAll("\\n{3,}", "\n\n");
        
        // 移除行首行尾空格
        String[] lines = text.split("\n");
        StringBuilder cleaned = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                cleaned.append(trimmed).append("\n");
            }
        }
        
        return cleaned.toString().trim();
    }
    
    /**
     * 验证URL格式
     */
    private boolean isValidUrl(String url) {
        url = url.trim().toLowerCase();
        return url.startsWith("http://") || url.startsWith("https://");
    }
}

