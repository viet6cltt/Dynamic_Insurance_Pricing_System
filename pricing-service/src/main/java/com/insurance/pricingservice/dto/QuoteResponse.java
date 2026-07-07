package com.insurance.pricingservice.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record QuoteResponse(
    UUID quoteId,
    UUID buyerUserId,
    UUID insuredPersonId,
    UUID productId,
    UUID coveragePlanId,
    String productType,
    String planName,
    BigDecimal sumInsured,
    BigDecimal predictedAnnualFrequency,
    BigDecimal predictedAverageSeverity,
    BigDecimal purePremium,
    BigDecimal loadingRate,
    BigDecimal finalPremium,
    String frequencyModelVersion,
    String severityModelVersion,
    String riskLevel,
    String status,
    Instant createdAt,
    Instant expiredAt,
    QuoteExplanationResponse explanation
) {}
