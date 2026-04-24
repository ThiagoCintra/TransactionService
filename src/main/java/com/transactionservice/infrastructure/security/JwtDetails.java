package com.transactionservice.infrastructure.security;

/**
 * Holds JWT-derived details stored in the Spring Security Authentication object.
 */
public record JwtDetails(String channel, String rawToken) {}
