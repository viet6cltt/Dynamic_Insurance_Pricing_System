package com.insurance.applicationpolicyservice.dto;

public record PolicyExperienceSummaryResponse(
    Integer pastClaimCount,
    Double totalPastClaimAmount,
    Integer claimFreeYears,
    Double recencyWeightedClaimScore,
    Double prevCostClaimsYear,
    Double prevNMedicalServices,
    Boolean prevHadClaimOrService,
    Boolean claimFreePreviousYear,
    Double historicalExposureYears,
    Double seniorityInsured,
    Integer activePolicyCount,
    Integer completedPolicyCount
) {}
