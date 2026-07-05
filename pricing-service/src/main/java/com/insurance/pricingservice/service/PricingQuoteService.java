package com.insurance.pricingservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.insurance.pricingservice.dto.RiskLevel;
import com.insurance.pricingservice.model.PremiumQuote;
import com.insurance.pricingservice.model.PricingAuditLog;
import com.insurance.pricingservice.model.PricingExplanation;
import com.insurance.pricingservice.model.PricingInputSnapshot;
import com.insurance.pricingservice.repository.PremiumQuoteRepository;
import com.insurance.pricingservice.repository.PricingAuditLogRepository;
import com.insurance.pricingservice.repository.PricingExplanationRepository;
import com.insurance.pricingservice.repository.PricingInputSnapshotRepository;
import com.insurance.pricingservice.dto.*;
import com.insurance.pricingservice.dto.CreateQuoteRequest;
import com.insurance.pricingservice.dto.ValidateQuoteRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PricingQuoteService {

    private final PremiumQuoteRepository quoteRepository;
    private final PricingInputSnapshotRepository snapshotRepository;
    private final PricingExplanationRepository explanationRepository;
    private final PricingAuditLogRepository auditLogRepository;

    private final ProductServiceClient productServiceClient;
    private final UserServiceClient userServiceClient;
    private final ApplicationPolicyServiceClient applicationPolicyServiceClient;
    private final AiModelServiceClient aiModelServiceClient;

    private final RatingEngine ratingEngine;
    private final RiskLevelCalculator riskLevelCalculator;
    private final PricingEventFactory pricingEventFactory;
    private final PricingOutboxService pricingOutboxService;
    private final ObjectMapper objectMapper;

    @Value("${app.currency.vnd-per-eur:27000}")
    private double vndPerEur;

    @Value("${app.rating.health-risk-log-alpha:0.18}")
    private double healthRiskLogAlpha;

    @Value("${app.rating.health-factor-min:0.85}")
    private double healthFactorMin;

    @Value("${app.rating.health-factor-max:1.50}")
    private double healthFactorMax;

    @Value("${app.rating.portfolio-factor-min:0.85}")
    private double portfolioFactorMin;

    @Value("${app.rating.portfolio-factor-max:1.30}")
    private double portfolioFactorMax;

    @Value("${app.rating.portfolio-credibility-k:3.0}")
    private double portfolioCredibilityK;

    @Value("${app.rating.portfolio-neutral-prev-cost-claims-year-eur:222.2}")
    private double neutralPrevCostClaimsYearEur;

    @Value("${app.rating.portfolio-neutral-prev-n-medical-services:7.0}")
    private double neutralPrevNMedicalServices;

    @Value("${app.rating.portfolio-neutral-prev-had-claim-or-service:false}")
    private boolean neutralPrevHadClaimOrService;

    @Value("${app.rating.portfolio-neutral-claim-free-previous-year:false}")
    private boolean neutralClaimFreePreviousYear;

    @Transactional
    public QuoteResponse createQuote(UUID buyerAuthUserId, CreateQuoteRequest request) {
        log.info("Creating premium quote for buyer auth user ID: {}", buyerAuthUserId);

        // 1. Get buyer user profile
        UserProfileResponse buyerProfile = userServiceClient.getUserProfileByAuthUserId(buyerAuthUserId);
        if (buyerProfile == null) {
            throw new IllegalArgumentException("Buyer profile not found for auth ID: " + buyerAuthUserId);
        }

        // 2. Fetch insured person details
        InsuredPersonResponse insuredPerson = userServiceClient.getInsuredPersonById(request.insuredPersonId());
        if (insuredPerson == null) {
            throw new IllegalArgumentException("Insured person not found with ID: " + request.insuredPersonId());
        }

        // 3. Verify ownership
        if (!insuredPerson.userId().equals(buyerProfile.userId())) {
            throw new IllegalArgumentException(
                    "Unauthorized: Insured person does not belong to the current buyer profile");
        }

        // 4. Fetch coverage plan details from Product Service
        InternalCoveragePlanResponse coveragePlan = productServiceClient
                .getInternalCoveragePlan(request.coveragePlanId());
        if (coveragePlan == null) {
            throw new IllegalArgumentException("Coverage plan not found with ID: " + request.coveragePlanId());
        }
        if (!"ACTIVE".equals(coveragePlan.status())) {
            throw new IllegalArgumentException("Selected coverage plan is inactive");
        }

        // 5. Parse risk profile and handle field mismatches
        JsonNode riskNode = request.riskProfile();
        double age = calculateAge(insuredPerson.dateOfBirth(), riskNode);
        String gender = parseGender(insuredPerson.gender(), riskNode);
        double bmi = riskNode != null && riskNode.has("bmi") ? riskNode.get("bmi").asDouble() : 22.5;
        int children = riskNode != null && riskNode.has("children") ? riskNode.get("children").asInt() : 0;
        String smokerStr = parseSmoker(riskNode);
        double bloodPressure = riskNode != null && riskNode.has("bloodPressure")
                ? riskNode.get("bloodPressure").asDouble()
                : 120.0;
        String exerciseFrequency = riskNode != null && riskNode.has("exerciseFrequency")
                ? riskNode.get("exerciseFrequency").asText()
                : "MODERATE";
        boolean preExistingCondition = riskNode != null && riskNode.has("preExistingCondition")
                && riskNode.get("preExistingCondition").asBoolean();

        // 6. Resolve Occupation Risk using internal mapping service
        String occupationRiskLower = "low";
        if (riskNode != null && riskNode.has("occupationCode")) {
            String occupationCode = riskNode.get("occupationCode").asText();
            try {
                ResolveOccupationRiskResponse occupationResponse = productServiceClient
                        .resolveOccupationRisk(coveragePlan.productType(), occupationCode);
                if (occupationResponse != null && occupationResponse.riskLevel() != null) {
                    occupationRiskLower = occupationResponse.riskLevel().toLowerCase();
                }
            } catch (Exception e) {
                log.warn("Failed to resolve occupation risk for code: {}, defaulting to 'low'. Error: {}",
                        occupationCode, e.getMessage());
            }
        }

        // 7. Fetch Claim History from Policy service (with mock fallback)
        ClaimHistorySummaryResponse claimHistory = fetchClaimHistory(request.insuredPersonId(), coveragePlan.productType());
        double prevCostClaimsYearEur = vndToEur(claimHistory.prevCostClaimsYear());
        double totalPastClaimAmountEur = vndToEur(claimHistory.totalPastClaimAmount());

        // 8. Build prediction payload
        HealthRiskProfile healthRiskProfile = new HealthRiskProfile(
                age,
                gender,
                bmi,
                children,
                smokerStr,
                bloodPressure,
                exerciseFrequency,
                preExistingCondition,
                occupationRiskLower);

        PortfolioPricingProfile portfolioProfile = new PortfolioPricingProfile(
                gender,
                coveragePlan.productType(),
                "STANDARD",
                "FULL",
                1.0,
                1.0,
                "YES",
                "DIRECT",
                prevCostClaimsYearEur,
                claimHistory.prevNMedicalServices(),
                claimHistory.prevHadClaimOrService(),
                claimHistory.claimFreePreviousYear());

        HistoricalExperienceFeatures historicalExperience = new HistoricalExperienceFeatures(
                claimHistory.pastClaimCount(),
                totalPastClaimAmountEur,
                claimHistory.claimFreeYears(),
                claimHistory.recencyWeightedClaimScore());

        HealthPricingPredictionRequest predictionRequest = new HealthPricingPredictionRequest(
                "HEALTH",
                healthRiskProfile,
                portfolioProfile,
                historicalExperience);

        HealthPricingPredictionRequest neutralPredictionRequest = new HealthPricingPredictionRequest(
                "HEALTH",
                healthRiskProfile,
                neutralPortfolioProfile(gender, coveragePlan.productType()),
                neutralHistoricalExperience());

        // 9. Invoke AI Model Service
        HealthPricingPredictionResponse predictionResponse = null;
        HealthPricingPredictionResponse neutralPredictionResponse = null;
        String predictionStatus = "SUCCESS";
        String errorMessage = null;
        try {
            predictionResponse = aiModelServiceClient.predictHealthPricing(predictionRequest);
            if (hasPortfolioExperience(claimHistory)) {
                neutralPredictionResponse = aiModelServiceClient.predictHealthPricing(neutralPredictionRequest);
            }
        } catch (Exception e) {
            predictionStatus = "FAILED";
            errorMessage = e.getMessage();
            log.error("AI Model Service call failed: {}", e.getMessage(), e);
        }

        // Initialize variables for factors
        BigDecimal portfolioFactor = BigDecimal.ONE;
        BigDecimal rawPortfolioFactor = BigDecimal.ONE;
        BigDecimal healthFactor = BigDecimal.ONE;
        BigDecimal rawHealthFactor = BigDecimal.ONE;
        BigDecimal predictedAnnualClaimCost = BigDecimal.ZERO;
        BigDecimal predictedHealthCost = BigDecimal.ZERO;
        BigDecimal baselineHealthCost = BigDecimal.ZERO;
        String portfolioModelVersion = "fallback";
        String healthModelVersion = "fallback";

        JsonNode portfolioExplanation = null;
        JsonNode healthExplanation = null;
        JsonNode topRiskFactors = null;
        JsonNode shapValues = null;
        String explanationMethod = "none";
        boolean approximate = false;

        if ("SUCCESS".equals(predictionStatus) && predictionResponse != null) {
            if (predictionResponse.portfolioModel() != null
                    && "configured".equals(predictionResponse.portfolioModel().status())) {
                portfolioModelVersion = predictionResponse.portfolioModel().modelVersion();
                predictedAnnualClaimCost = predictionResponse.portfolioModel().predictedAnnualClaimCost();
                rawPortfolioFactor = calculateRawExperienceFactor(predictionResponse, neutralPredictionResponse, claimHistory);
                portfolioFactor = calculatePortfolioExperienceFactor(rawPortfolioFactor, claimHistory);
                portfolioExplanation = predictionResponse.portfolioModel().portfolioModelExplanation();
            }

            if (predictionResponse.healthRiskModel() != null
                    && "configured".equals(predictionResponse.healthRiskModel().status())) {
                healthModelVersion = predictionResponse.healthRiskModel().modelVersion();
                predictedHealthCost = predictionResponse.healthRiskModel().predictedHealthCost();
                baselineHealthCost = predictionResponse.healthRiskModel().baselineHealthCost();
                rawHealthFactor = predictionResponse.healthRiskModel().rawHealthRiskFactor();
                healthFactor = calculateHealthRatingFactor(rawHealthFactor);
                healthExplanation = predictionResponse.healthRiskModel().healthRiskExplanation();

                if (predictionResponse.healthRiskModel().healthRiskExplanation() != null) {
                    JsonNode expl = predictionResponse.healthRiskModel().healthRiskExplanation();
                    topRiskFactors = expl.get("topRiskFactors");
                    shapValues = expl.get("shapValues");
                    explanationMethod = expl.has("method") ? expl.get("method").asText() : "shap";
                    approximate = expl.has("approximate") && expl.get("approximate").asBoolean();
                }
            }
        }

        topRiskFactors = mergeExplanationItems(portfolioExplanation, healthExplanation, "topRiskFactors", 8);
        shapValues = mergeExplanationItems(portfolioExplanation, healthExplanation, "shapValues", 10);
        if (topRiskFactors != null && topRiskFactors.size() > 0) {
            explanationMethod = "combined_model_explanation";
        }

        // 10. Run Rating Engine
        BigDecimal underwritingAdjustment = BigDecimal.ONE;
        BigDecimal businessAdjustment = BigDecimal.ONE;
        BigDecimal finalPremium = ratingEngine.calculateFinalPremium(
                coveragePlan.basePremium(),
                portfolioFactor,
                healthFactor,
                underwritingAdjustment,
                businessAdjustment);

        // 11. Run Combined Risk Level Calculation
        RiskLevel combinedRisk = riskLevelCalculator.calculateCombinedRiskLevel(portfolioFactor, healthFactor);

        // 12. Save Quote aggregate
        Instant now = Instant.now();
        Instant expiredAt = now.plus(7, ChronoUnit.DAYS); // default 7 days validity

        PremiumQuote quote = PremiumQuote.builder()
                .buyerUserId(buyerProfile.userId())
                .insuredPersonId(request.insuredPersonId())
                .productId(coveragePlan.productId())
                .coveragePlanId(request.coveragePlanId())
                .productType(coveragePlan.productType())
                .planName(coveragePlan.planName())
                .basePremium(coveragePlan.basePremium())
                .sumInsured(coveragePlan.sumInsured())
                .predictedAnnualClaimCost(predictedAnnualClaimCost)
                .predictedHealthCost(predictedHealthCost)
                .baselineHealthCost(baselineHealthCost)
                .rawPortfolioRiskFactor(rawPortfolioFactor)
                .portfolioRiskFactor(portfolioFactor)
                .rawHealthRiskFactor(rawHealthFactor)
                .healthRiskFactor(healthFactor)
                .underwritingAdjustmentFactor(underwritingAdjustment)
                .businessAdjustmentFactor(businessAdjustment)
                .finalPremium(finalPremium)
                .riskLevel(combinedRisk.name())
                .portfolioModelVersion(portfolioModelVersion)
                .healthModelVersion(healthModelVersion)
                .pricingRuleVersion("v1.0")
                .status("SUCCESS".equals(predictionStatus) ? "GENERATED" : "FAILED")
                .expiredAt(expiredAt)
                .build();

        PremiumQuote savedQuote = quoteRepository.save(quote);

        // 13. Save Snapshots
        PricingInputSnapshot snapshot = PricingInputSnapshot.builder()
                .quote(savedQuote)
                .coveragePlanSnapshot(objectMapper.valueToTree(coveragePlan))
                .insuredPersonSnapshot(objectMapper.valueToTree(insuredPerson))
                .riskProfileSnapshot(request.riskProfile())
                .claimHistorySnapshot(objectMapper.valueToTree(claimHistory))
                .aiRequestSnapshot(objectMapper.valueToTree(predictionRequest))
                .build();
        snapshotRepository.save(snapshot);

        // 14. Save Explanations
        PricingExplanation explanation = PricingExplanation.builder()
                .quote(savedQuote)
                .portfolioExplanation(portfolioExplanation)
                .healthExplanation(healthExplanation)
                .topRiskFactors(topRiskFactors)
                .shapValues(shapValues)
                .explanationMethod(explanationMethod)
                .approximate(approximate)
                .build();
        explanationRepository.save(explanation);

        // 15. Save Audit log
        PricingAuditLog auditLog = PricingAuditLog.builder()
                .quote(savedQuote)
                .action("PREDICT_RISK")
                .requestPayload(objectMapper.valueToTree(predictionRequest))
                .responsePayload(predictionResponse != null ? objectMapper.valueToTree(predictionResponse) : null)
                .status(predictionStatus)
                .errorMessage(errorMessage)
                .build();
        auditLogRepository.save(auditLog);

        if ("GENERATED".equals(savedQuote.getStatus())) {
            pricingOutboxService.enqueue(pricingEventFactory.createQuoteEvent(
                    savedQuote,
                    "quote.generated",
                    null,
                    null));
        }

        return mapToResponse(savedQuote, explanation);
    }

    @Transactional(readOnly = true)
    public QuoteResponse getQuoteById(UUID quoteId) {
        PremiumQuote quote = quoteRepository.findById(quoteId)
                .orElseThrow(() -> new IllegalArgumentException("Premium quote not found with ID: " + quoteId));

        PricingExplanation explanation = explanationRepository.findByQuoteQuoteId(quoteId).orElse(null);
        return mapToResponse(quote, explanation);
    }

    @Transactional(readOnly = true)
    public List<QuoteResponse> getMyQuotes(UUID buyerAuthUserId) {
        UserProfileResponse buyerProfile = userServiceClient.getUserProfileByAuthUserId(buyerAuthUserId);
        if (buyerProfile == null) {
            throw new IllegalArgumentException("Buyer profile not found for auth ID: " + buyerAuthUserId);
        }

        List<PremiumQuote> quotes = quoteRepository.findByBuyerUserIdOrderByCreatedAtDesc(buyerProfile.userId());
        return quotes.stream()
                .map(q -> {
                    PricingExplanation explanation = explanationRepository.findByQuoteQuoteId(q.getQuoteId())
                            .orElse(null);
                    return mapToResponse(q, explanation);
                })
                .toList();
    }

    @Transactional(noRollbackFor = IllegalStateException.class)
    public QuoteResponse acceptQuote(UUID quoteId) {
        PremiumQuote quote = quoteRepository.findById(quoteId)
                .orElseThrow(() -> new IllegalArgumentException("Premium quote not found with ID: " + quoteId));

        if (!"GENERATED".equals(quote.getStatus())) {
            throw new IllegalStateException("Quote cannot be accepted as status is: " + quote.getStatus());
        }

        if (quote.getExpiredAt().isBefore(Instant.now())) {
            quote.setStatus("EXPIRED");
            PremiumQuote saved = quoteRepository.save(quote);
            pricingOutboxService.enqueue(pricingEventFactory.createQuoteEvent(
                    saved,
                    "quote.expired",
                    null,
                    null));
            throw new IllegalStateException("Quote has expired");
        }

        quote.setStatus("ACCEPTED");
        PremiumQuote saved = quoteRepository.save(quote);
        PricingExplanation explanation = explanationRepository.findByQuoteQuoteId(quoteId).orElse(null);
        return mapToResponse(saved, explanation);
    }

    @Transactional(noRollbackFor = IllegalStateException.class)
    public QuoteResponse markQuoteUsed(UUID quoteId) {
        PremiumQuote quote = quoteRepository.findById(quoteId)
                .orElseThrow(() -> new IllegalArgumentException("Premium quote not found with ID: " + quoteId));

        if (quote.getExpiredAt().isBefore(Instant.now())) {
            quote.setStatus("EXPIRED");
            PremiumQuote saved = quoteRepository.save(quote);
            pricingOutboxService.enqueue(pricingEventFactory.createQuoteEvent(
                    saved,
                    "quote.expired",
                    null,
                    null));
            throw new IllegalStateException("Quote has expired");
        }

        if (!"GENERATED".equals(quote.getStatus()) && !"ACCEPTED".equals(quote.getStatus())) {
            throw new IllegalStateException("Quote cannot be marked as used as status is: " + quote.getStatus());
        }

        quote.setStatus("USED");
        PremiumQuote saved = quoteRepository.save(quote);
        PricingExplanation explanation = explanationRepository.findByQuoteQuoteId(quoteId).orElse(null);
        return mapToResponse(saved, explanation);
    }

    @Transactional(readOnly = true)
    public ValidateQuoteResponse validateQuote(UUID quoteId, ValidateQuoteRequest request) {
        PremiumQuote quote = quoteRepository.findById(quoteId).orElse(null);
        if (quote == null) {
            return new ValidateQuoteResponse(false, quoteId, null, null, null, null, null, null, "NOT_FOUND", null);
        }

        boolean valid = true;
        String status = quote.getStatus();

        if (!"GENERATED".equals(status) && !"ACCEPTED".equals(status)) {
            valid = false;
        }
        if (quote.getExpiredAt().isBefore(Instant.now())) {
            valid = false;
            status = "EXPIRED";
        }
        if (request.buyerUserId() != null && !quote.getBuyerUserId().equals(request.buyerUserId())) {
            valid = false;
        }
        if (request.insuredPersonId() != null && !quote.getInsuredPersonId().equals(request.insuredPersonId())) {
            valid = false;
        }
        if (request.coveragePlanId() != null && !quote.getCoveragePlanId().equals(request.coveragePlanId())) {
            valid = false;
        }

        return new ValidateQuoteResponse(
                valid,
                quote.getQuoteId(),
                quote.getBuyerUserId(),
                quote.getInsuredPersonId(),
                quote.getProductId(),
                quote.getCoveragePlanId(),
                quote.getBasePremium(),
                quote.getFinalPremium(),
                status,
                quote.getExpiredAt());
    }

    // --- Helper Parsing Methods ---

    private double calculateAge(String dobStr, JsonNode riskNode) {
        if (riskNode != null && riskNode.has("age")) {
            return riskNode.get("age").asDouble();
        }
        if (dobStr != null && !dobStr.isBlank()) {
            try {
                LocalDate dob = LocalDate.parse(dobStr);
                return Period.between(dob, LocalDate.now()).getYears();
            } catch (Exception e) {
                log.warn("Failed to parse dateOfBirth '{}', defaulting age to 30.", dobStr);
            }
        }
        return 30.0;
    }

    private String parseGender(String insuredGender, JsonNode riskNode) {
        if (riskNode != null && riskNode.has("sex")) {
            return riskNode.get("sex").asText().toLowerCase();
        }
        if (insuredGender != null && !insuredGender.isBlank()) {
            String lower = insuredGender.toLowerCase();
            if (lower.startsWith("m"))
                return "male";
            if (lower.startsWith("f"))
                return "female";
        }
        return "male";
    }

    private String parseSmoker(JsonNode riskNode) {
        if (riskNode != null && riskNode.has("smoker")) {
            JsonNode smokerNode = riskNode.get("smoker");
            if (smokerNode.isBoolean()) {
                return smokerNode.asBoolean() ? "yes" : "no";
            }
            String val = smokerNode.asText().toLowerCase();
            return ("yes".equals(val) || "true".equals(val)) ? "yes" : "no";
        }
        return "no";
    }

    private ClaimHistorySummaryResponse fetchClaimHistory(UUID customerId, String productType) {
        try {
            return applicationPolicyServiceClient.getClaimHistorySummary(customerId, productType);
        } catch (Exception e) {
            log.info("Claim history service unavailable, fallback to default profile. Message: {}", e.getMessage());
            return new ClaimHistorySummaryResponse(
                    0,
                    0.0,
                    5,
                    0.0,
                    0.0,
                    0.0,
                    false,
                    true,
                    0.0);
        }
    }

    private PortfolioPricingProfile neutralPortfolioProfile(String gender, String productType) {
        return new PortfolioPricingProfile(
                gender,
                productType,
                "STANDARD",
                "FULL",
                1.0,
                1.0,
                "YES",
                "DIRECT",
                neutralPrevCostClaimsYearEur,
                neutralPrevNMedicalServices,
                neutralPrevHadClaimOrService,
                neutralClaimFreePreviousYear);
    }

    private HistoricalExperienceFeatures neutralHistoricalExperience() {
        return new HistoricalExperienceFeatures(0, 0.0, 5, 0.0);
    }

    private boolean hasPortfolioExperience(ClaimHistorySummaryResponse claimHistory) {
        return positive(claimHistory.historicalExposureYears())
                || positive(claimHistory.pastClaimCount())
                || positive(claimHistory.totalPastClaimAmount())
                || positive(claimHistory.prevCostClaimsYear())
                || positive(claimHistory.prevNMedicalServices())
                || Boolean.TRUE.equals(claimHistory.prevHadClaimOrService());
    }

    private BigDecimal calculateRawExperienceFactor(
            HealthPricingPredictionResponse actualResponse,
            HealthPricingPredictionResponse neutralResponse,
            ClaimHistorySummaryResponse claimHistory) {
        if (!hasPortfolioExperience(claimHistory)
                || actualResponse == null
                || actualResponse.portfolioModel() == null
                || neutralResponse == null
                || neutralResponse.portfolioModel() == null) {
            return BigDecimal.ONE;
        }

        BigDecimal actual = actualResponse.portfolioModel().predictedAnnualClaimCost();
        BigDecimal neutral = neutralResponse.portfolioModel().predictedAnnualClaimCost();
        if (actual == null || neutral == null || neutral.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ONE;
        }
        return BigDecimal.valueOf(actual.doubleValue() / neutral.doubleValue());
    }

    private BigDecimal calculatePortfolioExperienceFactor(BigDecimal rawExperienceFactor, ClaimHistorySummaryResponse claimHistory) {
        if (!hasPortfolioExperience(claimHistory)) {
            return BigDecimal.ONE;
        }

        double raw = rawExperienceFactor != null ? rawExperienceFactor.doubleValue() : 1.0;
        double exposureYears = Math.max(nullToZero(claimHistory.historicalExposureYears()), 0.0);
        double credibility = portfolioCredibilityK > 0
                ? exposureYears / (exposureYears + portfolioCredibilityK)
                : 1.0;
        double blended = 1.0 + credibility * (raw - 1.0);
        return BigDecimal.valueOf(clamp(blended, portfolioFactorMin, portfolioFactorMax));
    }

    private BigDecimal calculateHealthRatingFactor(BigDecimal rawHealthRiskFactor) {
        double raw = rawHealthRiskFactor != null ? rawHealthRiskFactor.doubleValue() : 1.0;
        if (raw <= 0) {
            return BigDecimal.ONE;
        }
        double logAdjusted = 1.0 + healthRiskLogAlpha * Math.log(raw);
        return BigDecimal.valueOf(clamp(logAdjusted, healthFactorMin, healthFactorMax));
    }

    private double clamp(double value, double min, double max) {
        double lower = Math.min(min, max);
        double upper = Math.max(min, max);
        return Math.max(lower, Math.min(value, upper));
    }

    private boolean positive(Number value) {
        return value != null && value.doubleValue() > 0.0;
    }

    private double nullToZero(Double value) {
        return value != null ? value : 0.0;
    }

    private double vndToEur(Double amountVnd) {
        if (amountVnd == null || amountVnd <= 0) {
            return 0.0;
        }
        if (vndPerEur <= 0) {
            log.warn("Invalid app.currency.vnd-per-eur value: {}. Falling back to unconverted VND amount.", vndPerEur);
            return amountVnd;
        }
        return amountVnd / vndPerEur;
    }

    private ArrayNode mergeExplanationItems(JsonNode portfolioExplanation, JsonNode healthExplanation, String fieldName, int limit) {
        List<JsonNode> items = new ArrayList<>();
        collectExplanationItems(items, portfolioExplanation, fieldName, "portfolio");
        collectExplanationItems(items, healthExplanation, fieldName, "health");

        if (items.isEmpty()) {
            return null;
        }

        items.sort(Comparator.comparingDouble(this::absContribution).reversed());
        ArrayNode merged = objectMapper.createArrayNode();
        items.stream().limit(limit).forEach(merged::add);
        return merged;
    }

    private void collectExplanationItems(List<JsonNode> target, JsonNode explanation, String fieldName, String model) {
        if (explanation == null || !explanation.has(fieldName) || !explanation.get(fieldName).isArray()) {
            return;
        }

        for (JsonNode item : explanation.get(fieldName)) {
            ObjectNode copy = item.isObject()
                    ? ((ObjectNode) item).deepCopy()
                    : objectMapper.createObjectNode().set("value", item);
            copy.put("model", model);
            target.add(copy);
        }
    }

    private double absContribution(JsonNode item) {
        double contribution = item.has("contribution")
                ? item.get("contribution").asDouble()
                : item.path("value").asDouble(0.0);
        return Math.abs(contribution);
    }

    private QuoteResponse mapToResponse(PremiumQuote q, PricingExplanation expl) {
        QuoteExplanationResponse explanationResp = null;
        if (expl != null) {
            explanationResp = new QuoteExplanationResponse(
                    expl.getPortfolioExplanation(),
                    expl.getHealthExplanation(),
                    expl.getTopRiskFactors(),
                    expl.getShapValues(),
                    expl.getExplanationMethod(),
                    expl.getApproximate());
        }

        BigDecimal combinedFactor = q.getPortfolioRiskFactor().multiply(q.getHealthRiskFactor());

        return new QuoteResponse(
                q.getQuoteId(),
                q.getBuyerUserId(),
                q.getInsuredPersonId(),
                q.getProductId(),
                q.getCoveragePlanId(),
                q.getProductType(),
                q.getPlanName(),
                q.getBasePremium(),
                q.getSumInsured(),
                q.getPredictedAnnualClaimCost(),
                q.getPredictedHealthCost(),
                q.getBaselineHealthCost(),
                q.getPortfolioRiskFactor(),
                q.getHealthRiskFactor(),
                combinedFactor,
                q.getFinalPremium(),
                q.getRiskLevel(),
                q.getStatus(),
                q.getCreatedAt(),
                q.getExpiredAt(),
                explanationResp);
    }
}
