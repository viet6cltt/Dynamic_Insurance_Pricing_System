package com.insurance.applicationpolicyservice.dto;

import java.util.UUID;

public record ValidateQuoteRequest(
    UUID buyerUserId,
    UUID insuredPersonId,
    UUID coveragePlanId
) {}
