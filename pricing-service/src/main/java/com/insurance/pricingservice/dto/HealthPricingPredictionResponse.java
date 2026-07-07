package com.insurance.pricingservice.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;

public record HealthPricingPredictionResponse(
    BigDecimal predictedAnnualFrequency,
    BigDecimal predictedAverageSeverity,
    BigDecimal purePremium,
    String riskLevel,
    String frequencyModelVersion,
    String severityModelVersion,
    JsonNode frequencyExplanation,
    JsonNode severityExplanation,
    JsonNode topRiskFactors
) {
    public HealthPricingPredictionResponse(
        BigDecimal predictedAnnualFrequency,
        BigDecimal predictedAverageSeverity,
        BigDecimal purePremium,
        String riskLevel,
        String frequencyModelVersion,
        String severityModelVersion,
        JsonNode frequencyExplanation,
        JsonNode severityExplanation
    ) {
        this(predictedAnnualFrequency, predictedAverageSeverity, purePremium, riskLevel,
             frequencyModelVersion, severityModelVersion, frequencyExplanation, severityExplanation, null);
    }
}
