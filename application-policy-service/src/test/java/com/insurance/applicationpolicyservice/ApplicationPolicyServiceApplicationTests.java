package com.insurance.applicationpolicyservice;

import com.insurance.applicationpolicyservice.dto.*;
import com.insurance.applicationpolicyservice.model.InsuranceContract;
import com.insurance.applicationpolicyservice.model.PolicyExperienceSummary;
import com.insurance.applicationpolicyservice.repository.OutboxEventRepository;
import com.insurance.applicationpolicyservice.repository.InsuranceContractRepository;
import com.insurance.applicationpolicyservice.repository.PolicyExperienceSummaryRepository;
import com.insurance.applicationpolicyservice.repository.ProcessedEventRepository;
import com.insurance.applicationpolicyservice.service.ContractService;
import com.insurance.applicationpolicyservice.service.ContractExpirationService;
import com.insurance.applicationpolicyservice.service.PaymentEventProcessor;
import com.insurance.applicationpolicyservice.service.PaymentServiceClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.insurance.applicationpolicyservice.service.PricingServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class ApplicationPolicyServiceApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ContractService contractService;

    @Autowired
    private InsuranceContractRepository contractRepository;

    @Autowired
    private PolicyExperienceSummaryRepository summaryRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private PaymentEventProcessor paymentEventProcessor;

    @Autowired
    private ContractExpirationService contractExpirationService;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PricingServiceClient pricingServiceClient;

    @MockitoBean
    private PaymentServiceClient paymentServiceClient;

    private UUID buyerUserId;
    private UUID insuredPersonId;
    private UUID quoteId;
    private UUID productId;
    private UUID coveragePlanId;

    @BeforeEach
    public void setUp() {
        outboxEventRepository.deleteAll();
        processedEventRepository.deleteAll();
        summaryRepository.deleteAll();
        contractRepository.deleteAll();

        buyerUserId = UUID.randomUUID();
        insuredPersonId = UUID.randomUUID();
        quoteId = UUID.randomUUID();
        productId = UUID.randomUUID();
        coveragePlanId = UUID.randomUUID();

        // Stub spring security authentication context
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                buyerUserId.toString(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    public void contextLoads() {
        assertNotNull(contractService);
    }

    @Test
    public void testCreateContractSuccess() throws Exception {
        // Stub Pricing Service Calls
        ValidateQuoteResponse validateResp = new ValidateQuoteResponse(
                true, quoteId, buyerUserId, insuredPersonId, productId, coveragePlanId,
                BigDecimal.valueOf(100.0), BigDecimal.valueOf(120.0), "GENERATED", Instant.now().plusSeconds(3600)
        );
        Mockito.when(pricingServiceClient.validateQuote(eq(quoteId), any(ValidateQuoteRequest.class)))
                .thenReturn(validateResp);

        QuoteResponse quoteResp = new QuoteResponse(
                quoteId, buyerUserId, insuredPersonId, productId, coveragePlanId, "HEALTH", "STANDARD_PLAN",
                BigDecimal.valueOf(100.0), BigDecimal.valueOf(1000000.0), BigDecimal.valueOf(50.0), BigDecimal.valueOf(40.0), BigDecimal.valueOf(30.0),
                BigDecimal.valueOf(1.1), BigDecimal.valueOf(1.2), BigDecimal.valueOf(1.32), BigDecimal.valueOf(120.0),
                "STANDARD", "USED", Instant.now(), Instant.now().plusSeconds(3600)
        );
        Mockito.when(pricingServiceClient.markQuoteUsed(eq(quoteId))).thenReturn(quoteResp);
        UUID paymentId = UUID.randomUUID();
        Mockito.when(paymentServiceClient.createMockPayment(any(CreateMockPaymentRequest.class), any(String.class), any(String.class)))
                .thenReturn(new PaymentResponse(
                        paymentId,
                        null,
                        quoteId,
                        buyerUserId,
                        BigDecimal.valueOf(120.0),
                        "VND",
                        "MOCK",
                        "MOCK_PROVIDER",
                        "SUCCESS",
                        Instant.now().plusSeconds(900),
                        Instant.now(),
                        null,
                        null,
                        null,
                        Instant.now(),
                        Instant.now()
                ));

        // Perform call
        String requestJson = String.format("""
                {
                    "quoteId": "%s",
                    "insuredPersonId": "%s",
                    "productId": "%s",
                    "coveragePlanId": "%s",
                    "simulatePaymentResult": "SUCCESS"
                }
                """, quoteId, insuredPersonId, productId, coveragePlanId);

        mockMvc.perform(post("/contracts")
                        .header("X-USER-ID", buyerUserId.toString())
                        .header("X-USER-ROLE", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated());

        // Verify saved contract
        List<InsuranceContract> contracts = contractRepository.findByApplicantUserIdOrderByCreatedAtDesc(buyerUserId);
        assertEquals(1, contracts.size());
        InsuranceContract saved = contracts.get(0);
        assertEquals(quoteId, saved.getQuoteId());
        assertEquals("PAYMENT_PENDING", saved.getStatus().name());
        assertEquals("HEALTH", saved.getProductType());
        assertEquals(paymentId, saved.getPaymentId());
        assertEquals("SUCCESS", saved.getPaymentStatus());
        assertTrue(saved.getNewBusiness());
    }

    @Test
    public void testPaymentSucceededEventActivatesContract() {
        InsuranceContract contract = savePendingContract();
        UUID paymentId = UUID.randomUUID();
        EventEnvelope event = paymentEvent("payment.succeeded", contract, paymentId, "SUCCESS");

        paymentEventProcessor.process(event);
        paymentEventProcessor.process(event);

        InsuranceContract updated = contractRepository.findById(contract.getContractId()).orElseThrow();
        assertEquals(ContractStatus.ACTIVE, updated.getStatus());
        assertEquals(paymentId, updated.getPaymentId());
        assertEquals("SUCCESS", updated.getPaymentStatus());
        assertNotNull(updated.getIssuedAt());
        assertEquals(LocalDate.now().plusYears(1).minusDays(1), updated.getExpiryDate());
        assertTrue(processedEventRepository.existsByEventIdAndConsumerName(event.eventId(), "policy-service-payment-consumer"));
        assertEquals(1, outboxEventRepository.findByEventType("policy.issued").size());

        PolicyExperienceSummary summary = summaryRepository
                .findByInsuredPersonIdAndProductType(contract.getInsuredPersonId(), contract.getProductType())
                .orElseThrow();
        assertEquals(1, summary.getActivePolicyCount());
    }

    @Test
    public void testPaymentFailedEventMarksContractPaymentFailed() {
        InsuranceContract contract = savePendingContract();
        UUID paymentId = UUID.randomUUID();
        EventEnvelope event = paymentEvent("payment.failed", contract, paymentId, "FAILED");

        paymentEventProcessor.process(event);

        InsuranceContract updated = contractRepository.findById(contract.getContractId()).orElseThrow();
        assertEquals(ContractStatus.PAYMENT_FAILED, updated.getStatus());
        assertEquals(paymentId, updated.getPaymentId());
        assertEquals("FAILED", updated.getPaymentStatus());
        assertEquals(1, outboxEventRepository.findByEventType("contract.payment_failed").size());
    }

    @Test
    public void testExpiredActiveContractUpdatesSummaryAndPublishesEvent() {
        UUID sharedInsuredPersonId = UUID.randomUUID();
        InsuranceContract expiredActive = saveActiveContract(
                sharedInsuredPersonId,
                LocalDate.now().minusYears(1),
                LocalDate.now().minusDays(1)
        );
        saveActiveContract(
                sharedInsuredPersonId,
                LocalDate.now(),
                LocalDate.now().plusMonths(6)
        );

        contractService.updateExperienceSummary(expiredActive);
        PolicyExperienceSummary before = summaryRepository
                .findByInsuredPersonIdAndProductType(sharedInsuredPersonId, "HEALTH")
                .orElseThrow();
        assertEquals(2, before.getActivePolicyCount());
        assertEquals(0, before.getCompletedPolicyCount());

        int expiredCount = contractExpirationService.expireActiveContracts();

        assertEquals(1, expiredCount);
        InsuranceContract updated = contractRepository.findById(expiredActive.getContractId()).orElseThrow();
        assertEquals(ContractStatus.EXPIRED, updated.getStatus());

        PolicyExperienceSummary after = summaryRepository
                .findByInsuredPersonIdAndProductType(sharedInsuredPersonId, "HEALTH")
                .orElseThrow();
        assertEquals(1, after.getActivePolicyCount());
        assertEquals(1, after.getCompletedPolicyCount());
        assertEquals(1, outboxEventRepository.findByEventType("contract.expired").size());
    }

    @Test
    public void testGetClaimHistorySummarySuccess() throws Exception {
        UUID customerId = UUID.randomUUID();
        
        // Save mock policy experience summary
        PolicyExperienceSummary summary = PolicyExperienceSummary.builder()
                .insuredPersonId(customerId)
                .policyholderUserId(customerId)
                .productType("HEALTH")
                .pastClaimCount(3)
                .totalPastClaimAmount(BigDecimal.valueOf(1500.50))
                .claimFreeYears(2)
                .recencyWeightedClaimScore(BigDecimal.valueOf(0.85))
                .prevCostClaimsYear(BigDecimal.valueOf(500.0))
                .prevNMedicalServices(4)
                .prevHadClaimOrService(true)
                .claimFreePreviousYear(false)
                .seniorityInsured(BigDecimal.valueOf(1.5))
                .activePolicyCount(1)
                .completedPolicyCount(1)
                .build();
        
        summaryRepository.save(summary);

        // Execute GET API
        mockMvc.perform(get("/internal/customers/{customerId}/claim-history-summary", customerId)
                        .header("X-USER-ID", "SYSTEM")
                        .header("X-USER-ROLE", "SYSTEM")
                        .param("productType", "HEALTH")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pastClaimCount").value(3))
                .andExpect(jsonPath("$.totalPastClaimAmount").value(1500.50))
                .andExpect(jsonPath("$.claimFreeYears").value(2))
                .andExpect(jsonPath("$.recencyWeightedClaimScore").value(0.85))
                .andExpect(jsonPath("$.prevCostClaimsYear").value(500.0))
                .andExpect(jsonPath("$.prevNMedicalServices").value(4.0))
                .andExpect(jsonPath("$.prevHadClaimOrService").value(true))
                .andExpect(jsonPath("$.claimFreePreviousYear").value(false));
    }

    private InsuranceContract savePendingContract() {
        return contractRepository.save(InsuranceContract.builder()
                .applicantUserId(UUID.randomUUID())
                .insuredPersonId(UUID.randomUUID())
                .quoteId(UUID.randomUUID())
                .productId(UUID.randomUUID())
                .coveragePlanId(UUID.randomUUID())
                .productType("HEALTH")
                .typePolicy("STANDARD")
                .reimbursement("FULL")
                .distributionChannel("DIRECT")
                .basePremium(BigDecimal.valueOf(100))
                .quotedPremium(BigDecimal.valueOf(120))
                .sumInsured(BigDecimal.valueOf(1000000))
                .status(ContractStatus.PAYMENT_PENDING)
                .submittedAt(Instant.now())
                .newBusiness(true)
                .policyYear(1)
                .build());
    }

    private InsuranceContract saveActiveContract(UUID insuredPersonId, LocalDate effectiveDate, LocalDate expiryDate) {
        return contractRepository.save(InsuranceContract.builder()
                .applicantUserId(UUID.randomUUID())
                .insuredPersonId(insuredPersonId)
                .quoteId(UUID.randomUUID())
                .productId(UUID.randomUUID())
                .coveragePlanId(UUID.randomUUID())
                .productType("HEALTH")
                .typePolicy("STANDARD")
                .reimbursement("FULL")
                .distributionChannel("DIRECT")
                .basePremium(BigDecimal.valueOf(100))
                .quotedPremium(BigDecimal.valueOf(120))
                .sumInsured(BigDecimal.valueOf(1000000))
                .status(ContractStatus.ACTIVE)
                .submittedAt(Instant.now().minusSeconds(86400))
                .issuedAt(Instant.now().minusSeconds(86400))
                .effectiveDate(effectiveDate)
                .expiryDate(expiryDate)
                .exposureTime(BigDecimal.ONE)
                .newBusiness(true)
                .policyYear(1)
                .build());
    }

    private EventEnvelope paymentEvent(String eventType,
                                       InsuranceContract contract,
                                       UUID paymentId,
                                       String paymentStatus) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("paymentId", paymentId.toString());
        payload.put("contractId", contract.getContractId().toString());
        payload.put("quoteId", contract.getQuoteId().toString());
        payload.put("customerId", contract.getApplicantUserId().toString());
        payload.put("amount", contract.getQuotedPremium());
        payload.put("currency", "VND");
        payload.put("paymentMethod", "MOCK");
        payload.put("provider", "MOCK_PROVIDER");
        payload.put("status", paymentStatus);
        payload.put("expiresAt", Instant.now().plusSeconds(900).toString());

        return new EventEnvelope(
                UUID.randomUUID(),
                eventType,
                1,
                Instant.now(),
                "payment-service",
                "Payment",
                paymentId,
                UUID.randomUUID().toString(),
                null,
                payload
        );
    }
}
