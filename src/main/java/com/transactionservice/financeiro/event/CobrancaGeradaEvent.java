package com.transactionservice.financeiro.event;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Evento publicado no SQS quando uma cobrança mensal é gerada.
 */
public record CobrancaGeradaEvent(
        String eventId,
        Long escolaId,
        Long alunoId,
        Long cobrancaId,
        String mesReferencia,
        BigDecimal total,
        Instant timestamp
) implements FinanceiroBaseEvent {}
