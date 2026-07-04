package com.insurance.pricingservice.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record EventEnvelope(
        UUID eventId,
        String eventType,
        Integer eventVersion,
        Instant occurredAt,
        String producer,
        String aggregateType,
        UUID aggregateId,
        String correlationId,
        String causationId,
        JsonNode payload
) {}
