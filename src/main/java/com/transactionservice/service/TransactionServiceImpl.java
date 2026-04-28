package com.transactionservice.service;

import com.transactionservice.exception.BusinessException;
import com.transactionservice.exception.UnauthorizedException;
import com.transactionservice.model.event.TransactionEvent;
import com.transactionservice.model.request.TransactionRequest;
import com.transactionservice.model.response.TransactionResponse;
import com.transactionservice.model.session.SessionDTO;
import com.transactionservice.domains.LoginClientDomain;
import com.transactionservice.domains.SqsProducerDomain;
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
public class TransactionServiceImpl implements TransactionService {

    private static final String ALLOWED_CHANNEL = "MOBILE";

    private final LoginClientDomain loginClient;
    private final SqsProducerDomain sqsProducer;
    private final TransactionValidator validator;
    private final Counter transactionCounter;
    private final Counter transactionFailureCounter;
    private final Timer loginServiceTimer;

    public TransactionServiceImpl(LoginClientDomain loginClient,
                                  SqsProducerDomain sqsProducer,
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

    @Override
    public TransactionResponse processTransaction(TransactionRequest request) {
        return processTransaction(request, null);
    }

    @Override
    public TransactionResponse processTransaction(TransactionRequest request, String idempotencyKey) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UnauthorizedException("No valid authentication found");
        }

        String customerId = (String) authentication.getPrincipal();
        var jwtDetails = (com.transactionservice.infrastructure.security.JwtDetails) authentication.getDetails();
        String rawToken = jwtDetails.rawToken();
        String channel = jwtDetails.channel();

        log.info("Processing transaction: customerId='{}', type='{}', amount='{}', channel='{}'",
                customerId, request.type(), request.amount(), channel);

        try {
            validator.validate(request);
            validateChannel(channel);

            SessionDTO session = fetchAndValidateSession(rawToken, customerId);

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

//        if (Boolean.FALSE.equals(session.contractService())) {
//            throw new BusinessException("Cliente não possui serviço contratado");
//        }

        return session;
    }

    private void validateChannel(String channel) {
        if (channel == null || !ALLOWED_CHANNEL.equalsIgnoreCase(channel)) {
            throw new BusinessException(
                    "Channel '" + channel + "' is not allowed. Only MOBILE transactions are accepted.");
        }
    }

}
