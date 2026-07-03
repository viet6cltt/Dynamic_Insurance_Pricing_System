package com.insurance.pricingservice.service;

import com.insurance.pricingservice.dto.InsuredPersonResponse;
import com.insurance.pricingservice.dto.UserProfileResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "user-service", url = "${app.services.user-service-url:http://localhost:8081}")
public interface UserServiceClient {

    @GetMapping("/internal/insured-persons/{insuredPersonId}")
    InsuredPersonResponse getInsuredPersonById(@PathVariable("insuredPersonId") UUID insuredPersonId);

    @GetMapping("/internal/users/by-auth-user/{authUserId}")
    UserProfileResponse getUserProfileByAuthUserId(@PathVariable("authUserId") UUID authUserId);
}
