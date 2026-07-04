package com.insurance.notificationservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.notificationservice.dto.EventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventConsumer {

    private final ObjectMapper objectMapper;
    private final NotificationEventProcessor notificationEventProcessor;

    @KafkaListener(
            topics = {
                    "${app.kafka.topics.payment-events:payment.events}",
                    "${app.kafka.topics.policy-events:policy.events}",
                    "${app.kafka.topics.pricing-events:pricing.events}"
            },
            groupId = "${app.kafka.consumer-groups.notifications:notification-service-group}"
    )
    public void consume(String message) {
        try {
            EventEnvelope event = objectMapper.readValue(message, EventEnvelope.class);
            notificationEventProcessor.process(event);
        } catch (Exception ex) {
            log.error("Failed to process notification event: {}", ex.getMessage(), ex);
            throw new IllegalStateException("Failed to process notification event", ex);
        }
    }
}
