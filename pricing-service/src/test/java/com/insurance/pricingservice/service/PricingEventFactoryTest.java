package com.insurance.pricingservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.insurance.pricingservice.model.PremiumQuote;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PricingEventFactoryTest {

    @Test
    void createQuoteEventCopiesQuoteFieldsIntoEnvelope() {
        PremiumQuote quote = PremiumQuote.builder()
                .quoteId(UUID.randomUUID())
                .buyerUserId(UUID.randomUUID())
                .insuredPersonId(UUID.randomUUID())
                .productId(UUID.randomUUID())
                .coveragePlanId(UUID.randomUUID())
                .productType("HEALTH")
                .planName("Gold")
                .sumInsured(new BigDecimal("100000000.00"))
                .purePremium(new BigDecimal("1000000.00"))
                .loadingRate(new BigDecimal("0.1000"))
                .finalPremium(new BigDecimal("1100000.00"))
                .riskLevel("LOW")
                .status("GENERATED")
                .expiredAt(Instant.now())
                .build();

        var envelope = new PricingEventFactory(new ObjectMapper().registerModule(new JavaTimeModule()))
                .createQuoteEvent(quote, "PremiumQuoteGenerated", "corr-1", "cause-1");

        assertEquals("PremiumQuoteGenerated", envelope.eventType());
        assertEquals("pricing-service", envelope.producer());
        assertEquals(quote.getQuoteId(), envelope.aggregateId());
        assertEquals("Gold", envelope.payload().get("planName").asText());
    }
}
