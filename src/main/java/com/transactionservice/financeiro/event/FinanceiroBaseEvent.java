package com.transactionservice.financeiro.event;

/**
 * Interface base para todos os eventos do domínio financeiro publicados no SQS.
 */
public interface FinanceiroBaseEvent {

    String eventId();

    Long escolaId();
}
