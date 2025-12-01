package com.aichat.service.ingest;

import com.aichat.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * 文件摄取服务
 * 支持多种文件格式的解析
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FileIngestionService {
    
    private final Tika tika = new Tika();
    
    // 支持的文件类型
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    
    // 支持的代码文件扩展名
    private static final List<String> CODE_EXTENSIONS = Arrays.asList(
        "java", "py", "js", "ts", "jsx", "tsx", "cpp", "c", "h", "hpp",
        "cs", "go", "rs", "rb", "php", "swift", "kt", "scala", "r",
        "sql", "sh", "bash", "ps1", "bat", "cmd", "xml", "json", "yaml", "yml",
        "css", "scss", "sass", "less", "vue", "svelte", "dart", "lua", "pl", "m"
    );
    
    // 支持的图片扩展名
    private static final List<String> IMAGE_EXTENSIONS = Arrays.asList(
        "jpg", "jpeg", "png", "gif", "bmp", "webp", "svg"
    );
    
    /**
     * 从上传的文件中提取文本内容
     */
    public String extractText(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("文件不能为空");
        }
        
        // 检查文件大小
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException("文件大小不能超过10MB");
        }
        
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            throw new BusinessException("无效的文件名");
        }
        
        String extension = getFileExtension(originalFilename);
        log.info("Processing file: name={}, size={}, type={}", originalFilename, file.getSize(), extension);
        
        try {
            String text = extractTextByExtension(file, extension);
            log.info("Extracted text from file: name={}, textLength={}", originalFilename, text.length());
            return text;
        } catch (IOException e) {
            log.error("Failed to extract text from file: {}", originalFilename, e);
            throw new BusinessException("文件解析失败: " + e.getMessage());
        }
    }
    
    /**
     * 根据文件扩展名选择合适的解析方法
     */
    private String extractTextByExtension(MultipartFile file, String extension) throws IOException {
        String ext = extension.toLowerCase();
        
        // 图片文件
        if (IMAGE_EXTENSIONS.contains(ext)) {
            return extractImageInfo(file, ext);
        }
        
        // 代码文件
        if (CODE_EXTENSIONS.contains(ext)) {
            return extractCodeFile(file, ext);
        }
        
        // 其他文档文件
        return switch (ext) {
            case "txt", "md", "log", "markdown", "text" -> extractTextFromPlainText(file);
            case "pdf" -> extractTextFromPdf(file);
            case "doc" -> extractTextFromDoc(file);
            case "docx" -> extractTextFromDocx(file);
            case "html", "htm" -> extractTextWithTika(file);
            default -> extractTextWithTika(file); // 使用Tika作为后备方案
        };
    }
    
    /**
     * 提取纯文本文件内容
     */
    private String extractTextFromPlainText(MultipartFile file) throws IOException {
        try (InputStream is = file.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            return content.toString().trim();
        }
    }
    
    /**
     * 提取PDF文件内容
     */
    private String extractTextFromPdf(MultipartFile file) throws IOException {
        try (InputStream is = file.getInputStream();
             PDDocument document = PDDocument.load(is)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            return text.trim();
        }
    }
    
    /**
     * 提取旧版Word文档内容 (.doc)
     */
    private String extractTextFromDoc(MultipartFile file) throws IOException {
        try (InputStream is = file.getInputStream();
             HWPFDocument document = new HWPFDocument(is)) {
            WordExtractor extractor = new WordExtractor(document);
            String text = extractor.getText();
            extractor.close();
            return text.trim();
        }
    }
    
    /**
     * 提取新版Word文档内容 (.docx)
     */
    private String extractTextFromDocx(MultipartFile file) throws IOException {
        try (InputStream is = file.getInputStream();
             XWPFDocument document = new XWPFDocument(is)) {
            XWPFWordExtractor extractor = new XWPFWordExtractor(document);
            String text = extractor.getText();
            extractor.close();
            return text.trim();
        }
    }
    
    /**
     * 使用Apache Tika提取文本（通用方法）
     */
    private String extractTextWithTika(MultipartFile file) throws IOException {
        try (InputStream is = file.getInputStream()) {
            String text = tika.parseToString(is);
            return text.trim();
        } catch (Exception e) {
            throw new IOException("Tika解析失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 提取代码文件内容
     */
    private String extractCodeFile(MultipartFile file, String extension) throws IOException {
        try (InputStream is = file.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder content = new StringBuilder();
            content.append("【代码文件类型: .").append(extension).append("】\n\n");
            content.append("```").append(extension).append("\n");
            
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            
            content.append("```");
            return content.toString();
        }
    }
    
    /**
     * 提取图片信息
     * 注意：这里返回图片的基本信息，不进行OCR识别
     * 如需OCR功能，需要集成Tesseract或调用第三方OCR服务
     */
    private String extractImageInfo(MultipartFile file, String extension) throws IOException {
        StringBuilder info = new StringBuilder();
        info.append("【这是一张图片文件，AI模型当前无法直接查看图片内容】\n\n");
        
        info.append("图片基本信息：\n");
        info.append("- 文件名: ").append(file.getOriginalFilename()).append("\n");
        info.append("- 格式: ").append(extension.toUpperCase()).append("\n");
        info.append("- 大小: ").append(formatFileSize(file.getSize())).append("\n");
        
        try (InputStream is = file.getInputStream()) {
            BufferedImage image = ImageIO.read(is);
            if (image != null) {
                info.append("- 尺寸: ").append(image.getWidth()).append(" x ").append(image.getHeight()).append(" 像素\n");
                info.append("- 颜色: ").append(getColorModelName(image)).append("\n");
            }
        } catch (Exception e) {
            log.warn("无法读取图片元数据: {}", e.getMessage());
        }
        
        info.append("\n重要说明：");
        info.append("\n由于当前使用的AI模型（DeepSeek）不支持图片输入，我无法直接看到或分析图片的实际内容。");
        info.append("\n\n建议的替代方案：");
        info.append("\n1. 请您用文字描述图片中的内容，我可以基于您的描述提供帮助");
        info.append("\n2. 如果图片中包含文字，您可以手动输入文字内容");
        info.append("\n3. 如果需要图片识别功能，可以使用支持视觉的AI模型（如GPT-4V）或OCR工具");
        
        return info.toString();
    }
    
    /**
     * 获取颜色模型名称
     */
    private String getColorModelName(BufferedImage image) {
        int type = image.getType();
        return switch (type) {
            case BufferedImage.TYPE_INT_RGB -> "RGB";
            case BufferedImage.TYPE_INT_ARGB -> "ARGB (带透明通道)";
            case BufferedImage.TYPE_INT_BGR -> "BGR";
            case BufferedImage.TYPE_3BYTE_BGR -> "3字节BGR";
            case BufferedImage.TYPE_4BYTE_ABGR -> "4字节ABGR";
            case BufferedImage.TYPE_BYTE_GRAY -> "灰度";
            case BufferedImage.TYPE_BYTE_BINARY -> "二值";
            default -> "其他 (类型" + type + ")";
        };
    }
    
    /**
     * 格式化文件大小
     */
    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
    }
    
    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1 || lastDotIndex == filename.length() - 1) {
            return "";
        }
        return filename.substring(lastDotIndex + 1);
    }
    
    /**
     * 检查文件类型是否支持
     */
    public boolean isSupportedFileType(String filename) {
        String extension = getFileExtension(filename).toLowerCase();
        
        // 检查是否是图片
        if (IMAGE_EXTENSIONS.contains(extension)) {
            return true;
        }
        
        // 检查是否是代码文件
        if (CODE_EXTENSIONS.contains(extension)) {
            return true;
        }
        
        // 检查是否是文档文件
        return switch (extension) {
            case "txt", "md", "log", "markdown", "text",
                 "pdf", "doc", "docx", "html", "htm" -> true;
            default -> false;
        };
    }
    
    /**
     * 获取支持的文件类型描述
     */
    public String getSupportedFileTypesDescription() {
        return "支持的文件类型：\n" +
               "- 文档：PDF, Word (doc/docx), TXT, Markdown, HTML\n" +
               "- 代码：Java, Python, JavaScript, TypeScript, C/C++, Go, Rust, 等40+种\n" +
               "- 图片：JPG, PNG, GIF, BMP, WebP, SVG (仅提供基本信息)";
    }
}

