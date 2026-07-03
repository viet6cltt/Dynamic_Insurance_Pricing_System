package com.insurance.pricingservice.controller;

import com.insurance.pricingservice.service.PricingQuoteService;
import com.insurance.pricingservice.dto.ValidateQuoteRequest;
import com.insurance.pricingservice.dto.QuoteResponse;
import com.insurance.pricingservice.dto.ValidateQuoteResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/internal/pricing/quotes")
@RequiredArgsConstructor
public class InternalPricingQuoteController {

    private final PricingQuoteService quoteService;

    @GetMapping("/{quoteId}")
    public ResponseEntity<QuoteResponse> getInternalQuoteById(@PathVariable UUID quoteId) {
        return ResponseEntity.ok(quoteService.getQuoteById(quoteId));
    }

    @PostMapping("/{quoteId}/validate")
    public ResponseEntity<ValidateQuoteResponse> validateQuote(
            @PathVariable UUID quoteId,
            @RequestBody ValidateQuoteRequest request) {
        return ResponseEntity.ok(quoteService.validateQuote(quoteId, request));
    }

    @PostMapping("/{quoteId}/mark-used")
    public ResponseEntity<QuoteResponse> markQuoteUsed(@PathVariable UUID quoteId) {
        return ResponseEntity.ok(quoteService.markQuoteUsed(quoteId));
    }
}
