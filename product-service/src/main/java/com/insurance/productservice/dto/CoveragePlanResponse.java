package com.insurance.productservice.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CoveragePlanResponse(
    UUID coveragePlanId,
    UUID productId,
    String productType,
    String planName,
    String description,
    BigDecimal basePremium,
    BigDecimal sumInsured,
    String status,
    Instant createdAt,
    Instant updatedAt
) {}
