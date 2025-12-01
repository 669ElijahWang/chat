package com.aichat.controller;

import com.aichat.domain.dto.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
public class CaptchaController {

    private final RedisTemplate<String, String> redisTemplate;

    @Value("${captcha.expiration-seconds:120}")
    private long captchaExpirationSeconds;

    @GetMapping("/captcha")
    public ApiResponse<Map<String, String>> getCaptcha() {
        String code = generateCode(4);
        String captchaId = UUID.randomUUID().toString();

        // 存储到Redis，设置过期时间
        String key = "captcha:" + captchaId;
        redisTemplate.opsForValue().set(key, code, captchaExpirationSeconds, TimeUnit.SECONDS);

        String imageBase64;
        try {
            imageBase64 = generateImageBase64(code);
        } catch (IOException e) {
            log.error("生成验证码图片失败", e);
            return ApiResponse.error(500, "验证码生成失败");
        }

        Map<String, String> data = new HashMap<>();
        data.put("captchaId", captchaId);
        // 前端可以直接以 <img src="data:image/png;base64,..."/> 使用
        data.put("imageBase64", imageBase64);
        return ApiResponse.success("获取成功", data);
    }

    private String generateCode(int length) {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // 去掉易混淆字符
        Random random = new Random();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private String generateImageBase64(String code) throws IOException {
        int width = 120;
        int height = 40;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();

        // 背景
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);

        // 干扰线
        Random random = new Random();
        for (int i = 0; i < 8; i++) {
            g.setColor(new Color(random.nextInt(150), random.nextInt(150), random.nextInt(150)));
            int x1 = random.nextInt(width);
            int y1 = random.nextInt(height);
            int x2 = random.nextInt(width);
            int y2 = random.nextInt(height);
            g.drawLine(x1, y1, x2, y2);
        }

        // 文本
        g.setFont(new Font("Arial", Font.BOLD, 24));
        FontMetrics fm = g.getFontMetrics();
        int textWidth = fm.stringWidth(code);
        int x = (width - textWidth) / 2;
        int y = ((height - fm.getHeight()) / 2) + fm.getAscent();
        g.setColor(Color.BLACK);
        g.drawString(code, x, y);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        String base64 = Base64.getEncoder().encodeToString(baos.toByteArray());
        // 返回带前缀的data URL，前端可直接使用
        return "data:image/png;base64," + base64;
    }
}