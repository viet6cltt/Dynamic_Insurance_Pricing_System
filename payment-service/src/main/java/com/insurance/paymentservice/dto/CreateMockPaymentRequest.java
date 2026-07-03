package com.insurance.paymentservice.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateMockPaymentRequest(
        UUID contractId,
        UUID quoteId,
        UUID customerId,
        BigDecimal amount,
        String currency,
        String paymentMethod,
        String simulateResult
) {}
