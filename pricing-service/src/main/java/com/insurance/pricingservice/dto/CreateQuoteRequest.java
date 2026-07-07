package com.insurance.pricingservice.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

public record CreateQuoteRequest(
    UUID insuredPersonId,
    UUID productId,
    UUID coveragePlanId,
    JsonNode riskProfile,
    Boolean reimbursementEnabled
) {}
