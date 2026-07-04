package com.insurance.notificationservice.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID notificationId,
        String eventType,
        String title,
        String message,
        String status,
        String channelPolicy,
        JsonNode metadata,
        Instant createdAt,
        Instant readAt
) {}
