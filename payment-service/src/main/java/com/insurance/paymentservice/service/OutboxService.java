package com.insurance.paymentservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.paymentservice.dto.EventEnvelope;
import com.insurance.paymentservice.model.OutboxEvent;
import com.insurance.paymentservice.model.OutboxStatus;
import com.insurance.paymentservice.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.kafka.topics.payment-events:payment.events}")
    private String paymentEventsTopic;

    public OutboxEvent enqueue(EventEnvelope envelope) {
        OutboxEvent event = OutboxEvent.builder()
                .aggregateType(envelope.aggregateType())
                .aggregateId(envelope.aggregateId())
                .eventType(envelope.eventType())
                .topicName(paymentEventsTopic)
                .payload(objectMapper.valueToTree(envelope))
                .status(OutboxStatus.PENDING)
                .retryCount(0)
                .build();
        return outboxEventRepository.save(event);
    }
}
