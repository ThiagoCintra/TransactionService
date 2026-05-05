package com.transactionservice.financeiro.dto;

import com.transactionservice.financeiro.domain.StatusFinanceiro;
import jakarta.validation.constraints.NotNull;

/**
 * Payload para atualização interna de status de pagamento.
 * Uso restrito a ADMIN (endpoint interno).
 */
public record PagamentoRequest(

        @NotNull(message = "status é obrigatório")
        StatusFinanceiro status
) {}
