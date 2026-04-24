package com.transactionservice.validator;

import com.transactionservice.domain.TransactionType;
import com.transactionservice.dto.TransactionRequest;
import com.transactionservice.exception.BusinessException;
import com.transactionservice.service.TransactionValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TransactionValidator Tests")
class TransactionValidatorTest {

    private TransactionValidator validator;

    @BeforeEach
    void setUp() {
        validator = new TransactionValidator();
    }

    @Test
    @DisplayName("Should pass validation for a valid PIX transaction")
    void shouldPassValidationForValidPixTransaction() {
        TransactionRequest request = new TransactionRequest(TransactionType.PIX, new BigDecimal("100.00"));
        assertThatCode(() -> validator.validate(request)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should pass validation for all transaction types")
    void shouldPassValidationForAllTransactionTypes() {
        for (TransactionType type : TransactionType.values()) {
            TransactionRequest request = new TransactionRequest(type, new BigDecimal("50.00"));
            assertThatCode(() -> validator.validate(request)).doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("Should fail validation when type is null")
    void shouldFailWhenTypeIsNull() {
        TransactionRequest request = new TransactionRequest(null, new BigDecimal("100.00"));
        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("type is required");
    }

    @Test
    @DisplayName("Should fail validation when amount is null")
    void shouldFailWhenAmountIsNull() {
        TransactionRequest request = new TransactionRequest(TransactionType.PIX, null);
        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("amount is required");
    }

    @Test
    @DisplayName("Should fail validation when amount is zero")
    void shouldFailWhenAmountIsZero() {
        TransactionRequest request = new TransactionRequest(TransactionType.PIX, BigDecimal.ZERO);
        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("greater than zero");
    }

    @Test
    @DisplayName("Should fail validation when amount is negative")
    void shouldFailWhenAmountIsNegative() {
        TransactionRequest request = new TransactionRequest(TransactionType.PIX, new BigDecimal("-50.00"));
        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("greater than zero");
    }

    @Test
    @DisplayName("Should fail validation when amount exceeds maximum")
    void shouldFailWhenAmountExceedsMaximum() {
        TransactionRequest request = new TransactionRequest(TransactionType.PIX, new BigDecimal("2000000.00"));
        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("maximum allowed");
    }

    @Test
    @DisplayName("Should pass validation for maximum allowed amount")
    void shouldPassForMaximumAllowedAmount() {
        TransactionRequest request = new TransactionRequest(TransactionType.PIX, new BigDecimal("1000000.00"));
        assertThatCode(() -> validator.validate(request)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should pass validation for minimum positive amount")
    void shouldPassForMinimumPositiveAmount() {
        TransactionRequest request = new TransactionRequest(TransactionType.PIX, new BigDecimal("0.01"));
        assertThatCode(() -> validator.validate(request)).doesNotThrowAnyException();
    }
}
