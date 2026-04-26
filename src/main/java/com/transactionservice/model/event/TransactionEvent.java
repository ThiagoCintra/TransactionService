package com.transactionservice.model.event;

import java.math.BigDecimal;
import java.time.Instant;


public record TransactionEvent(
        String eventId,
        String customerId,
        String type,
        BigDecimal amount,
        String channel,
        Instant timestamp
) {
}
