package com.insurance.applicationpolicyservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.insurance.applicationpolicyservice.dto.EventEnvelope;
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
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentEventConsumerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Mock
    private PaymentEventProcessor paymentEventProcessor;

    private PaymentEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new PaymentEventConsumer(objectMapper, paymentEventProcessor);
    }

    @Test
    void consumeDeserializesEventAndDelegatesToProcessor() throws Exception {
        EventEnvelope event = new EventEnvelope(
                UUID.randomUUID(),
                "payment.succeeded",
                1,
                Instant.now(),
                "payment-service",
                "Payment",
                UUID.randomUUID(),
                "corr-1",
                null,
                objectMapper.createObjectNode().put("contractId", UUID.randomUUID().toString()));

        consumer.consume(objectMapper.writeValueAsString(event));

        verify(paymentEventProcessor).process(argThat(processed ->
                processed.eventId().equals(event.eventId())
                        && processed.eventType().equals("payment.succeeded")));
    }

    @Test
    void consumeWrapsInvalidJsonAsIllegalStateException() {
        assertThatThrownBy(() -> consumer.consume("{invalid-json"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to process payment event");
        verify(paymentEventProcessor, org.mockito.Mockito.never()).process(any());
    }
}
