package com.transactionservice.service;

import com.transactionservice.dto.TransactionRequest;
import com.transactionservice.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component
public class TransactionValidator {

    private static final BigDecimal MIN_AMOUNT = BigDecimal.ZERO;
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000.00");

    public void validate(TransactionRequest request) {
        validateType(request);
        validateAmount(request);
    }

    private void validateType(TransactionRequest request) {
        if (request.type() == null) {
            throw new BusinessException("Transaction type is required");
        }
        log.debug("Transaction type '{}' is valid", request.type());
    }

    private void validateAmount(TransactionRequest request) {
        BigDecimal amount = request.amount();

        if (amount == null) {
            throw new BusinessException("Transaction amount is required");
        }
        if (amount.compareTo(MIN_AMOUNT) <= 0) {
            throw new BusinessException("Transaction amount must be greater than zero");
        }
        if (amount.compareTo(MAX_AMOUNT) > 0) {
            throw new BusinessException("Transaction amount exceeds maximum allowed value of " + MAX_AMOUNT);
        }
        log.debug("Transaction amount '{}' is valid", amount);
    }
}
