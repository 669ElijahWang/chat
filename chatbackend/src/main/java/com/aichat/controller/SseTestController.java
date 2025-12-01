package com.aichat.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.servlet.http.HttpServletResponse;

@RestController
@Profile({"local", "test"})
@RequestMapping("/test")
@Slf4j
public class SseTestController {

    /**
     * 简单的 SSE 测试端点，用于验证代理/浏览器是否存在缓冲。
     * 每隔 intervalMs 发送一个顺序片段，总计 count 次，最后发送 [DONE]。
     */
    @GetMapping(value = "/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter testSse(
            @RequestParam(name = "count", defaultValue = "10") int count,
            @RequestParam(name = "intervalMs", defaultValue = "250") long intervalMs,
            HttpServletResponse response) {

        // 明确设置 SSE 相关响应头，帮助代理识别并避免缓冲
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Connection", "keep-alive");

        SseEmitter emitter = new SseEmitter(0L);

        new Thread(() -> {
            try {
                for (int i = 1; i <= count; i++) {
                    String chunk = "chunk-" + i;
                    emitter.send(SseEmitter.event().data(chunk));
                    log.info("[SSE TEST] sent: {}", chunk);
                    Thread.sleep(intervalMs);
                }
                emitter.send(SseEmitter.event().data("[DONE]"));
                emitter.complete();
                log.info("[SSE TEST] completed");
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event().data("[ERROR] " + e.getMessage()));
                } catch (Exception ignored) {}
                emitter.completeWithError(e);
                log.error("[SSE TEST] error", e);
            }
        }, "sse-test-thread").start();

        return emitter;
    }
}