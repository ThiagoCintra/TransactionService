package com.transactionservice.model.session;

public record SessionDTO(
        String sessionId,
        String username,
        Boolean contractService,
        String symmetricKey,
        String role
) {}
