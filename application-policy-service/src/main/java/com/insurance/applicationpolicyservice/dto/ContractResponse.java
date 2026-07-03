package com.insurance.applicationpolicyservice.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ContractResponse(
    UUID contractId,
    UUID applicantUserId,
    UUID insuredPersonId,
    UUID quoteId,
    UUID productId,
    UUID coveragePlanId,
    String productType,
    String typePolicy,
    String reimbursement,
    String distributionChannel,
    BigDecimal quotedPremium,
    BigDecimal basePremium,
    BigDecimal sumInsured,
    LocalDate effectiveDate,
    LocalDate expiryDate,
    BigDecimal exposureTime,
    Boolean newBusiness,
    Integer policyYear,
    String status,
    Instant submittedAt,
    Instant issuedAt,
    UUID paymentId,
    String paymentStatus,
    Instant createdAt,
    Instant updatedAt
) {}
