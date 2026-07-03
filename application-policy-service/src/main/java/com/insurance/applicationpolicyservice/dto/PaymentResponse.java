package com.insurance.applicationpolicyservice.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        UUID paymentId,
        UUID contractId,
        UUID quoteId,
        UUID customerId,
        BigDecimal amount,
        String currency,
        String paymentMethod,
        String provider,
        String status,
        Instant expiresAt,
        Instant paidAt,
        Instant failedAt,
        Instant expiredAt,
        String failureReason,
        Instant createdAt,
        Instant updatedAt
) {}
