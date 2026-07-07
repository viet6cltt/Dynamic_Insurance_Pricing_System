package com.insurance.applicationpolicyservice.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ValidateQuoteResponse(
    boolean valid,
    UUID quoteId,
    UUID buyerUserId,
    UUID insuredPersonId,
    UUID productId,
    UUID coveragePlanId,
    BigDecimal purePremium,
    BigDecimal loadingRate,
    BigDecimal finalPremium,
    String status,
    Instant expiredAt
) {}
