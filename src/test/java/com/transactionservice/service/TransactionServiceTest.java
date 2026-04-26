package com.transactionservice.service;

import com.transactionservice.domain.TransactionType;
import com.transactionservice.model.session.SessionDTO;
import com.transactionservice.model.request.TransactionRequest;
import com.transactionservice.model.response.TransactionResponse;
import com.transactionservice.exception.BusinessException;
import com.transactionservice.exception.LoginServiceUnavailableException;
import com.transactionservice.exception.UnauthorizedException;
import com.transactionservice.domains.LoginClientDomain;
import com.transactionservice.infrastructure.security.JwtDetails;
import com.transactionservice.domains.SqsProducerDomain;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionService Tests")
class TransactionServiceTest {

    @Mock
    private LoginClientDomain loginClient;

    @Mock
    private SqsProducerDomain sqsProducer;

    private TransactionValidator validator;
    private TransactionService transactionService;

    @BeforeEach
    void setUp() {
        validator = new TransactionValidator();
        transactionService = new TransactionServiceImpl(loginClient, sqsProducer, validator, new SimpleMeterRegistry());
        SecurityContextHolder.clearContext();
    }

    private void setAuthentication(String customerId, String channel, String rawToken) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                customerId, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        auth.setDetails(new JwtDetails(channel, rawToken));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("Should process a valid PIX transaction successfully")
    void shouldProcessValidPixTransaction() {
        setAuthentication("customer-1", "MOBILE", "valid-token");

        SessionDTO session = new SessionDTO("session-1", "customer-1", true, "sym-key", "USER");
        when(loginClient.getSession("valid-token")).thenReturn(session);
        doNothing().when(sqsProducer).publish(any());

        TransactionRequest request = new TransactionRequest(TransactionType.PIX, new BigDecimal("500.00"));
        TransactionResponse response = transactionService.processTransaction(request);

        assertThat(response).isNotNull();
        assertThat(response.customerId()).isEqualTo("customer-1");
        assertThat(response.type()).isEqualTo(TransactionType.PIX);
        assertThat(response.amount()).isEqualByComparingTo(new BigDecimal("500.00"));
        assertThat(response.status()).isEqualTo("ACCEPTED");
        assertThat(response.transactionId()).isNotBlank();

        verify(loginClient).getSession("valid-token");
        verify(sqsProducer).publish(any());
    }

    @Test
    @DisplayName("Should throw BusinessException when contractService is false")
    void shouldThrowBusinessExceptionWhenContractServiceIsFalse() {
        setAuthentication("customer-2", "MOBILE", "valid-token");

        SessionDTO session = new SessionDTO("session-2", "customer-2", false, "sym-key", "USER");
        when(loginClient.getSession("valid-token")).thenReturn(session);

        TransactionRequest request = new TransactionRequest(TransactionType.PIX, new BigDecimal("100.00"));

        assertThatThrownBy(() -> transactionService.processTransaction(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("não possui serviço contratado");

        verify(sqsProducer, never()).publish(any());
    }

    @Test
    @DisplayName("Should throw BusinessException when channel is not MOBILE")
    void shouldThrowBusinessExceptionWhenChannelIsNotMobile() {
        setAuthentication("customer-3", "WEB", "valid-token");

        TransactionRequest request = new TransactionRequest(TransactionType.PIX, new BigDecimal("100.00"));

        assertThatThrownBy(() -> transactionService.processTransaction(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Channel");

        verify(loginClient, never()).getSession(anyString());
        verify(sqsProducer, never()).publish(any());
    }

    @Test
    @DisplayName("Should throw LoginServiceUnavailableException when LoginService is down")
    void shouldThrowLoginServiceUnavailableExceptionWhenServiceIsDown() {
        setAuthentication("customer-4", "MOBILE", "valid-token");

        when(loginClient.getSession("valid-token"))
                .thenThrow(new LoginServiceUnavailableException("LoginService is unavailable"));

        TransactionRequest request = new TransactionRequest(TransactionType.PIX, new BigDecimal("100.00"));

        assertThatThrownBy(() -> transactionService.processTransaction(request))
                .isInstanceOf(LoginServiceUnavailableException.class);

        verify(sqsProducer, never()).publish(any());
    }

    @Test
    @DisplayName("Should throw UnauthorizedException when no authentication in SecurityContext")
    void shouldThrowUnauthorizedExceptionWhenNoAuthentication() {
        TransactionRequest request = new TransactionRequest(TransactionType.PIX, new BigDecimal("100.00"));

        assertThatThrownBy(() -> transactionService.processTransaction(request))
                .isInstanceOf(UnauthorizedException.class);

        verify(loginClient, never()).getSession(anyString());
        verify(sqsProducer, never()).publish(any());
    }

    @Test
    @DisplayName("Should throw BusinessException for invalid amount")
    void shouldThrowBusinessExceptionForInvalidAmount() {
        setAuthentication("customer-5", "MOBILE", "valid-token");

        TransactionRequest request = new TransactionRequest(TransactionType.PAGAMENTO, BigDecimal.ZERO);

        assertThatThrownBy(() -> transactionService.processTransaction(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("greater than zero");

        verify(loginClient, never()).getSession(anyString());
        verify(sqsProducer, never()).publish(any());
    }

    @Test
    @DisplayName("Should process INVESTIMENTO transaction successfully")
    void shouldProcessInvestimentoTransaction() {
        setAuthentication("customer-6", "MOBILE", "valid-token");

        SessionDTO session = new SessionDTO("session-6", "customer-6", true, "sym-key", "USER");
        when(loginClient.getSession("valid-token")).thenReturn(session);
        doNothing().when(sqsProducer).publish(any());

        TransactionRequest request = new TransactionRequest(TransactionType.INVESTIMENTO, new BigDecimal("10000.00"));
        TransactionResponse response = transactionService.processTransaction(request);

        assertThat(response.type()).isEqualTo(TransactionType.INVESTIMENTO);
        assertThat(response.status()).isEqualTo("ACCEPTED");
    }
}
