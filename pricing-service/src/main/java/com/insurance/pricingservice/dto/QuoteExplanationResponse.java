package com.insurance.pricingservice.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record QuoteExplanationResponse(
    JsonNode portfolioExplanation,
    JsonNode healthExplanation,
    JsonNode topRiskFactors,
    JsonNode shapValues,
    String explanationMethod,
    Boolean approximate
) {}
