package com.insurance.productservice.dto;

import java.math.BigDecimal;

public record UpdateLoadingRateRequest(
    BigDecimal loadingRate
) {}
