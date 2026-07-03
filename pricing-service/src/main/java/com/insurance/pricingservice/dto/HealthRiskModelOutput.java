package com.insurance.pricingservice.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;

public record HealthRiskModelOutput(
    String status,
    String modelVersion,
    String modelSource,
    String registryModelName,
    String registryAlias,
    String registryVersion,
    BigDecimal predictedHealthCost,
    BigDecimal baselineHealthCost,
    BigDecimal rawHealthRiskFactor,
    BigDecimal healthRiskFactor,
    String riskLevel,
    JsonNode healthRiskExplanation,
    String message
) {}
