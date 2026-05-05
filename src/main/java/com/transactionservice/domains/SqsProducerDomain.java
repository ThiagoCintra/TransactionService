package com.transactionservice.domains;

import com.transactionservice.financeiro.event.FinanceiroBaseEvent;

/**
 * Domain interface for publishing financial events to a message broker.
 */
public interface SqsProducerDomain {

    void publish(FinanceiroBaseEvent event);

}
