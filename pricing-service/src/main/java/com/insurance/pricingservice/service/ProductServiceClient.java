package com.insurance.pricingservice.service;

import com.insurance.pricingservice.dto.InternalCoveragePlanResponse;
import com.insurance.pricingservice.dto.ResolveOccupationRiskResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

@FeignClient(name = "product-service", url = "${app.services.product-service-url:http://localhost:8082}")
public interface ProductServiceClient {

    @GetMapping("/internal/coverage-plans/{coveragePlanId}")
    InternalCoveragePlanResponse getInternalCoveragePlan(@PathVariable("coveragePlanId") UUID coveragePlanId);

    @GetMapping("/internal/occupation-risk-mappings/resolve")
    ResolveOccupationRiskResponse resolveOccupationRisk(
            @RequestParam("productType") String productType,
            @RequestParam("occupationCode") String occupationCode
    );
}
