package com.transactionservice.infrastructure.sqs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.transactionservice.financeiro.event.FinanceiroBaseEvent;
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
    public void publish(FinanceiroBaseEvent event) {
        Timer.Sample sample = Timer.start(meterRegistry);
        Counter counter = meterRegistry.counter("financeiro.eventos.enviados.sqs");

        String eventType = event.getClass().getSimpleName();

        try {
            String messageBody = objectMapper.writeValueAsString(event);

            log.info("Publicando evento no SQS: eventType='{}', eventId='{}', escolaId='{}'",
                    eventType, event.eventId(), event.escolaId());

            SendMessageRequest.Builder builder = SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(messageBody)
                    .messageAttributes(Map.of(
                            "eventType", MessageAttributeValue.builder()
                                    .dataType("String").stringValue(eventType).build(),
                            "escolaId", MessageAttributeValue.builder()
                                    .dataType("Number").stringValue(String.valueOf(event.escolaId())).build()
                    ));

            if (fifoQueue || queueUrl.toLowerCase().endsWith(".fifo")) {
                builder.messageGroupId(String.valueOf(event.escolaId()));
                builder.messageDeduplicationId(event.eventId());
            } else {
                builder.messageDeduplicationId(event.eventId());
            }

            SendMessageResponse response = sqsClient.sendMessage(builder.build());

            counter.increment();
            sample.stop(meterRegistry.timer("sqs.publish.duration"));

            log.info("Evento publicado com sucesso no SQS: messageId='{}', eventType='{}', escolaId='{}'",
                    response.messageId(), eventType, event.escolaId());
        } catch (JsonProcessingException e) {
            log.error("Falha ao serializar evento '{}': {}", eventType, e.getMessage(), e);
            throw new BusinessException("Falha ao serializar evento financeiro");
        } catch (Exception e) {
            log.error("Falha ao publicar evento '{}' no SQS (eventId='{}'): {}",
                    eventType, event.eventId(), e.getMessage(), e);
            throw new BusinessException("Falha ao publicar evento financeiro no SQS: " + e.getMessage());
        }
    }
}
