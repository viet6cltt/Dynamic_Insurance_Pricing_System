package com.insurance.pricingservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.pricingservice.dto.EventEnvelope;
import com.insurance.pricingservice.model.OutboxEvent;
import com.insurance.pricingservice.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PricingOutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.kafka.topics.pricing-events:pricing.events}")
    private String pricingEventsTopic;

    public void enqueue(EventEnvelope envelope) {
        OutboxEvent event = OutboxEvent.builder()
                .aggregateType(envelope.aggregateType())
                .aggregateId(envelope.aggregateId())
                .eventType(envelope.eventType())
                .topicName(pricingEventsTopic)
                .payload(objectMapper.valueToTree(envelope))
                .build();
        outboxEventRepository.save(event);
    }
}
