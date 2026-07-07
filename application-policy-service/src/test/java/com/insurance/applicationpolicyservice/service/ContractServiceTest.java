package com.insurance.applicationpolicyservice.service;

import com.insurance.applicationpolicyservice.dto.ContractStatus;
import com.insurance.applicationpolicyservice.dto.CreateContractRequest;
import com.insurance.applicationpolicyservice.dto.PaymentResponse;
import com.insurance.applicationpolicyservice.dto.PolicyExperienceSummaryResponse;
import com.insurance.applicationpolicyservice.dto.QuoteResponse;
import com.insurance.applicationpolicyservice.dto.ValidateQuoteResponse;
import com.insurance.applicationpolicyservice.model.InsuranceContract;
import com.insurance.applicationpolicyservice.model.PolicyExperienceSummary;
import com.insurance.applicationpolicyservice.repository.ClaimExperienceRecordRepository;
import com.insurance.applicationpolicyservice.repository.InsuranceContractRepository;
import com.insurance.applicationpolicyservice.repository.PolicyExperienceSummaryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContractServiceTest {

    @Mock
    private InsuranceContractRepository contractRepository;

    @Mock
    private PolicyExperienceSummaryRepository summaryRepository;

    @Mock
    private ClaimExperienceRecordRepository claimExperienceRecordRepository;

    @Mock
    private PricingServiceClient pricingServiceClient;

    @Mock
    private PaymentServiceClient paymentServiceClient;

    private ContractService service;

    @BeforeEach
    void setUp() {
        service = new ContractService(
                contractRepository,
                summaryRepository,
                claimExperienceRecordRepository,
                pricingServiceClient,
                paymentServiceClient);
    }

    @Test
    void createContractValidatesQuoteCreatesPaymentAndMarksQuoteUsed() {
        UUID buyerUserId = UUID.randomUUID();
        UUID insuredPersonId = UUID.randomUUID();
        UUID quoteId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID coveragePlanId = UUID.randomUUID();
        CreateContractRequest request = new CreateContractRequest(
                quoteId, insuredPersonId, productId, coveragePlanId, "SUCCESS");
        when(pricingServiceClient.validateQuote(eq(quoteId), any())).thenReturn(validQuote(
                quoteId, insuredPersonId, productId, coveragePlanId));
        when(contractRepository.existsByInsuredPersonIdAndProductId(insuredPersonId, productId)).thenReturn(false);
        when(contractRepository.countByInsuredPersonIdAndProductType(insuredPersonId, "HEALTH")).thenReturn(0L);
        when(contractRepository.save(any(InsuranceContract.class))).thenAnswer(invocation -> {
            InsuranceContract contract = invocation.getArgument(0);
            if (contract.getContractId() == null) {
                contract.setContractId(UUID.randomUUID());
            }
            return contract;
        });
        when(paymentServiceClient.createMockPayment(any(), any(), any())).thenAnswer(invocation ->
                new PaymentResponse(UUID.randomUUID(), invocation.getArgument(0,
                        com.insurance.applicationpolicyservice.dto.CreateMockPaymentRequest.class).contractId(),
                        quoteId, buyerUserId, new BigDecimal("1200000.00"), "VND", "MOCK",
                        "MOCK_PROVIDER", "SUCCESS", Instant.now(), Instant.now(), null, null,
                        null, Instant.now(), Instant.now()));
        when(pricingServiceClient.markQuoteUsed(quoteId)).thenReturn(new QuoteResponse(
                quoteId, buyerUserId, insuredPersonId, productId, coveragePlanId, "HEALTH",
                "Gold", new BigDecimal("100000000.00"), BigDecimal.ONE, new BigDecimal("1000000.00"),
                new BigDecimal("1000000.00"), new BigDecimal("0.2000"), new BigDecimal("1200000.00"),
                "freq-v1", "sev-v1", "LOW", "USED", Instant.now(), Instant.now()));

        var response = service.createContract(buyerUserId, request);

        assertEquals("PAYMENT_PENDING", response.status());
        assertEquals("SUCCESS", response.paymentStatus());
        assertEquals(new BigDecimal("1200000.00"), response.quotedPremium());
        verify(paymentServiceClient).createMockPayment(any(), any(), any());
        verify(pricingServiceClient).markQuoteUsed(quoteId);
    }

    @Test
    void createContractRejectsInvalidQuote() {
        UUID quoteId = UUID.randomUUID();
        when(pricingServiceClient.validateQuote(eq(quoteId), any()))
                .thenReturn(new ValidateQuoteResponse(false, quoteId, null, null, null, null,
                        null, null, null, "EXPIRED", Instant.now()));

        assertThrows(IllegalArgumentException.class, () -> service.createContract(UUID.randomUUID(),
                new CreateContractRequest(quoteId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null)));
    }

    @Test
    void createContractRejectsExistingContractForSameProduct() {
        UUID quoteId = UUID.randomUUID();
        UUID insuredPersonId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID coveragePlanId = UUID.randomUUID();
        when(pricingServiceClient.validateQuote(eq(quoteId), any()))
                .thenReturn(validQuote(quoteId, insuredPersonId, productId, coveragePlanId));
        when(contractRepository.existsByInsuredPersonIdAndProductId(insuredPersonId, productId)).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> service.createContract(UUID.randomUUID(),
                new CreateContractRequest(quoteId, insuredPersonId, productId, coveragePlanId, null)));
    }

    @Test
    void payContractReturnsActiveContractWithoutCallingPaymentService() {
        UUID buyerUserId = UUID.randomUUID();
        InsuranceContract contract = contract(buyerUserId, ContractStatus.ACTIVE);
        when(contractRepository.findByIdForUpdate(contract.getContractId())).thenReturn(Optional.of(contract));

        var response = service.payContract(buyerUserId, contract.getContractId());

        assertEquals("ACTIVE", response.status());
    }

    @Test
    void payContractRejectsContractOwnedByAnotherUser() {
        InsuranceContract contract = contract(UUID.randomUUID(), ContractStatus.PAYMENT_PENDING);
        when(contractRepository.findByIdForUpdate(contract.getContractId())).thenReturn(Optional.of(contract));

        assertThrows(IllegalArgumentException.class,
                () -> service.payContract(UUID.randomUUID(), contract.getContractId()));
    }

    @Test
    void payContractCreatesCustomerPaymentForFailedContract() {
        UUID buyerUserId = UUID.randomUUID();
        InsuranceContract contract = contract(buyerUserId, ContractStatus.PAYMENT_FAILED);
        when(contractRepository.findByIdForUpdate(contract.getContractId())).thenReturn(Optional.of(contract));
        when(paymentServiceClient.createMockPayment(any(), any(), any())).thenAnswer(invocation ->
                new PaymentResponse(UUID.randomUUID(), contract.getContractId(), contract.getQuoteId(),
                        buyerUserId, contract.getQuotedPremium(), "VND", "CUSTOMER_PAYMENT",
                        "MOCK_PROVIDER", "SUCCESS", Instant.now(), Instant.now(), null,
                        null, null, Instant.now(), Instant.now()));
        when(contractRepository.save(contract)).thenReturn(contract);

        var response = service.payContract(buyerUserId, contract.getContractId());

        assertEquals("PAYMENT_PENDING", response.status());
        assertEquals("SUCCESS", response.paymentStatus());
    }

    @Test
    void getExperienceSummaryReturnsExistingSummaryByInsuredPerson() {
        UUID customerId = UUID.randomUUID();
        PolicyExperienceSummary summary = summary(customerId, "HEALTH");
        when(summaryRepository.findByInsuredPersonIdAndProductType(customerId, "HEALTH"))
                .thenReturn(Optional.of(summary));

        PolicyExperienceSummaryResponse response = service.getExperienceSummary(customerId, "HEALTH");

        assertEquals(2, response.pastClaimCount());
        assertEquals(5000000.0, response.totalPastClaimAmount());
    }

    @Test
    void getExperienceSummaryFallsBackToDefaultForNewCustomer() {
        UUID customerId = UUID.randomUUID();
        when(summaryRepository.findByInsuredPersonIdAndProductType(customerId, "HEALTH")).thenReturn(Optional.empty());
        when(summaryRepository.findByPolicyholderUserIdAndProductType(customerId, "HEALTH")).thenReturn(Optional.empty());

        PolicyExperienceSummaryResponse response = service.getExperienceSummary(customerId, "HEALTH");

        assertEquals(0, response.pastClaimCount());
        assertEquals(true, response.claimFreePreviousYear());
    }

    @Test
    void updateExperienceSummaryCalculatesActiveCompletedCountsAndSeniority() {
        UUID insuredPersonId = UUID.randomUUID();
        InsuranceContract active = contract(UUID.randomUUID(), ContractStatus.ACTIVE);
        active.setInsuredPersonId(insuredPersonId);
        active.setProductType("HEALTH");
        active.setEffectiveDate(LocalDate.now().minusYears(2));
        InsuranceContract expired = contract(active.getApplicantUserId(), ContractStatus.EXPIRED);
        expired.setInsuredPersonId(insuredPersonId);
        expired.setProductType("HEALTH");
        expired.setEffectiveDate(LocalDate.now().minusYears(1));
        when(contractRepository.findByInsuredPersonIdAndProductType(insuredPersonId, "HEALTH"))
                .thenReturn(List.of(active, expired));
        when(summaryRepository.findByInsuredPersonIdAndProductType(insuredPersonId, "HEALTH"))
                .thenReturn(Optional.empty());

        service.updateExperienceSummary(active);

        ArgumentCaptor<PolicyExperienceSummary> captor = ArgumentCaptor.forClass(PolicyExperienceSummary.class);
        verify(summaryRepository).save(captor.capture());
        assertEquals(1, captor.getValue().getActivePolicyCount());
        assertEquals(1, captor.getValue().getCompletedPolicyCount());
        assertEquals(new BigDecimal("2.00"), captor.getValue().getSeniorityInsured());
    }

    private ValidateQuoteResponse validQuote(UUID quoteId, UUID insuredPersonId, UUID productId, UUID coveragePlanId) {
        return new ValidateQuoteResponse(
                true,
                quoteId,
                UUID.randomUUID(),
                insuredPersonId,
                productId,
                coveragePlanId,
                new BigDecimal("1000000.00"),
                new BigDecimal("0.2000"),
                new BigDecimal("1200000.00"),
                "GENERATED",
                Instant.now().plusSeconds(3600));
    }

    private InsuranceContract contract(UUID buyerUserId, ContractStatus status) {
        return InsuranceContract.builder()
                .contractId(UUID.randomUUID())
                .applicantUserId(buyerUserId)
                .insuredPersonId(UUID.randomUUID())
                .quoteId(UUID.randomUUID())
                .productId(UUID.randomUUID())
                .coveragePlanId(UUID.randomUUID())
                .productType("HEALTH")
                .typePolicy("STANDARD")
                .reimbursement("FULL")
                .distributionChannel("DIRECT")
                .purePremium(new BigDecimal("1000000.00"))
                .loadingRate(new BigDecimal("0.2000"))
                .quotedPremium(new BigDecimal("1200000.00"))
                .sumInsured(new BigDecimal("100000000.00"))
                .status(status)
                .submittedAt(Instant.now())
                .newBusiness(true)
                .policyYear(1)
                .build();
    }

    private PolicyExperienceSummary summary(UUID customerId, String productType) {
        return PolicyExperienceSummary.builder()
                .insuredPersonId(customerId)
                .policyholderUserId(customerId)
                .productType(productType)
                .prevCostClaimsYear(new BigDecimal("1000000.00"))
                .prevNMedicalServices(3)
                .prevHadClaimOrService(true)
                .claimFreePreviousYear(false)
                .totalPastClaimAmount(new BigDecimal("5000000.00"))
                .pastClaimCount(2)
                .claimFreeYears(1)
                .recencyWeightedClaimScore(new BigDecimal("0.75"))
                .seniorityInsured(new BigDecimal("2.50"))
                .activePolicyCount(1)
                .completedPolicyCount(1)
                .build();
    }
}
