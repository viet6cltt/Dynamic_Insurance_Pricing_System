package com.insurance.pricingservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.insurance.pricingservice.model.PremiumQuote;
import com.insurance.pricingservice.repository.PremiumQuoteRepository;
import com.insurance.pricingservice.repository.PricingAuditLogRepository;
import com.insurance.pricingservice.repository.PricingExplanationRepository;
import com.insurance.pricingservice.repository.PricingInputSnapshotRepository;
import com.insurance.pricingservice.service.AiModelServiceClient;
import com.insurance.pricingservice.service.ApplicationPolicyServiceClient;
import com.insurance.pricingservice.service.ProductServiceClient;
import com.insurance.pricingservice.service.PricingQuoteService;
import com.insurance.pricingservice.service.UserServiceClient;
import com.insurance.pricingservice.dto.*;
import com.insurance.pricingservice.dto.CreateQuoteRequest;
import com.insurance.pricingservice.dto.InternalCoveragePlanResponse;
import com.insurance.pricingservice.dto.InsuredPersonResponse;
import com.insurance.pricingservice.dto.UserProfileResponse;
import com.insurance.pricingservice.dto.ClaimHistorySummaryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
public class PricingQuoteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PricingQuoteService pricingQuoteService;

    @Autowired
    private PremiumQuoteRepository quoteRepository;

    @Autowired
    private PricingInputSnapshotRepository snapshotRepository;

    @Autowired
    private PricingExplanationRepository explanationRepository;

    @Autowired
    private PricingAuditLogRepository auditLogRepository;

    @MockitoBean
    private UserServiceClient userServiceClient;

    @MockitoBean
    private ProductServiceClient productServiceClient;

    @MockitoBean
    private ApplicationPolicyServiceClient applicationPolicyServiceClient;

    @MockitoBean
    private AiModelServiceClient aiModelServiceClient;

    @BeforeEach
    void cleanDatabase() {
        auditLogRepository.deleteAll();
        explanationRepository.deleteAll();
        snapshotRepository.deleteAll();
        quoteRepository.deleteAll();
    }

    @Test
    public void testCreateQuoteSuccess() throws Exception {
        UUID authUserId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID insuredPersonId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID coveragePlanId = UUID.randomUUID();

        // 1. Mock User Service
        UserProfileResponse buyerProfile = new UserProfileResponse(
                userId, authUserId, "Nguyen Van A", "0987654321", "buyer@gmail.com",
                "1990-01-01", "male", "123456789", "Ha Noi", "ACTIVE"
        );
        Mockito.when(userServiceClient.getUserProfileByAuthUserId(Mockito.any(UUID.class)))
                .thenReturn(buyerProfile);

        InsuredPersonResponse insuredPerson = new InsuredPersonResponse(
                insuredPersonId, userId, "Nguyen Van", "B", "1995-05-15",
                "male", "brother", "0912345678", "brother@gmail.com", "ACTIVE"
        );
        Mockito.when(userServiceClient.getInsuredPersonById(insuredPersonId))
                .thenReturn(insuredPerson);

        // Mock Policy Service
        Mockito.when(applicationPolicyServiceClient.getClaimHistorySummary(Mockito.any(UUID.class), Mockito.anyString()))
                .thenReturn(new ClaimHistorySummaryResponse(
                        2, 50000000.0, 1, 0.7, 25000000.0, 4.0, true, false, 2.0, 2.0, 1, 0
                ));

        // 2. Mock Product Service
        InternalCoveragePlanResponse coveragePlan = new InternalCoveragePlanResponse(
                coveragePlanId, productId, "HEALTH", "Premium Gold Plan",
                new BigDecimal("150000000.00"), new BigDecimal("0.2000"), true, "ACTIVE"
        );
        Mockito.when(productServiceClient.getInternalCoveragePlan(coveragePlanId))
                .thenReturn(coveragePlan);

        // 3. Mock AI Model Service
        ObjectNode frequencyExplanation = objectMapper.createObjectNode();
        frequencyExplanation.set("topFactors", objectMapper.createArrayNode());
        ObjectNode severityExplanation = objectMapper.createObjectNode();
        severityExplanation.set("topFactors", objectMapper.createArrayNode());
        HealthPricingPredictionResponse predictionResponse = new HealthPricingPredictionResponse(
                new BigDecimal("10.000000"),
                new BigDecimal("20.00"),
                new BigDecimal("200.00"),
                "HIGH",
                "frequency-v4",
                "severity-v1",
                frequencyExplanation,
                severityExplanation
        );
        Mockito.when(aiModelServiceClient.predictHealthPricing(any(HealthPricingPredictionRequest.class)))
                .thenReturn(predictionResponse);

        // Build sample input
        ObjectNode riskProfile = objectMapper.createObjectNode();
        riskProfile.put("bmi", 27.5);
        riskProfile.put("children", 1);
        riskProfile.put("smoker", true);
        riskProfile.put("bloodPressure", 130.0);
        riskProfile.put("exerciseFrequency", "LOW");
        riskProfile.put("preExistingCondition", false);
        riskProfile.put("occupationCode", "OFFICE_WORKER");

        CreateQuoteRequest request = new CreateQuoteRequest(
                insuredPersonId, productId, coveragePlanId, riskProfile, true
        );

        // Mock Security Context
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        authUserId.toString(), null, java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"))
                )
        );

        // Run MockMvc post request
        mockMvc.perform(post("/pricing/quotes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-USER-ID", authUserId.toString())
                        .header("X-USER-ROLE", "ROLE_USER")
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isCreated());

        PremiumQuote savedQuote = quoteRepository.findAll().getFirst();
        assertEquals("GENERATED", savedQuote.getStatus());
        assertEquals(new BigDecimal("5000000.00"), savedQuote.getPurePremium());
        assertEquals(new BigDecimal("6000000.00"), savedQuote.getFinalPremium());
        assertEquals(new BigDecimal("0.2000"), savedQuote.getLoadingRate());
        assertEquals(0.2, snapshotRepository.findAll().getFirst()
                .getCoveragePlanSnapshot()
                .get("loadingRate")
                .asDouble());

        var predictionCaptor = org.mockito.ArgumentCaptor.forClass(HealthPricingPredictionRequest.class);
        Mockito.verify(aiModelServiceClient, Mockito.times(1)).predictHealthPricing(predictionCaptor.capture());
        HealthPricingPredictionRequest actualRequest = predictionCaptor.getAllValues().getFirst();
        assertEquals(1000.0, actualRequest.portfolioProfile().prevCostClaimsYear());
        assertEquals(2000.0, actualRequest.historicalExperienceFeatures().totalPastClaimAmount());
        assertEquals("P", actualRequest.portfolioProfile().typeProduct());
        assertEquals("I", actualRequest.portfolioProfile().typePolicy());
        assertEquals("Yes", actualRequest.portfolioProfile().reimbursement());
        assertEquals("No", actualRequest.portfolioProfile().newBusiness());
        assertEquals("D", actualRequest.portfolioProfile().distributionChannel());
    }

    @Test
    public void testMarkQuoteUsedRejectsExpiredQuote() {
        PremiumQuote quote = quoteRepository.save(quote("GENERATED", Instant.now().minusSeconds(60)));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> pricingQuoteService.markQuoteUsed(quote.getQuoteId()));

        assertEquals("Quote has expired", exception.getMessage());
        assertEquals("EXPIRED", quoteRepository.findById(quote.getQuoteId()).orElseThrow().getStatus());
    }

    @Test
    public void testMarkQuoteUsedRejectsAlreadyUsedQuote() {
        PremiumQuote quote = quoteRepository.save(quote("USED", Instant.now().plusSeconds(3600)));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> pricingQuoteService.markQuoteUsed(quote.getQuoteId()));

        assertTrue(exception.getMessage().contains("Quote cannot be marked as used"));
        assertEquals("USED", quoteRepository.findById(quote.getQuoteId()).orElseThrow().getStatus());
    }

    private PremiumQuote quote(String status, Instant expiredAt) {
        return PremiumQuote.builder()
                .buyerUserId(UUID.randomUUID())
                .insuredPersonId(UUID.randomUUID())
                .productId(UUID.randomUUID())
                .coveragePlanId(UUID.randomUUID())
                .productType("HEALTH")
                .planName("Premium Gold Plan")
                .sumInsured(new BigDecimal("150000000.00"))
                .predictedFrequency(new BigDecimal("0.000000"))
                .predictedSeverity(new BigDecimal("0.00"))
                .purePremium(new BigDecimal("2500000.00"))
                .loadingRate(new BigDecimal("0.0000"))
                .finalPremium(new BigDecimal("2500000.00"))
                .riskLevel("MEDIUM")
                .frequencyModelVersion("test-frequency")
                .severityModelVersion("test-severity")
                .status(status)
                .expiredAt(expiredAt)
                .build();
    }
}
