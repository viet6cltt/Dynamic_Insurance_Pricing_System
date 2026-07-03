package com.insurance.pricingservice.dto;

public record HistoricalExperienceFeatures(
    Integer pastClaimCount,
    Double totalPastClaimAmount,
    Integer claimFreeYears,
    Double recencyWeightedClaimScore
) {}
