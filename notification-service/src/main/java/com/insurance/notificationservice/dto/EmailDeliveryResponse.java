package com.insurance.notificationservice.dto;

import java.time.Instant;
import java.util.UUID;

public record EmailDeliveryResponse(
        UUID deliveryId,
        UUID notificationId,
        String recipientEmail,
        String subject,
        String templateName,
        String status,
        Integer retryCount,
        Instant nextAttemptAt,
        Instant sentAt,
        Instant failedAt,
        String lastError,
        Instant createdAt,
        Instant updatedAt
) {}
