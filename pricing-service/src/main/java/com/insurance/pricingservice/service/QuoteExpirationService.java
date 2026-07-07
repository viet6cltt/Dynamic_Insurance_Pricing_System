package com.insurance.pricingservice.service;

import com.insurance.pricingservice.model.PremiumQuote;
import com.insurance.pricingservice.repository.PremiumQuoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuoteExpirationService {

    private final PremiumQuoteRepository quoteRepository;
    private final PricingEventFactory pricingEventFactory;
    private final PricingOutboxService pricingOutboxService;

    @Scheduled(cron = "${app.quote.expiration-cron:0 10 0 * * *}")
    @Transactional
    public int expireQuotes() {
        List<PremiumQuote> quotes = quoteRepository.findActiveExpiredQuotes(Instant.now());
        for (PremiumQuote quote : quotes) {
            quote.setStatus("EXPIRED");
            PremiumQuote saved = quoteRepository.save(quote);
            pricingOutboxService.enqueue(pricingEventFactory.createQuoteEvent(
                    saved,
                    "PremiumQuoteExpired",
                    null,
                    null
            ));
        }
        if (!quotes.isEmpty()) {
            log.info("Expired {} premium quotes", quotes.size());
        }
        return quotes.size();
    }
}
