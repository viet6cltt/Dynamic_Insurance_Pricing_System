package com.insurance.pricingservice.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;

public record PortfolioModelOutput(
    String status,
    String modelVersion,
    String modelSource,
    String registryModelName,
    String registryAlias,
    String registryVersion,
    BigDecimal predictedAnnualClaimCost,
    BigDecimal rawPortfolioRiskFactor,
    BigDecimal portfolioRiskFactor,
    JsonNode portfolioModelExplanation,
    String message
) {}
