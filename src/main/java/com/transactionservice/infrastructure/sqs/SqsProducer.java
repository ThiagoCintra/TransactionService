package com.transactionservice.infrastructure.sqs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.transactionservice.dto.TransactionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

@Slf4j
@Component
@RequiredArgsConstructor
public class SqsProducer {

    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;

    @Value("${aws.sqs.queue-url}")
    private String queueUrl;

    public void publish(TransactionEvent event) {
        try {
            String messageBody = objectMapper.writeValueAsString(event);

            log.info("Publishing event to SQS queue '{}' for customerId='{}', type='{}'",
                    queueUrl, event.customerId(), event.type());

            SendMessageRequest request = SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(messageBody)
                    .build();

            SendMessageResponse response = sqsClient.sendMessage(request);

            log.info("SQS message published successfully. MessageId='{}', customerId='{}'",
                    response.messageId(), event.customerId());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize TransactionEvent for customerId='{}': {}",
                    event.customerId(), e.getMessage(), e);
            throw new RuntimeException("Failed to serialize transaction event", e);
        } catch (Exception e) {
            log.error("Failed to publish event to SQS for customerId='{}': {}",
                    event.customerId(), e.getMessage(), e);
            throw new RuntimeException("Failed to publish transaction event to SQS", e);
        }
    }
}
