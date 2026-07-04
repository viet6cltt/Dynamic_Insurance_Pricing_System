package com.insurance.pricingservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.pricingservice.dto.EventEnvelope;
import com.insurance.pricingservice.model.PremiumQuote;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class PricingEventFactory {

    private final ObjectMapper objectMapper;

    public PricingEventFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public EventEnvelope createQuoteEvent(PremiumQuote quote,
                                          String eventType,
                                          String correlationId,
                                          String causationId) {
        Map<String, Object> payloadValues = new HashMap<>();
        payloadValues.put("quoteId", quote.getQuoteId());
        payloadValues.put("buyerUserId", quote.getBuyerUserId());
        payloadValues.put("insuredPersonId", quote.getInsuredPersonId());
        payloadValues.put("productId", quote.getProductId());
        payloadValues.put("coveragePlanId", quote.getCoveragePlanId());
        payloadValues.put("productType", quote.getProductType());
        payloadValues.put("planName", quote.getPlanName());
        payloadValues.put("basePremium", quote.getBasePremium());
        payloadValues.put("finalPremium", quote.getFinalPremium());
        payloadValues.put("currency", "VND");
        payloadValues.put("status", quote.getStatus());
        payloadValues.put("expiredAt", quote.getExpiredAt());
        JsonNode payload = objectMapper.valueToTree(payloadValues);

        return new EventEnvelope(
                UUID.randomUUID(),
                eventType,
                1,
                Instant.now(),
                "pricing-service",
                "PremiumQuote",
                quote.getQuoteId(),
                correlationId,
                causationId,
                payload
        );
    }
}
