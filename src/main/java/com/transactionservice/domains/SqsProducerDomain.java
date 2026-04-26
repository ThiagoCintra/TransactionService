package com.transactionservice.domains;

import com.transactionservice.model.event.TransactionEvent;

/**
 * Domain interface for publishing transaction events to a message broker.
 */
public interface SqsProducerDomain {

    void publish(TransactionEvent event);

}
