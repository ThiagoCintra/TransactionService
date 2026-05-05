package com.transactionservice.financeiro.event;

import java.time.Instant;

/**
 * Evento publicado no SQS quando o pagamento de uma cobrança é confirmado.
 * Consumido pelo pagamentos-service para sincronização de status.
 */
public record PagamentoConfirmadoEvent(
        String eventId,
        Long escolaId,
        Long alunoId,
        Long cobrancaId,
        String mesReferencia,
        Instant timestamp
) implements FinanceiroBaseEvent {}
