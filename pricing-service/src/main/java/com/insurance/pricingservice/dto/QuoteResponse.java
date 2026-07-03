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
    BigDecimal basePremium,
    BigDecimal sumInsured,
    BigDecimal predictedAnnualClaimCost,
    BigDecimal predictedHealthCost,
    BigDecimal baselineHealthCost,
    BigDecimal portfolioRiskFactor,
    BigDecimal healthRiskFactor,
    BigDecimal combinedRiskFactor,
    BigDecimal finalPremium,
    String riskLevel,
    String status,
    Instant createdAt,
    Instant expiredAt,
    QuoteExplanationResponse explanation
) {}
