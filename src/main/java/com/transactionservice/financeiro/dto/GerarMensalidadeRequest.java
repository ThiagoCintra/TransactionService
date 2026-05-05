package com.transactionservice.financeiro.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

/**
 * Payload para geração de cobrança mensal.
 *
 * <p>Regras de segurança:
 * <ul>
 *   <li>{@code total} NÃO é aceito no request — calculado exclusivamente pelo backend.</li>
 *   <li>{@code escolaId} NÃO é aceito no request — extraído do JWT (multi-tenancy).</li>
 * </ul>
 */
public record GerarMensalidadeRequest(

        @NotNull(message = "alunoId é obrigatório")
        Long alunoId,

        @NotBlank(message = "mesReferencia é obrigatório")
        @Pattern(regexp = "\\d{4}-\\d{2}", message = "mesReferencia deve estar no formato YYYY-MM")
        String mesReferencia,

        @NotNull(message = "mensalidade é obrigatória")
        @DecimalMin(value = "0.00", message = "mensalidade não pode ser negativa")
        BigDecimal mensalidade,

        @NotNull(message = "alimentacao é obrigatória")
        @DecimalMin(value = "0.00", message = "alimentacao não pode ser negativa")
        BigDecimal alimentacao,

        @DecimalMin(value = "0.00", message = "multa não pode ser negativa")
        BigDecimal multa,

        @DecimalMin(value = "0.00", message = "juros não pode ser negativo")
        BigDecimal juros
) {}
