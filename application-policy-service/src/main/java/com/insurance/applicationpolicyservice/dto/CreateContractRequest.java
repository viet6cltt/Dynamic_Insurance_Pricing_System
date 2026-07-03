package com.insurance.applicationpolicyservice.dto;

import java.util.UUID;

public record CreateContractRequest(
    UUID quoteId,
    UUID insuredPersonId,
    UUID productId,
    UUID coveragePlanId,
    String simulatePaymentResult
) {}
