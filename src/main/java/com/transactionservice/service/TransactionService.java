package com.transactionservice.service;

import com.transactionservice.dto.SessionDTO;
import com.transactionservice.dto.TransactionEvent;
import com.transactionservice.dto.TransactionRequest;
import com.transactionservice.dto.TransactionResponse;
import com.transactionservice.exception.BusinessException;
import com.transactionservice.exception.UnauthorizedException;
import com.transactionservice.infrastructure.client.LoginClient;
import com.transactionservice.infrastructure.security.JwtDetails;
import com.transactionservice.infrastructure.sqs.SqsProducer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
public class TransactionService {

    private static final String ALLOWED_CHANNEL = "MOBILE";

    private final LoginClient loginClient;
    private final SqsProducer sqsProducer;
    private final TransactionValidator validator;
    private final Counter transactionCounter;
    private final Counter transactionFailureCounter;
    private final Timer loginServiceTimer;

    public TransactionService(LoginClient loginClient,
                              SqsProducer sqsProducer,
                              TransactionValidator validator,
                              MeterRegistry meterRegistry) {
        this.loginClient = loginClient;
        this.sqsProducer = sqsProducer;
        this.validator = validator;
        this.transactionCounter = Counter.builder("transactions.total")
                .description("Total number of transactions processed")
                .register(meterRegistry);
        this.transactionFailureCounter = Counter.builder("transactions.failures")
                .description("Total number of failed transactions")
                .register(meterRegistry);
        this.loginServiceTimer = Timer.builder("login.service.request.duration")
                .description("Duration of calls to LoginService")
                .register(meterRegistry);
    }

    public TransactionResponse processTransaction(TransactionRequest request) {
        return processTransaction(request, null);
    }

    /**
     * Process transaction and publish event to SQS.
     * If idempotencyKey is provided it will be used as eventId.
     */
    public TransactionResponse processTransaction(TransactionRequest request, String idempotencyKey) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UnauthorizedException("No valid authentication found");
        }

        String customerId = (String) authentication.getPrincipal();
        JwtDetails jwtDetails = (JwtDetails) authentication.getDetails();
        String rawToken = jwtDetails.rawToken();
        String channel = jwtDetails.channel();

        log.info("Processing transaction: customerId='{}', type='{}', amount='{}', channel='{}'",
                customerId, request.type(), request.amount(), channel);

        try {
            validator.validate(request);
            validateChannel(channel);

            SessionDTO session = fetchAndValidateSession(rawToken, customerId);

            // idempotency: reuse provided header if present, otherwise generate UUID
            String eventId = (idempotencyKey != null && !idempotencyKey.isBlank()) ? idempotencyKey : UUID.randomUUID().toString();

            TransactionEvent event = new TransactionEvent(
                    eventId,
                    customerId,
                    request.type().name(),
                    request.amount(),
                    channel,
                    Instant.now()
            );

            sqsProducer.publish(event);
            transactionCounter.increment();

            log.info("Transaction processed successfully: customerId='{}', type='{}'",
                    customerId, request.type());

            return new TransactionResponse(
                    UUID.randomUUID().toString(),
                    customerId,
                    request.type(),
                    request.amount(),
                    "ACCEPTED",
                    event.timestamp()
            );
        } catch (BusinessException | UnauthorizedException e) {
            transactionFailureCounter.increment();
            log.warn("Transaction rejected: customerId='{}', reason='{}'", customerId, e.getMessage());
            throw e;
        } catch (Exception e) {
            transactionFailureCounter.increment();
            log.error("Unexpected error processing transaction for customerId='{}': {}",
                    customerId, e.getMessage(), e);
            throw e;
        }
    }

    private SessionDTO fetchAndValidateSession(String token, String customerId) {
        log.info("Fetching session from LoginService for customerId='{}'", customerId);

        SessionDTO session = loginServiceTimer.record(() -> loginClient.getSession(token));

        if (session == null) {
            log.warn("[RISK] Proceeding without session validation for customerId='{}'", customerId);
            return null;
        }

        log.info("Session validated: username='{}', contractService='{}'",
                session.username(), session.contractService());

        if (Boolean.FALSE.equals(session.contractService())) {
            throw new BusinessException("Cliente não possui serviço contratado");
        }

        return session;
    }

    private void validateChannel(String channel) {
        if (channel == null || !ALLOWED_CHANNEL.equalsIgnoreCase(channel)) {
            throw new BusinessException(
                    "Channel '" + channel + "' is not allowed. Only MOBILE transactions are accepted.");
        }
    }
}
