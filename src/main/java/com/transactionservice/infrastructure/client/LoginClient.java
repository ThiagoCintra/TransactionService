package com.transactionservice.infrastructure.client;

import com.transactionservice.model.session.SessionDTO;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.core.ParameterizedTypeReference;

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
        try {
            if (token != null) {
                String prefix = token.length() > 8 ? token.substring(0, 8) : token;
                String suffix = token.length() > 8 ? token.substring(token.length() - 8) : token;
                log.info("LoginClient: token length={} prefix={} suffix={}", token.length(), prefix, suffix);
            } else {
                log.info("LoginClient: token is null");
            }
        } catch (Exception e) {
            log.warn("Failed to log token details: {}", e.getMessage());
        }

        // Normalize token/header: callers may pass either the raw token or the full "Bearer <token>" header.
        String tokenRaw = token == null ? "" : token.trim();
        String authHeader = tokenRaw.toLowerCase().startsWith("bearer ") ? tokenRaw : "Bearer " + tokenRaw;

        // Read generic JSON and adapt to internal SessionDTO so we tolerate different shapes
        Map<String, Object> json = webClient.get()
                .uri("/api/v1/auth/me")
                .header("Authorization", authHeader)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response -> {
                    log.warn("LoginService returned 4xx status: {}", response.statusCode());
                    return response.createException();
                })
                .onStatus(HttpStatusCode::is5xxServerError, response -> {
                    log.error("LoginService returned 5xx status: {}", response.statusCode());
                    return response.createException();
                })
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .timeout(Duration.ofMillis(timeoutMillis))
                .block();

        if (json == null) return null;

        // id or userId
        String userId = null;
        if (json.get("userId") != null) userId = String.valueOf(json.get("userId"));
        else if (json.get("id") != null) userId = String.valueOf(json.get("id"));
        else if (json.get("sub") != null) userId = String.valueOf(json.get("sub"));

        String username = json.get("username") != null ? String.valueOf(json.get("username")) : null;

        List<String> roles = new ArrayList<>();
        Object roleObj = json.get("role");
        if (roleObj instanceof String) roles.add((String) roleObj);
        Object rolesObj = json.get("roles");
        if (rolesObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> raw = (List<Object>) rolesObj;
            roles.addAll(raw.stream().map(String::valueOf).collect(Collectors.toList()));
        }

        Long escolaId = null;
        if (json.get("escolaId") != null) {
            try { escolaId = Long.valueOf(String.valueOf(json.get("escolaId"))); } catch (Exception ignore) {}
        }

        List<Long> alunosIds = new ArrayList<>();
        Object alunosObj = json.get("alunosIds");
        if (alunosObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> raw = (List<Object>) alunosObj;
            alunosIds.addAll(raw.stream().map(o -> {
                try { return Long.valueOf(String.valueOf(o)); } catch (Exception e) { return null; }
            }).filter(x -> x != null).collect(Collectors.toList()));
        }

        // If user is RESPONSAVEL but alunosIds are not present, try to enrich calling alunos-service
        if (roles.stream().anyMatch(r -> "RESPONSAVEL".equalsIgnoreCase(r)) && alunosIds.isEmpty()) {
            WebClient alunosClient = WebClient.builder().baseUrl("http://alunos-service:8080").build();
            try {
                log.info("Attempting to enrich alunosIds from alunos-service using Authorization header=" + (authHeader.length()>20? authHeader.substring(0,20)+"...": authHeader));
                // reuse the normalized Authorization header so downstream services receive a valid header
                List<Map<String, Object>> alunos = alunosClient.get()
                        .uri(uri -> uri.path("/api/v1/alunos").queryParam("page", 0).queryParam("size", 200).build())
                        .header("Authorization", authHeader)
                        .retrieve()
                        .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
                        .timeout(Duration.ofMillis(timeoutMillis))
                        .block();

                if (alunos != null) {
                    log.info("alunos-service returned {} entries for user enrichment", alunos.size());
                    alunosIds.addAll(alunos.stream()
                            .map(m -> m.get("id"))
                            .filter(id -> id != null)
                            .map(Object::toString)
                            .map(Long::valueOf)
                            .collect(Collectors.toList()));
                    log.info("Enriched alunosIds: {}", alunosIds);
                } else {
                    log.info("alunos-service returned null when enriching alunosIds");
                }
            } catch (WebClientResponseException wcre) {
                log.warn("Could not enrich alunosIds from alunos-service: status={} body={}", wcre.getRawStatusCode(), wcre.getResponseBodyAsString());
            } catch (Exception e) {
                log.warn("Could not enrich alunosIds from alunos-service: {}", e.getMessage());
            }
        }

        return new SessionDTO(userId, username, roles, escolaId, alunosIds);
    }

    // Placeholder token getter when original response doesn't include it
    private String tokenPlaceholder() { return ""; }

    public SessionDTO getSessionFallback(String token, Throwable throwable) {
        log.error("LoginService fallback triggered. Cause: {}", throwable.getMessage());

        if (failFast) {
            log.warn("Fail-fast mode: blocking transaction due to LoginService unavailability");
            throw new LoginServiceUnavailableException(
                    "LoginService is currently unavailable. Transaction blocked for safety.", throwable);
        }

        return null;
    }
}
