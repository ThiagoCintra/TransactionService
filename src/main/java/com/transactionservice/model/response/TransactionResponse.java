package com.transactionservice.model.response;

import com.transactionservice.domain.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionResponse(
        String transactionId,
        String customerId,
        TransactionType type,
        BigDecimal amount,
        String status,
        Instant timestamp
) {}
