package com.insurance.applicationpolicyservice.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ClaimHistoryResponse(
        UUID recordId,
        UUID contractId,
        UUID insuredPersonId,
        UUID policyholderUserId,
        String productType,
        LocalDate experienceDate,
        BigDecimal claimAmount,
        Integer nMedicalServices,
        Boolean hadClaimOrService,
        String severityLevel,
        String source,
        Instant importedAt
) {}
