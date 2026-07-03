package com.insurance.pricingservice.dto;

public record ClaimHistorySummaryResponse(
    Integer pastClaimCount,
    Double totalPastClaimAmount,
    Integer claimFreeYears,
    Double recencyWeightedClaimScore,
    Double prevCostClaimsYear,
    Double prevNMedicalServices,
    Boolean prevHadClaimOrService,
    Boolean claimFreePreviousYear
) {}
