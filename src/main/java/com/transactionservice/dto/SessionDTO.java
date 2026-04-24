package com.transactionservice.dto;

public record SessionDTO(
        String sessionId,
        String username,
        Boolean contractService,
        String symmetricKey,
        String role
) {}
