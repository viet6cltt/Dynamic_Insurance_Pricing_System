package com.insurance.notificationservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.insurance.notificationservice.dto.EventEnvelope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationEventConsumerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Mock
    private NotificationEventProcessor notificationEventProcessor;

    private NotificationEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new NotificationEventConsumer(objectMapper, notificationEventProcessor);
    }

    @Test
    void consumeDeserializesEventAndDelegatesToProcessor() throws Exception {
        EventEnvelope event = new EventEnvelope(
                UUID.randomUUID(),
                "policy.issued",
                1,
                Instant.now(),
                "application-policy-service",
                "Contract",
                UUID.randomUUID(),
                "corr-1",
                null,
                objectMapper.createObjectNode().put("customerId", UUID.randomUUID().toString()));

        consumer.consume(objectMapper.writeValueAsString(event));

        verify(notificationEventProcessor).process(argThat(processed ->
                processed.eventId().equals(event.eventId())
                        && processed.eventType().equals("policy.issued")));
    }

    @Test
    void consumeWrapsInvalidJsonAsIllegalStateException() {
        assertThatThrownBy(() -> consumer.consume("{invalid-json"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to process notification event");
        verify(notificationEventProcessor, never()).process(any());
    }
}
