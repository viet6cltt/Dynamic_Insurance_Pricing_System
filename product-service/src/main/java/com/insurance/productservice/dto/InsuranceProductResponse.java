package com.insurance.productservice.dto;

import java.time.Instant;
import java.util.UUID;

public record InsuranceProductResponse(
    UUID productId,
    String productType,
    String name,
    String description,
    String status,
    Instant createdAt,
    Instant updatedAt
) {}
