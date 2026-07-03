package com.insurance.applicationpolicyservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.applicationpolicyservice.dto.EventEnvelope;
import com.insurance.applicationpolicyservice.model.OutboxEvent;
import com.insurance.applicationpolicyservice.model.OutboxStatus;
import com.insurance.applicationpolicyservice.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PolicyOutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.kafka.topics.policy-events:policy.events}")
    private String policyEventsTopic;

    public OutboxEvent enqueue(EventEnvelope envelope) {
        OutboxEvent event = OutboxEvent.builder()
                .aggregateType(envelope.aggregateType())
                .aggregateId(envelope.aggregateId())
                .eventType(envelope.eventType())
                .topicName(policyEventsTopic)
                .payload(objectMapper.valueToTree(envelope))
                .status(OutboxStatus.PENDING)
                .retryCount(0)
                .build();
        return outboxEventRepository.save(event);
    }
}
