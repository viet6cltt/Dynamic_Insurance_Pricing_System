package com.insurance.applicationpolicyservice.service;

import com.insurance.applicationpolicyservice.dto.*;
import com.insurance.applicationpolicyservice.model.ClaimExperienceRecord;
import com.insurance.applicationpolicyservice.model.InsuranceContract;
import com.insurance.applicationpolicyservice.model.PolicyExperienceSummary;
import com.insurance.applicationpolicyservice.repository.ClaimExperienceRecordRepository;
import com.insurance.applicationpolicyservice.repository.InsuranceContractRepository;
import com.insurance.applicationpolicyservice.repository.PolicyExperienceSummaryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContractService {

    private final InsuranceContractRepository contractRepository;
    private final PolicyExperienceSummaryRepository summaryRepository;
    private final ClaimExperienceRecordRepository claimExperienceRecordRepository;
    private final PricingServiceClient pricingServiceClient;
    private final PaymentServiceClient paymentServiceClient;

    @Transactional
    public ContractResponse createContract(UUID buyerUserId, CreateContractRequest request) {
        log.info("Creating insurance contract from quote ID: {} for buyer: {}", request.quoteId(), buyerUserId);

        // 1. Validate quote via Pricing Service Feign Client
        ValidateQuoteResponse validateResponse;
        try {
            validateResponse = pricingServiceClient.validateQuote(
                    request.quoteId(),
                    new ValidateQuoteRequest(null, request.insuredPersonId(), request.coveragePlanId())
            );
        } catch (Exception e) {
            log.error("Failed to connect to Pricing Service for quote validation: {}", e.getMessage());
            throw new IllegalStateException("Pricing Service is currently unavailable. Please try again later.");
        }

        if (validateResponse == null || !validateResponse.valid()) {
            String reason = validateResponse == null ? "Null response" : "Quote status: " + validateResponse.status();
            throw new IllegalArgumentException("Quote validation failed. Reason: " + reason);
        }

        if (contractRepository.existsByInsuredPersonIdAndProductId(
                request.insuredPersonId(),
                validateResponse.productId())) {
            throw new IllegalArgumentException(
                    "Insured person already has a contract related to this insurance product.");
        }

        // 2. Initialize contract entity as PAYMENT_PENDING
        long existingContractsCount = contractRepository.countByInsuredPersonIdAndProductType(
                request.insuredPersonId(), "HEALTH" // default to HEALTH for MVP
        );
        boolean isNewBusiness = existingContractsCount == 0;
        int policyYear = (int) existingContractsCount + 1;

        InsuranceContract contract = InsuranceContract.builder()
                .applicantUserId(buyerUserId)
                .insuredPersonId(request.insuredPersonId())
                .quoteId(request.quoteId())
                .productId(request.productId())
                .coveragePlanId(request.coveragePlanId())
                .productType("HEALTH") // Default product type
                .typePolicy("STANDARD") // Default policy segment
                .reimbursement("FULL") // Default reimbursement type
                .distributionChannel("DIRECT") // Default channel
                .purePremium(validateResponse.purePremium())
                .loadingRate(validateResponse.loadingRate())
                .quotedPremium(validateResponse.finalPremium())
                .sumInsured(BigDecimal.valueOf(100000000)) // Default sum insured fallback
                .status(ContractStatus.PAYMENT_PENDING)
                .submittedAt(Instant.now())
                .newBusiness(isNewBusiness)
                .policyYear(policyYear)
                .build();

        InsuranceContract savedContract = contractRepository.save(contract);
        log.info("Contract initialized with ID: {} and status: PAYMENT_PENDING", savedContract.getContractId());

        // 3. Create mock payment in Payment Service. Contract activation is event-driven.
        PaymentResponse paymentResponse = createPayment(savedContract, request.simulatePaymentResult());
        savedContract.setPaymentId(paymentResponse.paymentId());
        savedContract.setPaymentStatus(paymentResponse.status());

        // 4. Mark quote as USED in Pricing Service. The local contract remains the source of truth
        // if this non-critical remote update fails.
        try {
            QuoteResponse quoteDetails = pricingServiceClient.markQuoteUsed(request.quoteId());
            if (quoteDetails != null) {
                savedContract.setProductType(quoteDetails.productType());
                savedContract.setPurePremium(quoteDetails.purePremium());
                savedContract.setLoadingRate(quoteDetails.loadingRate());
                savedContract.setQuotedPremium(quoteDetails.finalPremium());
                savedContract.setSumInsured(quoteDetails.sumInsured());
            }
        } catch (Exception e) {
            log.warn("Failed to mark quote as used in Pricing Service: {}", e.getMessage());
        }

        savedContract = contractRepository.save(savedContract);

        return mapToResponse(savedContract);
    }

    @Transactional(readOnly = true)
    public ContractResponse getContractById(UUID contractId) {
        InsuranceContract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new IllegalArgumentException("Contract not found with ID: " + contractId));
        return mapToResponse(contract);
    }

    @Transactional(readOnly = true)
    public List<ContractResponse> getMyContracts(UUID buyerUserId) {
        List<InsuranceContract> contracts = contractRepository.findByApplicantUserIdOrderByCreatedAtDesc(buyerUserId);
        return contracts.stream().map(this::mapToResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ClaimHistoryResponse> getMyClaimHistory(UUID buyerUserId) {
        return claimExperienceRecordRepository.findByPolicyholderUserIdOrderByExperienceDateDesc(buyerUserId)
                .stream()
                .map(this::mapClaimToResponse)
                .toList();
    }

    @Transactional
    public ContractResponse payContract(UUID buyerUserId, UUID contractId) {
        InsuranceContract contract = contractRepository.findByIdForUpdate(contractId)
                .orElseThrow(() -> new IllegalArgumentException("Contract not found with ID: " + contractId));

        if (!contract.getApplicantUserId().equals(buyerUserId)) {
            throw new IllegalArgumentException("Contract does not belong to current user.");
        }

        if (contract.getStatus() == ContractStatus.ACTIVE) {
            return mapToResponse(contract);
        }

        if (contract.getStatus() != ContractStatus.PAYMENT_PENDING
                && contract.getStatus() != ContractStatus.PAYMENT_FAILED) {
            throw new IllegalArgumentException("Contract is not payable in status: " + contract.getStatus());
        }

        contract.setStatus(ContractStatus.PAYMENT_PENDING);
        PaymentResponse paymentResponse = createPayment(
                contract,
                "SUCCESS",
                "contract-pay-" + contract.getContractId() + "-" + UUID.randomUUID(),
                "CUSTOMER_PAYMENT"
        );
        contract.setPaymentId(paymentResponse.paymentId());
        contract.setPaymentStatus(paymentResponse.status());
        InsuranceContract savedContract = contractRepository.save(contract);
        return mapToResponse(savedContract);
    }

    @Transactional(readOnly = true)
    public PolicyExperienceSummaryResponse getExperienceSummary(UUID customerId, String productType) {
        log.info("Fetching policy experience summary for customer: {} and product: {}", customerId, productType);
        PolicyExperienceSummary summary = summaryRepository.findByInsuredPersonIdAndProductType(customerId, productType)
                .or(() -> summaryRepository.findByPolicyholderUserIdAndProductType(customerId, productType))
                .orElseGet(() -> {
                    // Fallback default for new clients without previous experience record
                    return PolicyExperienceSummary.builder()
                            .insuredPersonId(customerId)
                            .policyholderUserId(customerId)
                            .productType(productType)
                            .prevCostClaimsYear(BigDecimal.ZERO)
                            .prevNMedicalServices(0)
                            .prevHadClaimOrService(false)
                            .claimFreePreviousYear(true)
                            .totalPastClaimAmount(BigDecimal.ZERO)
                            .pastClaimCount(0)
                            .claimFreeYears(5)
                            .recencyWeightedClaimScore(BigDecimal.ZERO)
                            .seniorityInsured(BigDecimal.ZERO)
                            .activePolicyCount(0)
                            .completedPolicyCount(0)
                            .build();
                });

        return new PolicyExperienceSummaryResponse(
                summary.getPastClaimCount(),
                summary.getTotalPastClaimAmount().doubleValue(),
                summary.getClaimFreeYears(),
                summary.getRecencyWeightedClaimScore().doubleValue(),
                summary.getPrevCostClaimsYear().doubleValue(),
                summary.getPrevNMedicalServices().doubleValue(),
                summary.getPrevHadClaimOrService(),
                summary.getClaimFreePreviousYear(),
                summary.getSeniorityInsured().doubleValue()
        );
    }

    // --- Helper Methods ---

    private PaymentResponse createPayment(InsuranceContract contract, String simulatePaymentResult) {
        return createPayment(
                contract,
                simulatePaymentResult,
                "contract-" + contract.getContractId(),
                "MOCK"
        );
    }

    private PaymentResponse createPayment(InsuranceContract contract,
                                          String simulatePaymentResult,
                                          String idempotencyKey,
                                          String paymentMethod) {
        String normalizedSimulateResult = simulatePaymentResult == null || simulatePaymentResult.isBlank()
                ? "SUCCESS"
                : simulatePaymentResult;
        CreateMockPaymentRequest paymentRequest = new CreateMockPaymentRequest(
                contract.getContractId(),
                contract.getQuoteId(),
                contract.getApplicantUserId(),
                contract.getQuotedPremium(),
                "VND",
                paymentMethod,
                normalizedSimulateResult
        );
        String correlationId = UUID.randomUUID().toString();
        try {
            return paymentServiceClient.createMockPayment(paymentRequest, idempotencyKey, correlationId);
        } catch (Exception e) {
            log.error("Failed to create payment for contract {}: {}", contract.getContractId(), e.getMessage());
            throw new IllegalStateException("Payment Service is currently unavailable. Please try again later.");
        }
    }

    public void updateExperienceSummary(InsuranceContract contract) {
        log.info("Updating experience summary for insured person: {}", contract.getInsuredPersonId());
        
        List<InsuranceContract> allContracts = contractRepository.findByInsuredPersonIdAndProductType(
                contract.getInsuredPersonId(), contract.getProductType()
        );

        int activeCount = 0;
        int completedCount = 0;
        LocalDate earliestEffectiveDate = LocalDate.now();

        for (InsuranceContract c : allContracts) {
            if (c.getStatus() == ContractStatus.ACTIVE) {
                activeCount++;
            } else if (c.getStatus() == ContractStatus.EXPIRED || c.getStatus() == ContractStatus.CANCELLED) {
                completedCount++;
            }
            if (c.getEffectiveDate() != null && c.getEffectiveDate().isBefore(earliestEffectiveDate)) {
                earliestEffectiveDate = c.getEffectiveDate();
            }
        }

        long daysOfSeniority = ChronoUnit.DAYS.between(earliestEffectiveDate, LocalDate.now());
        BigDecimal seniorityYears = BigDecimal.valueOf(daysOfSeniority)
                .divide(BigDecimal.valueOf(365), 2, RoundingMode.HALF_UP);

        PolicyExperienceSummary summary = summaryRepository.findByInsuredPersonIdAndProductType(
                contract.getInsuredPersonId(), contract.getProductType()
        ).orElseGet(() -> PolicyExperienceSummary.builder()
                .insuredPersonId(contract.getInsuredPersonId())
                .policyholderUserId(contract.getApplicantUserId())
                .productType(contract.getProductType())
                .prevCostClaimsYear(BigDecimal.ZERO)
                .prevNMedicalServices(0)
                .prevHadClaimOrService(false)
                .claimFreePreviousYear(true)
                .totalPastClaimAmount(BigDecimal.ZERO)
                .pastClaimCount(0)
                .claimFreeYears(5)
                .recencyWeightedClaimScore(BigDecimal.ZERO)
                .build());

        summary.setActivePolicyCount(activeCount);
        summary.setCompletedPolicyCount(completedCount);
        summary.setSeniorityInsured(seniorityYears);
        
        summaryRepository.save(summary);
        log.info("Experience summary successfully updated/created for insured: {}", contract.getInsuredPersonId());
    }

    private ContractResponse mapToResponse(InsuranceContract c) {
        return new ContractResponse(
                c.getContractId(),
                c.getApplicantUserId(),
                c.getInsuredPersonId(),
                c.getQuoteId(),
                c.getProductId(),
                c.getCoveragePlanId(),
                c.getProductType(),
                c.getTypePolicy(),
                c.getReimbursement(),
                c.getDistributionChannel(),
                c.getQuotedPremium(),
                c.getPurePremium(),
                c.getLoadingRate(),
                c.getSumInsured(),
                c.getEffectiveDate(),
                c.getExpiryDate(),
                c.getExposureTime(),
                c.getNewBusiness(),
                c.getPolicyYear(),
                c.getStatus().name(),
                c.getSubmittedAt(),
                c.getIssuedAt(),
                c.getPaymentId(),
                c.getPaymentStatus(),
                c.getCreatedAt(),
                c.getUpdatedAt()
        );
    }

    private ClaimHistoryResponse mapClaimToResponse(ClaimExperienceRecord record) {
        return new ClaimHistoryResponse(
                record.getRecordId(),
                record.getContract() != null ? record.getContract().getContractId() : null,
                record.getInsuredPersonId(),
                record.getPolicyholderUserId(),
                record.getProductType(),
                record.getExperienceDate(),
                record.getClaimAmount(),
                record.getNMedicalServices(),
                record.getHadClaimOrService(),
                record.getSeverityLevel(),
                record.getSource(),
                record.getImportedAt()
        );
    }
}
