package com.insurance.productservice.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record InternalCoveragePlanResponse(
    UUID coveragePlanId,
    UUID productId,
    String productType,
    String planName,
    BigDecimal sumInsured,
    BigDecimal loadingRate,
    Boolean reimbursementEnabled,
    String status
) {}
