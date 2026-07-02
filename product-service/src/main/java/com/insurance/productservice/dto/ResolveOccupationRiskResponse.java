package com.insurance.productservice.dto;

public record ResolveOccupationRiskResponse(
    String productType,
    String occupationCode,
    String occupationName,
    String riskLevel
) {}
