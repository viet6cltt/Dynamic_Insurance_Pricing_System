package com.insurance.applicationpolicyservice.service;

import com.insurance.applicationpolicyservice.config.FeignConfig;
import com.insurance.applicationpolicyservice.dto.CreateMockPaymentRequest;
import com.insurance.applicationpolicyservice.dto.PaymentResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(
        name = "payment-service",
        url = "${app.services.payment-service-url}",
        configuration = FeignConfig.class
)
public interface PaymentServiceClient {

    @PostMapping("/payments/mock")
    PaymentResponse createMockPayment(
            @RequestBody CreateMockPaymentRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("X-Correlation-Id") String correlationId
    );
}
