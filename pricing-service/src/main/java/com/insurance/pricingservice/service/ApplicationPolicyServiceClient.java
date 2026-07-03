package com.insurance.pricingservice.service;

import com.insurance.pricingservice.dto.ClaimHistorySummaryResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

@FeignClient(name = "application-policy-service", url = "${app.services.application-policy-service-url:http://localhost:8084}")
public interface ApplicationPolicyServiceClient {

    @GetMapping("/internal/customers/{customerId}/claim-history-summary")
    ClaimHistorySummaryResponse getClaimHistorySummary(
            @PathVariable("customerId") UUID customerId,
            @RequestParam("productType") String productType
    );
}
