package com.insurance.paymentservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.insurance.paymentservice.model.Payment;
import com.insurance.paymentservice.model.PaymentStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PaymentEventFactoryTest {

    @Test
    void createPaymentEventBuildsEnvelopePayloadAndFallbackCorrelationId() {
        Payment payment = Payment.builder()
                .paymentId(UUID.randomUUID())
                .contractId(UUID.randomUUID())
                .quoteId(UUID.randomUUID())
                .customerId(UUID.randomUUID())
                .amount(new BigDecimal("100.00"))
                .currency("VND")
                .paymentMethod("MOCK")
                .provider("MOCK_PROVIDER")
                .status(PaymentStatus.SUCCESS)
                .expiresAt(Instant.now())
                .paidAt(Instant.now())
                .build();

        var envelope = new PaymentEventFactory(new ObjectMapper().registerModule(new JavaTimeModule()))
                .createPaymentEvent(payment, "payment.succeeded", null, "cause-1");

        assertEquals("payment.succeeded", envelope.eventType());
        assertEquals(payment.getPaymentId(), envelope.aggregateId());
        assertFalse(envelope.correlationId().isBlank());
        assertEquals("SUCCESS", envelope.payload().get("status").asText());
    }
}
