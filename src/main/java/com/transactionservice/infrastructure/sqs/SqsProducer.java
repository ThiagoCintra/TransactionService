package com.transactionservice.infrastructure.sqs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.transactionservice.dto.TransactionEvent;
import com.transactionservice.exception.BusinessException;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SqsProducer {

    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    @Value("${aws.sqs.queue-url}")
    private String queueUrl;

    @Value("${aws.sqs.fifo:false}")
    private boolean fifoQueue;

    

    // Use Resilience4j retry (configured in application.yml) to retry transient failures
    @Retry(name = "sqsPublish")
    public void publish(TransactionEvent event) {
        Timer.Sample sample = Timer.start(meterRegistry);
        Counter counter = meterRegistry.counter("transactions.sent.to.sqs");

        try {
            String messageBody = objectMapper.writeValueAsString(event);

            // structured JSON log for event publish intent
            Map<String, Object> logMap = new HashMap<>();
            logMap.put("eventId", event.eventId());
            logMap.put("customerId", event.customerId());
            logMap.put("type", event.type());
            logMap.put("amount", event.amount());
            logMap.put("status", "SENT_TO_SQS");
            try {
                log.info(objectMapper.writeValueAsString(logMap));
            } catch (JsonProcessingException ignore) {
                log.info("SENT_TO_SQS eventId='{}' customerId='{}' type='{}' amount='{}'",
                        event.eventId(), event.customerId(), event.type(), event.amount());
            }

            SendMessageRequest.Builder builder = SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(messageBody)
                    .messageAttributes(Map.of(
                            "channel", MessageAttributeValue.builder().dataType("String").stringValue(event.channel()).build(),
                            "type", MessageAttributeValue.builder().dataType("String").stringValue(event.type()).build()
                    ));

            // If FIFO queue, include group id and deduplication id
            if (fifoQueue || queueUrl.toLowerCase().endsWith(".fifo")) {
                String groupId = event.customerId();
                builder.messageGroupId(groupId);
                builder.messageDeduplicationId(event.eventId());
            } else {
                // use deduplication id when provided (helps idempotency if broker supports it)
                builder.messageDeduplicationId(event.eventId());
            }

            SendMessageRequest request = builder.build();

            SendMessageResponse response = sqsClient.sendMessage(request);

            counter.increment();
            sample.stop(meterRegistry.timer("sqs.publish.duration"));

            log.info("SQS message published successfully. MessageId='{}', customerId='{}'",
                    response.messageId(), event.customerId());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize TransactionEvent for customerId='{}': {}",
                    event.customerId(), e.getMessage(), e);
            throw new BusinessException("Failed to serialize transaction event");
        } catch (Exception e) {
            log.error("Failed to publish event to SQS for customerId='{}', eventId='{}': {}",
                    event.customerId(), event.eventId(), e.getMessage(), e);
            throw new BusinessException("Failed to publish transaction event to SQS: " + e.getMessage());
        }
    }
}
