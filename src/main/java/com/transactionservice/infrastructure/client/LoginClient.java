package com.transactionservice.infrastructure.client;

import com.transactionservice.dto.SessionDTO;
import com.transactionservice.exception.LoginServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoginClient {

    @Qualifier("loginServiceWebClient")
    private final WebClient webClient;

    @Value("${login-service.timeout-millis:2000}")
    private long timeoutMillis;

    @Value("${feature-flags.login-service-fail-fast:true}")
    private boolean failFast;

    @CircuitBreaker(name = "loginService", fallbackMethod = "getSessionFallback")
    @Retry(name = "loginService")
    public SessionDTO getSession(String token) {
        log.info("Calling LoginService /me endpoint");

        SessionDTO session = webClient.get()
                .uri("/me")
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response -> {
                    log.warn("LoginService returned 4xx status: {}", response.statusCode());
                    return response.createException();
                })
                .onStatus(HttpStatusCode::is5xxServerError, response -> {
                    log.error("LoginService returned 5xx status: {}", response.statusCode());
                    return response.createException();
                })
                .bodyToMono(SessionDTO.class)
                .timeout(Duration.ofMillis(timeoutMillis))
                .block();

        log.info("LoginService response received for user: {}", session != null ? session.username() : "null");
        return session;
    }

    public SessionDTO getSessionFallback(String token, Throwable throwable) {
        log.error("LoginService fallback triggered. Cause: {}", throwable.getMessage());

        if (failFast) {
            log.warn("Fail-fast mode: blocking transaction due to LoginService unavailability");
            throw new LoginServiceUnavailableException(
                    "LoginService is currently unavailable. Transaction blocked for safety.", throwable);
        }

        log.warn("[RISK] Fail-open mode: allowing transaction without LoginService validation");
        return null;
    }
}
