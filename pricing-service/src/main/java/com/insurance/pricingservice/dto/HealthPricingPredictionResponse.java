package com.insurance.pricingservice.dto;

public record HealthPricingPredictionResponse(
    String modelVersion,
    PortfolioModelOutput portfolioModel,
    HealthRiskModelOutput healthRiskModel,
    String finalPremiumCalculatedBy,
    String finalPremiumFormula
) {}
