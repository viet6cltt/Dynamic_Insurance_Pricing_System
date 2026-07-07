package com.insurance.pricingservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.insurance.pricingservice.model.PremiumQuote;
import com.insurance.pricingservice.repository.PremiumQuoteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuoteExpirationServiceTest {

    @Mock
    private PremiumQuoteRepository quoteRepository;

    @Mock
    private PricingOutboxService pricingOutboxService;

    @Test
    void expireQuotesMarksExpiredAndEnqueuesEvent() {
        PremiumQuote quote = quote();
        when(quoteRepository.findActiveExpiredQuotes(any())).thenReturn(List.of(quote));
        when(quoteRepository.save(quote)).thenReturn(quote);
        QuoteExpirationService service = new QuoteExpirationService(
                quoteRepository,
                new PricingEventFactory(new ObjectMapper().registerModule(new JavaTimeModule())),
                pricingOutboxService);

        int expired = service.expireQuotes();

        assertEquals(1, expired);
        assertEquals("EXPIRED", quote.getStatus());
        verify(pricingOutboxService).enqueue(any());
    }

    private PremiumQuote quote() {
        return PremiumQuote.builder()
                .quoteId(UUID.randomUUID())
                .buyerUserId(UUID.randomUUID())
                .insuredPersonId(UUID.randomUUID())
                .productId(UUID.randomUUID())
                .coveragePlanId(UUID.randomUUID())
                .productType("HEALTH")
                .planName("Gold")
                .sumInsured(new BigDecimal("100000000.00"))
                .predictedFrequency(new BigDecimal("1.000000"))
                .predictedSeverity(new BigDecimal("1000000.00"))
                .purePremium(new BigDecimal("1000000.00"))
                .loadingRate(new BigDecimal("0.1000"))
                .finalPremium(new BigDecimal("1100000.00"))
                .status("GENERATED")
                .expiredAt(Instant.now().minusSeconds(1))
                .build();
    }
}
