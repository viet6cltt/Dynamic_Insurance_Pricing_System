package com.insurance.applicationpolicyservice.service;

import com.insurance.applicationpolicyservice.config.FeignConfig;
import com.insurance.applicationpolicyservice.dto.QuoteResponse;
import com.insurance.applicationpolicyservice.dto.ValidateQuoteRequest;
import com.insurance.applicationpolicyservice.dto.ValidateQuoteResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

@FeignClient(
    name = "pricing-service",
    url = "${app.services.pricing-service-url}",
    configuration = FeignConfig.class
)
public interface PricingServiceClient {

    @PostMapping("/internal/pricing/quotes/{quoteId}/validate")
    ValidateQuoteResponse validateQuote(
        @PathVariable("quoteId") UUID quoteId,
        @RequestBody ValidateQuoteRequest request
    );

    @PostMapping("/internal/pricing/quotes/{quoteId}/mark-used")
    QuoteResponse markQuoteUsed(@PathVariable("quoteId") UUID quoteId);
}
