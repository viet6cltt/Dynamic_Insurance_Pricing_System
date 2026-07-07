package com.insurance.productservice.dto;

import java.math.BigDecimal;

public record UpdateCoveragePlanRequest(
    String planName,
    String description,
    BigDecimal sumInsured,
    BigDecimal loadingRate,
    String status
) {}
