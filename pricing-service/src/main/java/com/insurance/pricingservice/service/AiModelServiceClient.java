package com.insurance.pricingservice.service;

import com.insurance.pricingservice.dto.HealthPricingPredictionRequest;
import com.insurance.pricingservice.dto.HealthPricingPredictionResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "ai-model-service", url = "${app.services.ai-model-service-url:http://localhost:8000}")
public interface AiModelServiceClient {

    @PostMapping("/health/pricing/predict")
    HealthPricingPredictionResponse predictHealthPricing(@RequestBody HealthPricingPredictionRequest request);
}
