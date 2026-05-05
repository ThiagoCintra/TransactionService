package com.transactionservice.adapters;

import com.transactionservice.domains.SqsProducerDomain;
import com.transactionservice.financeiro.event.FinanceiroBaseEvent;
import com.transactionservice.infrastructure.sqs.SqsProducer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SqsProducerAdapter implements SqsProducerDomain {

    private final SqsProducer sqsProducer;

    @Override
    public void publish(FinanceiroBaseEvent event) {
        sqsProducer.publish(event);
    }
}
