package com.transactionservice.model.response;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(
        int status,
        String error,
        String message,
        Instant timestamp,
        List<String> details
) {
    public ErrorResponse(int status, String error, String message) {
        this(status, error, message, Instant.now(), List.of());
    }

    public ErrorResponse(int status, String error, String message, List<String> details) {
        this(status, error, message, Instant.now(), details);
    }
}
