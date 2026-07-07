package com.insurance.pricingservice.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record QuoteExplanationResponse(
    JsonNode frequencyExplanation,
    JsonNode severityExplanation,
    JsonNode topRiskFactors,
    JsonNode shapValues,
    String explanationMethod,
    Boolean approximate
) {}
