package com.insurance.productservice.dto;

public record CreateOccupationRiskMappingRequest(
    String occupationCode,
    String occupationName,
    String riskLevel,
    String status
) {}
