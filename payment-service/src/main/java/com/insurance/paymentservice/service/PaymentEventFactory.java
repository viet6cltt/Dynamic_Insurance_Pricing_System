package com.insurance.paymentservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.paymentservice.dto.EventEnvelope;
import com.insurance.paymentservice.dto.PaymentEventPayload;
import com.insurance.paymentservice.model.Payment;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class PaymentEventFactory {

    private final ObjectMapper objectMapper;

    public PaymentEventFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public EventEnvelope createPaymentEvent(Payment payment, String eventType, String correlationId, String causationId) {
        PaymentEventPayload eventPayload = new PaymentEventPayload(
                payment.getPaymentId(),
                payment.getContractId(),
                payment.getQuoteId(),
                payment.getCustomerId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getPaymentMethod(),
                payment.getProvider(),
                payment.getStatus().name(),
                payment.getExpiresAt(),
                payment.getPaidAt(),
                payment.getFailedAt(),
                payment.getExpiredAt(),
                payment.getFailureReason()
        );
        JsonNode payload = objectMapper.valueToTree(eventPayload);
        return new EventEnvelope(
                UUID.randomUUID(),
                eventType,
                1,
                Instant.now(),
                "payment-service",
                "Payment",
                payment.getPaymentId(),
                normalizeCorrelationId(correlationId),
                causationId,
                payload
        );
    }

    private String normalizeCorrelationId(String correlationId) {
        if (correlationId == null || correlationId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return correlationId;
    }
}
