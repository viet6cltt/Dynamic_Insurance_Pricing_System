package com.insurance.pricingservice.controller;

import com.insurance.pricingservice.service.PricingQuoteService;
import com.insurance.pricingservice.dto.CreateQuoteRequest;
import com.insurance.pricingservice.dto.QuoteResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/pricing/quotes")
@RequiredArgsConstructor
public class PricingQuoteController {

    private final PricingQuoteService quoteService;

    @PostMapping
    public ResponseEntity<QuoteResponse> createQuote(@RequestBody CreateQuoteRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UUID authUserId = UUID.fromString(authentication.getName());
        QuoteResponse response = quoteService.createQuote(authUserId, request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/{quoteId}")
    public ResponseEntity<QuoteResponse> getQuoteById(@PathVariable UUID quoteId) {
        return ResponseEntity.ok(quoteService.getQuoteById(quoteId));
    }

    @GetMapping("/me")
    public ResponseEntity<List<QuoteResponse>> getMyQuotes() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UUID authUserId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(quoteService.getMyQuotes(authUserId));
    }

    @PostMapping("/{quoteId}/accept")
    public ResponseEntity<QuoteResponse> acceptQuote(@PathVariable UUID quoteId) {
        return ResponseEntity.ok(quoteService.acceptQuote(quoteId));
    }
}
