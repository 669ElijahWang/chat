package com.aichat.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.ProxyProvider;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class WebClientConfig {

    @Value("${deepseek.api.timeout:60000}")
    private long timeout;

    @Value("${http.proxy.enabled:false}")
    private boolean proxyEnabled;

    @Value("${http.proxy.host:}")
    private String proxyHost;

    @Value("${http.proxy.port:0}")
    private int proxyPort;

    @Bean
    public ReactorClientHttpConnector reactorClientHttpConnector() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) timeout)
                .responseTimeout(Duration.ofMillis(timeout))
                .doOnConnected(conn ->
                        conn.addHandlerLast(new ReadTimeoutHandler(timeout, TimeUnit.MILLISECONDS))
                                .addHandlerLast(new WriteTimeoutHandler(timeout, TimeUnit.MILLISECONDS))
                );

        if (proxyEnabled && proxyHost != null && !proxyHost.isBlank() && proxyPort > 0) {
            httpClient = httpClient.proxy(type -> type
                    .type(ProxyProvider.Proxy.HTTP)
                    .host(proxyHost)
                    .port(proxyPort));
        }

        return new ReactorClientHttpConnector(httpClient);
    }

    @Bean
    public WebClient.Builder webClientBuilder(ReactorClientHttpConnector connector) {
        return WebClient.builder().clientConnector(connector);
    }
}

