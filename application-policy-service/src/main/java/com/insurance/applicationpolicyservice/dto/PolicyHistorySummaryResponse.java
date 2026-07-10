package com.insurance.applicationpolicyservice.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PolicyHistorySummaryResponse(
        UUID summaryId,
        UUID insuredPersonId,
        UUID policyholderUserId,
        String productType,
        BigDecimal prevCostClaimsYear,
        Integer prevNMedicalServices,
        Boolean prevHadClaimOrService,
        Boolean claimFreePreviousYear,
        BigDecimal totalPastClaimAmount,
        Integer pastClaimCount,
        Integer claimFreeYears,
        BigDecimal recencyWeightedClaimScore,
        LocalDate lastClaimDate,
        String claimSeverityLevel,
        BigDecimal seniorityInsured,
        Integer activePolicyCount,
        Integer completedPolicyCount,
        Instant calculatedAt,
        Instant updatedAt
) {}
