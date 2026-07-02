package com.insurance.productservice.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record UpdateRiskInputSchemaRequest(
    String schemaVersion,
    JsonNode schemaDefinition,
    String status
) {}
