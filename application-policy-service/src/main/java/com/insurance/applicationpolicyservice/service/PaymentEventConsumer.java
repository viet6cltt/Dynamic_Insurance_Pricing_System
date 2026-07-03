package com.insurance.applicationpolicyservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.applicationpolicyservice.dto.EventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private final ObjectMapper objectMapper;
    private final PaymentEventProcessor paymentEventProcessor;

    @KafkaListener(
            topics = "${app.kafka.topics.payment-events:payment.events}",
            groupId = "${app.kafka.consumer-groups.payment-events:policy-service-payment-group}"
    )
    public void consume(String message) {
        try {
            EventEnvelope event = objectMapper.readValue(message, EventEnvelope.class);
            paymentEventProcessor.process(event);
        } catch (Exception ex) {
            log.error("Failed to process payment event: {}", ex.getMessage(), ex);
            throw new IllegalStateException("Failed to process payment event", ex);
        }
    }
}
