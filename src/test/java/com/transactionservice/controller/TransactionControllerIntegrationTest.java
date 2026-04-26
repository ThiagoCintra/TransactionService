package com.transactionservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.transactionservice.JwtTestHelper;
import com.transactionservice.domain.TransactionType;
import com.transactionservice.model.request.TransactionRequest;
import com.transactionservice.infrastructure.sqs.SqsProducer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("TransactionController Integration Tests")
class TransactionControllerIntegrationTest {

    static WireMockServer wireMockServer;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    SqsProducer sqsProducer;

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());
    }

    @AfterAll
    static void stopWireMock() {
        wireMockServer.stop();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("login-service.base-url",
                () -> "http://localhost:" + wireMockServer.port());
        registry.add("aws.sqs.endpoint", () -> "");
    }

    @BeforeEach
    void setUp() {
        wireMockServer.resetAll();
        doNothing().when(sqsProducer).publish(any());
    }

    @Test
    @DisplayName("Should return 202 for valid transaction with active contract")
    void shouldReturn202ForValidTransaction() throws Exception {
        String token = JwtTestHelper.generateMobileToken("customer-123");

        wireMockServer.stubFor(get(urlEqualTo("/me"))
                .withHeader("Authorization", equalTo("Bearer " + token))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "sessionId": "session-abc",
                                    "username": "customer-123",
                                    "contractService": true,
                                    "symmetricKey": "sym-key",
                                    "role": "USER"
                                }
                                """)));

        TransactionRequest request = new TransactionRequest(TransactionType.PIX, new BigDecimal("500.00"));

        mockMvc.perform(post("/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.customerId").value("customer-123"))
                .andExpect(jsonPath("$.type").value("PIX"))
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.transactionId").isNotEmpty());

        verify(sqsProducer).publish(any());
    }

    @Test
    @DisplayName("Should return 422 when customer has no contracted service")
    void shouldReturn422WhenNoContractedService() throws Exception {
        String token = JwtTestHelper.generateMobileToken("customer-456");

        wireMockServer.stubFor(get(urlEqualTo("/me"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "sessionId": "session-def",
                                    "username": "customer-456",
                                    "contractService": false,
                                    "symmetricKey": "sym-key",
                                    "role": "USER"
                                }
                                """)));

        TransactionRequest request = new TransactionRequest(TransactionType.PIX, new BigDecimal("100.00"));

        mockMvc.perform(post("/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("Business Rule Violation"));

        verify(sqsProducer, never()).publish(any());
    }

    @Test
    @DisplayName("Should return 401 when no JWT token is provided")
    void shouldReturn401WhenNoTokenProvided() throws Exception {
        TransactionRequest request = new TransactionRequest(TransactionType.PIX, new BigDecimal("100.00"));

        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should return 401 when JWT token is expired")
    void shouldReturn401WhenTokenIsExpired() throws Exception {
        String expiredToken = JwtTestHelper.generateExpiredToken("customer-789");

        TransactionRequest request = new TransactionRequest(TransactionType.PIX, new BigDecimal("100.00"));

        mockMvc.perform(post("/transactions")
                        .header("Authorization", "Bearer " + expiredToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should return 422 when channel is not MOBILE")
    void shouldReturn422WhenChannelIsNotMobile() throws Exception {
        String token = JwtTestHelper.generateNonMobileToken("customer-111");

        TransactionRequest request = new TransactionRequest(TransactionType.PIX, new BigDecimal("100.00"));

        mockMvc.perform(post("/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("Business Rule Violation"));
    }

    @Test
    @DisplayName("Should return 400 when request body is invalid")
    void shouldReturn400WhenRequestIsInvalid() throws Exception {
        String token = JwtTestHelper.generateMobileToken("customer-222");

        mockMvc.perform(post("/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "type": "PIX",
                                    "amount": -100.00
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return 503 when LoginService is unavailable (circuit breaker open)")
    void shouldReturn503WhenLoginServiceIsUnavailable() throws Exception {
        String token = JwtTestHelper.generateMobileToken("customer-333");

        wireMockServer.stubFor(get(urlEqualTo("/me"))
                .willReturn(aResponse()
                        .withStatus(503)
                        .withFixedDelay(5000)));

        TransactionRequest request = new TransactionRequest(TransactionType.PIX, new BigDecimal("100.00"));

        mockMvc.perform(post("/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isServiceUnavailable());

        verify(sqsProducer, never()).publish(any());
    }
}
