package com.insurance.pricingservice.dto;

public record HealthPricingPredictionRequest(
    String productType,
    HealthRiskProfile riskProfile,
    PortfolioPricingProfile portfolioProfile,
    HistoricalExperienceFeatures historicalExperienceFeatures
) {}
