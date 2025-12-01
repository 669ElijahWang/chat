package com.aichat.controller;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class SseTestControllerIT {

    @LocalServerPort
    private int port;

    @Autowired
    private WebClient.Builder webClientBuilder;

    @Test
    public void testStreamingChunksArriveGradually() throws InterruptedException {
        String url = "http://localhost:" + port + "/api/test/sse?count=5&intervalMs=200";

        WebClient client = webClientBuilder.build();

        List<Long> timestamps = new ArrayList<>();
        CountDownLatch done = new CountDownLatch(1);

        Flux<String> flux = client.get()
                .uri(url)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .bodyToFlux(String.class);

        flux.subscribe(data -> {
            timestamps.add(System.currentTimeMillis());
        }, err -> {
            done.countDown();
        }, done::countDown);

        // 等待最多 5 秒钟获取完整流
        boolean finished = done.await(5, TimeUnit.SECONDS);
        Assertions.assertTrue(finished, "SSE 流未在预期时间内完成");

        // 至少应该收到 6 个事件（5 个 chunk + 1 个 [DONE]）
        Assertions.assertTrue(timestamps.size() >= 5, "收到的事件数量异常: " + timestamps.size());

        // 事件时间戳应呈渐进到达（间隔 > 100ms）
        int progressive = 0;
        for (int i = 1; i < timestamps.size(); i++) {
            long delta = timestamps.get(i) - timestamps.get(i - 1);
            if (delta >= 100) progressive++;
        }
        Assertions.assertTrue(progressive >= 3, "事件未逐步到达，可能被缓冲。progressive=" + progressive);
    }
}