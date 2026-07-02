package com.insurance.productservice.dto;

import java.time.Instant;
import java.util.UUID;

public record OccupationRiskMappingResponse(
    UUID mappingId,
    UUID productId,
    String productType,
    String occupationCode,
    String occupationName,
    String riskLevel,
    String status,
    Instant createdAt,
    Instant updatedAt
) {}
