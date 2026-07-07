package com.insurance.productservice.dto;

import java.math.BigDecimal;

public record CreateCoveragePlanRequest(
    String planName,
    String description,
    BigDecimal sumInsured,
    BigDecimal loadingRate,
    String status
) {}
