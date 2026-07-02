package com.insurance.productservice.dto;

import java.time.Instant;

public record ErrorDetails(
    Instant timestamp,
    String message,
    String details
) {}
