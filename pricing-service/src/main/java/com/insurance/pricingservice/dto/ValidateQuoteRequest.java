package com.insurance.pricingservice.dto;

import java.util.UUID;

public record ValidateQuoteRequest(
    UUID buyerUserId,
    UUID insuredPersonId,
    UUID coveragePlanId
) {}
