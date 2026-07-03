package com.insurance.pricingservice.dto;

public record PortfolioPricingProfile(
    String gender,
    String typeProduct,
    String typePolicy,
    String reimbursement,
    Double exposureTime,
    Double seniorityInsured,
    String newBusiness,
    String distributionChannel,
    Double prevCostClaimsYear,
    Double prevNMedicalServices,
    Boolean prevHadClaimOrService,
    Boolean claimFreePreviousYear
) {}
