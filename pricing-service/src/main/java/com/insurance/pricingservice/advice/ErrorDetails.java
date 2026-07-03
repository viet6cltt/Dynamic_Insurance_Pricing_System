package com.insurance.pricingservice.advice;

import java.time.Instant;

public record ErrorDetails(
    Instant timestamp,
    String message,
    String details
) {}
