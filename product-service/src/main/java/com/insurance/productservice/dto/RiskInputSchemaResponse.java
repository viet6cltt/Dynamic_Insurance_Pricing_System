package com.insurance.productservice.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

public record RiskInputSchemaResponse(
    UUID schemaId,
    UUID productId,
    String productType,
    String schemaVersion,
    JsonNode schemaDefinition,
    String status,
    Instant createdAt,
    Instant updatedAt
) {}
