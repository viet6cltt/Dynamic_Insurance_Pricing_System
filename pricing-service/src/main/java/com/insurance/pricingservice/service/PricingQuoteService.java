package com.insurance.pricingservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

    private static final double DEFAULT_EXPOSURE_TIME = 1.0;
    private static final String DEFAULT_DISTRIBUTION_CHANNEL = "D";
    private static final String DEFAULT_TYPE_POLICY = "I";

    private final PremiumQuoteRepository quoteRepository;
    private final PricingInputSnapshotRepository snapshotRepository;
    private final PricingExplanationRepository explanationRepository;
    private final PricingAuditLogRepository auditLogRepository;

    private final ProductServiceClient productServiceClient;
    private final UserServiceClient userServiceClient;
    private final ApplicationPolicyServiceClient applicationPolicyServiceClient;
    private final AiModelServiceClient aiModelServiceClient;

    private final RatingEngine ratingEngine;
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

    @Transactional(noRollbackFor = IllegalStateException.class)
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
        ClaimHistorySummaryResponse claimHistory = fetchClaimHistory(request.insuredPersonId(),
                coveragePlan.productType());
        double prevCostClaimsYearEur = vndToEur(claimHistory.prevCostClaimsYear());
        double totalPastClaimAmountEur = vndToEur(claimHistory.totalPastClaimAmount());
        String planTier = mapPlanNameToTier(coveragePlan.planName());
        String aiTypeProduct = mapTierToAiTypeProduct(planTier);
        String aiTypePolicy = mapCoveragePlanToAiTypePolicy();
        Boolean reimbursementVal = request.reimbursementEnabled();
        if (reimbursementVal == null) {
            reimbursementVal = coveragePlan.reimbursementEnabled();
        }
        String aiReimbursement = mapCoveragePlanToAiReimbursement(reimbursementVal);
        String aiNewBusiness = mapHistoricalExperienceToAiNewBusiness(claimHistory);
        String aiDistributionChannel = DEFAULT_DISTRIBUTION_CHANNEL;

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
                aiTypeProduct,
                aiTypePolicy,
                aiReimbursement,
                DEFAULT_EXPOSURE_TIME,
                claimHistory.seniorityInsured() != null ? claimHistory.seniorityInsured() : 0.0,
                aiNewBusiness,
                aiDistributionChannel,
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

        try {
            log.info("Sending prediction request to AI Model Service. Payload: {}", objectMapper.writeValueAsString(predictionRequest));
        } catch (Exception e) {
            log.warn("Failed to serialize prediction request for logging: {}", e.getMessage());
        }

        // 9. Invoke AI Model Service
        HealthPricingPredictionResponse predictionResponse = null;
        String predictionStatus = "SUCCESS";
        String errorMessage = null;
        try {
            predictionResponse = aiModelServiceClient.predictHealthPricing(predictionRequest);
        } catch (Exception e) {
            predictionStatus = "FAILED";
            errorMessage = e.getMessage();
            log.error("AI Model Service call failed: {}", e.getMessage(), e);
        }

        BigDecimal predictedFrequency = BigDecimal.ZERO.setScale(RatingEngine.FREQUENCY_SCALE,
                RatingEngine.ROUNDING_MODE);
        BigDecimal predictedSeverity = BigDecimal.ZERO.setScale(RatingEngine.MONEY_SCALE, RatingEngine.ROUNDING_MODE);
        BigDecimal aiPurePremium = BigDecimal.ZERO.setScale(RatingEngine.MONEY_SCALE, RatingEngine.ROUNDING_MODE);
        BigDecimal purePremium = BigDecimal.ZERO.setScale(RatingEngine.MONEY_SCALE, RatingEngine.ROUNDING_MODE);
        BigDecimal loadingRate = coveragePlan.loadingRate().setScale(RatingEngine.RATE_SCALE,
                RatingEngine.ROUNDING_MODE);
        BigDecimal finalPremium = BigDecimal.ZERO.setScale(RatingEngine.MONEY_SCALE, RatingEngine.ROUNDING_MODE);
        String frequencyModelVersion = null;
        String severityModelVersion = null;
        String riskLevel = null;
        JsonNode frequencyExplanation = null;
        JsonNode severityExplanation = null;
        JsonNode topRiskFactors = null;
        JsonNode shapValues = null;
        String explanationMethod = "frequency_severity";
        boolean approximate = false;
        boolean purePremiumRecalculated = false;

        if ("SUCCESS".equals(predictionStatus) && predictionResponse != null) {
            predictedFrequency = predictionResponse.predictedAnnualFrequency()
                    .setScale(RatingEngine.FREQUENCY_SCALE, RatingEngine.ROUNDING_MODE);
            predictedSeverity = eurToVnd(predictionResponse.predictedAverageSeverity());
            aiPurePremium = eurToVnd(predictionResponse.purePremium());
            purePremium = ratingEngine.calculatePurePremium(predictedFrequency, predictedSeverity);
            finalPremium = ratingEngine.calculateFinalPremium(purePremium, loadingRate);
            purePremiumRecalculated = purePremium.subtract(aiPurePremium).abs().compareTo(new BigDecimal("0.01")) > 0;
            riskLevel = predictionResponse.riskLevel();
            frequencyModelVersion = predictionResponse.frequencyModelVersion();
            severityModelVersion = predictionResponse.severityModelVersion();
            frequencyExplanation = predictionResponse.frequencyExplanation();
            JsonNode rawSeverityExplanation = predictionResponse.severityExplanation();
            severityExplanation = convertSeverityExplanationToVnd(rawSeverityExplanation);
            topRiskFactors = predictionResponse.topRiskFactors() != null && !predictionResponse.topRiskFactors().isNull()
                    ? predictionResponse.topRiskFactors()
                    : mergeExplanationItems(frequencyExplanation, rawSeverityExplanation, "topFactors", 8);
            shapValues = mergeExplanationItems(frequencyExplanation, rawSeverityExplanation, "shapValues", 10);
            approximate = purePremiumRecalculated;
            explanationMethod = "shap";
        }

        // 10. Save Quote aggregate
        Instant now = Instant.now();
        Instant expiredAt = now.plus(7, ChronoUnit.DAYS); // default 7 days validity

        PremiumQuote quote = PremiumQuote.builder()
                .buyerUserId(buyerProfile.userId())
                .insuredPersonId(request.insuredPersonId())
                .productId(coveragePlan.productId())
                .coveragePlanId(request.coveragePlanId())
                .productType(coveragePlan.productType())
                .planName(coveragePlan.planName())
                .sumInsured(coveragePlan.sumInsured())
                .predictedFrequency(predictedFrequency)
                .predictedSeverity(predictedSeverity)
                .purePremium(purePremium)
                .loadingRate(loadingRate)
                .finalPremium(finalPremium)
                .riskLevel(riskLevel)
                .frequencyModelVersion(frequencyModelVersion)
                .severityModelVersion(severityModelVersion)
                .status("SUCCESS".equals(predictionStatus) ? "GENERATED" : "FAILED")
                .expiredAt(expiredAt)
                .build();

        PremiumQuote savedQuote = quoteRepository.save(quote);

        // 11. Save Snapshots
        PricingInputSnapshot snapshot = PricingInputSnapshot.builder()
                .quote(savedQuote)
                .coveragePlanSnapshot(objectMapper.valueToTree(coveragePlan))
                .insuredPersonSnapshot(objectMapper.valueToTree(insuredPerson))
                .riskProfileSnapshot(request.riskProfile())
                .claimHistorySnapshot(objectMapper.valueToTree(claimHistory))
                .aiRequestSnapshot(objectMapper.valueToTree(predictionRequest))
                .build();
        snapshotRepository.save(snapshot);

        // 12. Save Explanations
        PricingExplanation explanation = PricingExplanation.builder()
                .quote(savedQuote)
                .portfolioExplanation(frequencyExplanation)
                .healthExplanation(severityExplanation)
                .topRiskFactors(topRiskFactors)
                .shapValues(shapValues)
                .explanationMethod(explanationMethod)
                .approximate(approximate)
                .build();
        explanationRepository.save(explanation);

        ObjectNode auditResponse = predictionResponse != null
                ? objectMapper.valueToTree(predictionResponse)
                : objectMapper.createObjectNode();
        auditResponse.put("vndPerEur", vndPerEur);
        auditResponse.put("pricingServicePurePremium", purePremium);
        auditResponse.put("aiPurePremium", aiPurePremium);
        auditResponse.put("purePremiumRecalculated", purePremiumRecalculated);
        auditResponse.put("loadingRate", loadingRate);
        auditResponse.put("finalPremium", finalPremium);

        // 13. Save Audit log
        PricingAuditLog auditLog = PricingAuditLog.builder()
                .quote(savedQuote)
                .action("PREDICT_FREQUENCY_SEVERITY")
                .requestPayload(objectMapper.valueToTree(predictionRequest))
                .responsePayload(auditResponse)
                .status(purePremiumRecalculated ? "SUCCESS_RECALCULATED" : predictionStatus)
                .errorMessage(errorMessage)
                .build();
        auditLogRepository.save(auditLog);

        if ("GENERATED".equals(savedQuote.getStatus())) {
            pricingOutboxService.enqueue(pricingEventFactory.createQuoteEvent(
                    savedQuote,
                    "PremiumQuoteGenerated",
                    null,
                    null));
        }

        if ("FAILED".equals(savedQuote.getStatus())) {
            pricingOutboxService.enqueue(pricingEventFactory.createQuoteEvent(
                    savedQuote,
                    "PremiumQuoteFailed",
                    null,
                    null));
            throw new IllegalStateException(
                    "AI Model Service failed to generate frequency-severity prediction: " + errorMessage);
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
                    "PremiumQuoteExpired",
                    null,
                    null));
            throw new IllegalStateException("Quote has expired");
        }

        quote.setStatus("ACCEPTED");
        PremiumQuote saved = quoteRepository.save(quote);
        pricingOutboxService.enqueue(pricingEventFactory.createQuoteEvent(
                saved,
                "PremiumQuoteAccepted",
                null,
                null));
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
                    "PremiumQuoteExpired",
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
            return new ValidateQuoteResponse(false, quoteId, null, null, null, null, null, null, null, "NOT_FOUND",
                    null);
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
                quote.getPurePremium(),
                quote.getLoadingRate(),
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

    private String mapPlanNameToTier(String planName) {
        String normalized = planName == null ? "" : planName.trim().toLowerCase();
        if (normalized.contains("premium")) {
            return "PREMIUM";
        }
        return "STANDARD";
    }

    private String mapTierToAiTypeProduct(String tier) {
        if ("PREMIUM".equals(tier)) {
            return "P";
        }
        return "S";
    }

    private String mapCoveragePlanToAiTypePolicy() {
        return DEFAULT_TYPE_POLICY;
    }

    private String mapCoveragePlanToAiReimbursement(Boolean reimbursementEnabled) {
        if (Boolean.TRUE.equals(reimbursementEnabled)) {
            return "Yes";
        }
        return "No";
    }

    private String mapHistoricalExperienceToAiNewBusiness(ClaimHistorySummaryResponse claimHistory) {
        if (claimHistory == null) {
            return "Yes";
        }
        int activeCount = claimHistory.activePolicyCount() != null ? claimHistory.activePolicyCount() : 0;
        int completedCount = claimHistory.completedPolicyCount() != null ? claimHistory.completedPolicyCount() : 0;
        if (activeCount == 0 && completedCount == 0) {
            return "Yes";
        }
        return "No";
    }

    private ClaimHistorySummaryResponse fetchClaimHistory(UUID customerId, String productType) {
        try {
            return applicationPolicyServiceClient.getClaimHistorySummary(customerId, productType);
        } catch (Exception e) {
            log.info("Claim history service unavailable, fallback to default profile. Message: {}", e.getMessage());
            return new ClaimHistorySummaryResponse(
                    0,
                    0.0,
                    0,
                    0.0,
                    0.0,
                    0.0,
                    false,
                    true,
                    0.0,
                    0.0,
                    0,
                    0);
        }
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

    private BigDecimal eurToVnd(BigDecimal amountEur) {
        if (amountEur == null || amountEur.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(RatingEngine.MONEY_SCALE, RatingEngine.ROUNDING_MODE);
        }
        if (vndPerEur <= 0) {
            log.warn("Invalid app.currency.vnd-per-eur value: {}. Falling back to unconverted EUR amount.", vndPerEur);
            return amountEur.setScale(RatingEngine.MONEY_SCALE, RatingEngine.ROUNDING_MODE);
        }
        return amountEur.multiply(BigDecimal.valueOf(vndPerEur))
                .setScale(RatingEngine.MONEY_SCALE, RatingEngine.ROUNDING_MODE);
    }

    private JsonNode convertSeverityExplanationToVnd(JsonNode severityExplanation) {
        if (severityExplanation == null || severityExplanation.isNull()) {
            return severityExplanation;
        }
        if (vndPerEur <= 0) {
            return severityExplanation;
        }
        ObjectNode objectNode = severityExplanation.deepCopy();

        if (objectNode.has("baseValue")) {
            double baseValEur = objectNode.get("baseValue").asDouble();
            BigDecimal baseValVnd = BigDecimal.valueOf(baseValEur).multiply(BigDecimal.valueOf(vndPerEur))
                    .setScale(RatingEngine.MONEY_SCALE, RatingEngine.ROUNDING_MODE);
            objectNode.put("baseValue", baseValVnd);
        }

        if (objectNode.has("predictedValue")) {
            double predValEur = objectNode.get("predictedValue").asDouble();
            BigDecimal predValVnd = BigDecimal.valueOf(predValEur).multiply(BigDecimal.valueOf(vndPerEur))
                    .setScale(RatingEngine.MONEY_SCALE, RatingEngine.ROUNDING_MODE);
            objectNode.put("predictedValue", predValVnd);
        }

        if (objectNode.has("topFactors") && objectNode.get("topFactors").isArray()) {
            ArrayNode topFactors = (ArrayNode) objectNode.get("topFactors");
            for (int i = 0; i < topFactors.size(); i++) {
                JsonNode factor = topFactors.get(i);
                if (factor instanceof ObjectNode factorObj && factorObj.has("shapValue")) {
                    double shapEur = factorObj.get("shapValue").asDouble();
                    BigDecimal shapVnd = BigDecimal.valueOf(shapEur).multiply(BigDecimal.valueOf(vndPerEur))
                            .setScale(RatingEngine.MONEY_SCALE, RatingEngine.ROUNDING_MODE);
                    factorObj.put("shapValue", shapVnd);
                }
            }
        }

        return objectNode;
    }

    private ArrayNode mergeExplanationItems(JsonNode frequencyExplanation, JsonNode severityExplanation,
            String fieldName, int limit) {
        List<JsonNode> items = new ArrayList<>();
        collectExplanationItems(items, frequencyExplanation, fieldName, "frequency");
        collectExplanationItems(items, severityExplanation, fieldName, "severity");

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

        return new QuoteResponse(
                q.getQuoteId(),
                q.getBuyerUserId(),
                q.getInsuredPersonId(),
                q.getProductId(),
                q.getCoveragePlanId(),
                q.getProductType(),
                q.getPlanName(),
                q.getSumInsured(),
                q.getPredictedFrequency(),
                q.getPredictedSeverity(),
                q.getPurePremium(),
                q.getLoadingRate(),
                q.getFinalPremium(),
                q.getFrequencyModelVersion(),
                q.getSeverityModelVersion(),
                q.getRiskLevel(),
                q.getStatus(),
                q.getCreatedAt(),
                q.getExpiredAt(),
                explanationResp);
    }
}
