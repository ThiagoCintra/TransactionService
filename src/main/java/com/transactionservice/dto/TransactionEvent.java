package com.transactionservice.dto;

import com.transactionservice.domain.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionEvent(
        String customerId,
        TransactionType type,
        BigDecimal amount,
        Instant timestamp
) {}
