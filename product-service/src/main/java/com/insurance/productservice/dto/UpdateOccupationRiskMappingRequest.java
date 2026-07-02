package com.insurance.productservice.dto;

public record UpdateOccupationRiskMappingRequest(
    String occupationName,
    String riskLevel,
    String status
) {}
