package com.insurance.pricingservice.dto;

public record ResolveOccupationRiskResponse(
    String productType,
    String occupationCode,
    String occupationName,
    String riskLevel
) {}
