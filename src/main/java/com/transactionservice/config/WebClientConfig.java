package com.transactionservice.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class WebClientConfig {

    @Value("${login-service.base-url}")
    private String loginServiceBaseUrl;

    @Value("${login-service.timeout-millis:2000}")
    private int timeoutMillis;

    @Bean("loginServiceWebClient")
    public WebClient loginServiceWebClient() {
        // Normalize configured base URL: remove trailing slashes and any embedded /api/v1
        // so that callers can safely append 
        // paths like "/api/v1/auth/me" without creating duplicated segments.
        String normalizedBase = loginServiceBaseUrl == null ? "" : loginServiceBaseUrl.trim();
        if (normalizedBase.endsWith("/")) {
            normalizedBase = normalizedBase.substring(0, normalizedBase.length() - 1);
        }
        int idx = normalizedBase.indexOf("/api/v1");
        if (idx != -1) {
            normalizedBase = normalizedBase.substring(0, idx);
        }
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, timeoutMillis)
                .responseTimeout(Duration.ofMillis(timeoutMillis))
                .doOnConnected(conn ->
                        conn.addHandlerLast(new ReadTimeoutHandler(timeoutMillis, TimeUnit.MILLISECONDS))
                            .addHandlerLast(new WriteTimeoutHandler(timeoutMillis, TimeUnit.MILLISECONDS)));

        return WebClient.builder()
                .baseUrl(normalizedBase)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
