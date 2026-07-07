package com.insurance.applicationpolicyservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.applicationpolicyservice.model.OutboxEvent;
import com.insurance.applicationpolicyservice.model.OutboxStatus;
import com.insurance.applicationpolicyservice.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PolicyOutboxPublisherTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private PolicyOutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new PolicyOutboxPublisher(outboxEventRepository, kafkaTemplate, objectMapper);
        ReflectionTestUtils.setField(publisher, "batchSize", 10);
        ReflectionTestUtils.setField(publisher, "maxRetries", 2);
    }

    @Test
    void publishPendingEventsPublishesMessageUsingContractIdAsKafkaKey() {
        UUID contractId = UUID.randomUUID();
        OutboxEvent event = event(objectMapper.createObjectNode()
                .set("payload", objectMapper.createObjectNode().put("contractId", contractId.toString())));
        when(outboxEventRepository.findByStatusOrderByCreatedAtAsc(eq(OutboxStatus.PENDING), any(Pageable.class)))
                .thenReturn(List.of(event));
        when(kafkaTemplate.send(eq("policy.events"), eq(contractId.toString()), any(String.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        int published = publisher.publishPendingEvents();

        assertThat(published).isEqualTo(1);
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(event.getPublishedAt()).isNotNull();
        assertThat(event.getLockedAt()).isNotNull();
        assertThat(event.getLastError()).isNull();
        verify(kafkaTemplate).send(eq("policy.events"), eq(contractId.toString()), any(String.class));
    }

    @Test
    void publishPendingEventsFallsBackToAggregateIdWhenContractIdMissing() {
        OutboxEvent event = event(objectMapper.createObjectNode().set("payload", objectMapper.createObjectNode()));
        when(outboxEventRepository.findByStatusOrderByCreatedAtAsc(eq(OutboxStatus.PENDING), any(Pageable.class)))
                .thenReturn(List.of(event));
        when(kafkaTemplate.send(eq("policy.events"), eq(event.getAggregateId().toString()), any(String.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher.publishPendingEvents();

        verify(kafkaTemplate).send(eq("policy.events"), eq(event.getAggregateId().toString()), any(String.class));
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
    }

    @Test
    void publishPendingEventsKeepsEventPendingBeforeMaxRetries() {
        OutboxEvent event = event(objectMapper.createObjectNode()
                .set("payload", objectMapper.createObjectNode().put("contractId", UUID.randomUUID().toString())));
        when(outboxEventRepository.findByStatusOrderByCreatedAtAsc(eq(OutboxStatus.PENDING), any(Pageable.class)))
                .thenReturn(List.of(event));
        when(kafkaTemplate.send(any(), any(), any())).thenThrow(new RuntimeException("Kafka down"));

        publisher.publishPendingEvents();

        assertThat(event.getRetryCount()).isEqualTo(1);
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getLastError()).contains("Kafka down");
    }

    private OutboxEvent event(JsonNode payload) {
        return OutboxEvent.builder()
                .eventId(UUID.randomUUID())
                .aggregateType("Contract")
                .aggregateId(UUID.randomUUID())
                .eventType("policy.issued")
                .topicName("policy.events")
                .payload(payload)
                .status(OutboxStatus.PENDING)
                .retryCount(0)
                .createdAt(Instant.now())
                .build();
    }
}
