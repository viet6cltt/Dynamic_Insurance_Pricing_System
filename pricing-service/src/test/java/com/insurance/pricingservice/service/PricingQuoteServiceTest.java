package com.insurance.pricingservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.insurance.pricingservice.dto.*;
import com.insurance.pricingservice.model.PremiumQuote;
import com.insurance.pricingservice.model.PricingExplanation;
import com.insurance.pricingservice.repository.PremiumQuoteRepository;
import com.insurance.pricingservice.repository.PricingAuditLogRepository;
import com.insurance.pricingservice.repository.PricingExplanationRepository;
import com.insurance.pricingservice.repository.PricingInputSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PricingQuoteServiceTest {

    @Mock
    private PremiumQuoteRepository quoteRepository;

    @Mock
    private PricingInputSnapshotRepository snapshotRepository;

    @Mock
    private PricingExplanationRepository explanationRepository;

    @Mock
    private PricingAuditLogRepository auditLogRepository;

    @Mock
    private ProductServiceClient productServiceClient;

    @Mock
    private UserServiceClient userServiceClient;

    @Mock
    private ApplicationPolicyServiceClient applicationPolicyServiceClient;

    @Mock
    private AiModelServiceClient aiModelServiceClient;

    @Mock
    private PricingOutboxService pricingOutboxService;

    private PricingQuoteService service;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        service = new PricingQuoteService(
                quoteRepository,
                snapshotRepository,
                explanationRepository,
                auditLogRepository,
                productServiceClient,
                userServiceClient,
                applicationPolicyServiceClient,
                aiModelServiceClient,
                new RatingEngine(),
                new PricingEventFactory(objectMapper),
                pricingOutboxService,
                objectMapper);
        ReflectionTestUtils.setField(service, "vndPerEur", 27000.0);
    }

    @Test
    void createQuoteConvertsAiMoneyOutputsToVnd() {
        UUID buyerAuthUserId = UUID.randomUUID();
        UUID buyerUserId = UUID.randomUUID();
        UUID insuredPersonId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID coveragePlanId = UUID.randomUUID();

        when(userServiceClient.getUserProfileByAuthUserId(buyerAuthUserId)).thenReturn(new UserProfileResponse(
                buyerUserId, buyerAuthUserId, "Buyer", "0900", "buyer@example.com", "1990-01-01",
                "MALE", "ID-1", null, "ACTIVE"));
        when(userServiceClient.getInsuredPersonById(insuredPersonId)).thenReturn(new InsuredPersonResponse(
                insuredPersonId, buyerUserId, "Nguyen", "A", "1990-01-01", "MALE",
                "SELF", "0900", "buyer@example.com", "ACTIVE"));
        when(productServiceClient.getInternalCoveragePlan(coveragePlanId)).thenReturn(new InternalCoveragePlanResponse(
                coveragePlanId, productId, "HEALTH", "Basic", new BigDecimal("100000000.00"),
                new BigDecimal("0.2000"), false, "ACTIVE"));
        when(applicationPolicyServiceClient.getClaimHistorySummary(insuredPersonId, "HEALTH"))
                .thenReturn(new ClaimHistorySummaryResponse(
                        0, 0.0, 5, 0.0, 0.0, 0.0,
                        false, true, 0.0, 0.0, 0, 0));
        when(aiModelServiceClient.predictHealthPricing(any())).thenReturn(new HealthPricingPredictionResponse(
                new BigDecimal("4.606232643127441"),
                new BigDecimal("39.58450994047385"),
                new BigDecimal("182.33546185001333"),
                "LOW",
                "frequency-v1",
                "severity-v1",
                null,
                null));
        when(quoteRepository.save(any(PremiumQuote.class))).thenAnswer(invocation -> {
            PremiumQuote saved = invocation.getArgument(0);
            saved.setQuoteId(UUID.randomUUID());
            return saved;
        });

        var response = service.createQuote(buyerAuthUserId, new CreateQuoteRequest(
                insuredPersonId,
                productId,
                coveragePlanId,
                new ObjectMapper().createObjectNode()
                        .put("bmi", 22)
                        .put("smoker", "no")
                        .put("bloodPressure", 120)
                        .put("exerciseFrequency", "weekly")
                        .put("preExistingCondition", false),
                false));

        assertEquals(new BigDecimal("1068781.77"), response.predictedAverageSeverity());
        assertEquals(new BigDecimal("4923057.86"), response.purePremium());
        assertEquals(new BigDecimal("5907669.43"), response.finalPremium());
        verify(pricingOutboxService).enqueue(any());
    }

    @Test
    void getQuoteByIdThrowsWhenMissing() {
        UUID quoteId = UUID.randomUUID();
        when(quoteRepository.findById(quoteId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.getQuoteById(quoteId));
    }

    @Test
    void getMyQuotesResolvesBuyerProfileAndMapsExplanations() {
        UUID authUserId = UUID.randomUUID();
        UUID buyerUserId = UUID.randomUUID();
        PremiumQuote quote = quote("GENERATED", Instant.now().plusSeconds(3600));
        quote.setBuyerUserId(buyerUserId);
        PricingExplanation explanation = PricingExplanation.builder()
                .quote(quote)
                .explanationMethod("frequency_severity")
                .approximate(false)
                .build();
        when(userServiceClient.getUserProfileByAuthUserId(authUserId)).thenReturn(new UserProfileResponse(
                buyerUserId, authUserId, "Buyer", "0900", "buyer@example.com", "1990-01-01",
                "MALE", "ID-1", null, "ACTIVE"));
        when(quoteRepository.findByBuyerUserIdOrderByCreatedAtDesc(buyerUserId)).thenReturn(List.of(quote));
        when(explanationRepository.findByQuoteQuoteId(quote.getQuoteId())).thenReturn(Optional.of(explanation));

        var response = service.getMyQuotes(authUserId);

        assertEquals(1, response.size());
        assertEquals("frequency_severity", response.getFirst().explanation().explanationMethod());
    }

    @Test
    void getMyQuotesRejectsMissingBuyerProfile() {
        UUID authUserId = UUID.randomUUID();
        when(userServiceClient.getUserProfileByAuthUserId(authUserId)).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () -> service.getMyQuotes(authUserId));
    }

    @Test
    void acceptQuoteMovesGeneratedQuoteToAcceptedAndEnqueuesEvent() {
        PremiumQuote quote = quote("GENERATED", Instant.now().plusSeconds(3600));
        when(quoteRepository.findById(quote.getQuoteId())).thenReturn(Optional.of(quote));
        when(quoteRepository.save(quote)).thenReturn(quote);
        when(explanationRepository.findByQuoteQuoteId(quote.getQuoteId())).thenReturn(Optional.empty());

        var response = service.acceptQuote(quote.getQuoteId());

        assertEquals("ACCEPTED", response.status());
        verify(pricingOutboxService).enqueue(any());
    }

    @Test
    void acceptQuoteExpiresExpiredGeneratedQuote() {
        PremiumQuote quote = quote("GENERATED", Instant.now().minusSeconds(10));
        when(quoteRepository.findById(quote.getQuoteId())).thenReturn(Optional.of(quote));
        when(quoteRepository.save(quote)).thenReturn(quote);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> service.acceptQuote(quote.getQuoteId()));

        assertEquals("Quote has expired", exception.getMessage());
        assertEquals("EXPIRED", quote.getStatus());
        verify(pricingOutboxService).enqueue(any());
    }

    @Test
    void markQuoteUsedAcceptsAcceptedQuote() {
        PremiumQuote quote = quote("ACCEPTED", Instant.now().plusSeconds(3600));
        when(quoteRepository.findById(quote.getQuoteId())).thenReturn(Optional.of(quote));
        when(quoteRepository.save(quote)).thenReturn(quote);
        when(explanationRepository.findByQuoteQuoteId(quote.getQuoteId())).thenReturn(Optional.empty());

        var response = service.markQuoteUsed(quote.getQuoteId());

        assertEquals("USED", response.status());
    }

    @Test
    void validateQuoteReturnsNotFoundForMissingQuote() {
        UUID quoteId = UUID.randomUUID();
        when(quoteRepository.findById(quoteId)).thenReturn(Optional.empty());

        var response = service.validateQuote(quoteId, new ValidateQuoteRequest(null, null, null));

        assertFalse(response.valid());
        assertEquals("NOT_FOUND", response.status());
    }

    @Test
    void validateQuoteFlagsBuyerInsuredAndPlanMismatches() {
        PremiumQuote quote = quote("GENERATED", Instant.now().plusSeconds(3600));
        when(quoteRepository.findById(quote.getQuoteId())).thenReturn(Optional.of(quote));

        var response = service.validateQuote(quote.getQuoteId(), new ValidateQuoteRequest(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()));

        assertFalse(response.valid());
        assertEquals("GENERATED", response.status());
    }

    @Test
    void validateQuoteKeepsValidGeneratedQuote() {
        PremiumQuote quote = quote("GENERATED", Instant.now().plusSeconds(3600));
        when(quoteRepository.findById(quote.getQuoteId())).thenReturn(Optional.of(quote));

        var response = service.validateQuote(quote.getQuoteId(), new ValidateQuoteRequest(
                quote.getBuyerUserId(), quote.getInsuredPersonId(), quote.getCoveragePlanId()));

        assertTrue(response.valid());
        assertEquals(quote.getFinalPremium(), response.finalPremium());
    }

    private PremiumQuote quote(String status, Instant expiredAt) {
        return PremiumQuote.builder()
                .quoteId(UUID.randomUUID())
                .buyerUserId(UUID.randomUUID())
                .insuredPersonId(UUID.randomUUID())
                .productId(UUID.randomUUID())
                .coveragePlanId(UUID.randomUUID())
                .productType("HEALTH")
                .planName("Standard Gold Plan")
                .sumInsured(new BigDecimal("150000000.00"))
                .predictedFrequency(new BigDecimal("1.000000"))
                .predictedSeverity(new BigDecimal("2500000.00"))
                .purePremium(new BigDecimal("2500000.00"))
                .loadingRate(new BigDecimal("0.2000"))
                .finalPremium(new BigDecimal("3000000.00"))
                .riskLevel("MEDIUM")
                .frequencyModelVersion("frequency-v1")
                .severityModelVersion("severity-v1")
                .status(status)
                .expiredAt(expiredAt)
                .build();
    }
}
