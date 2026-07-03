package com.insurance.applicationpolicyservice.controller;

import com.insurance.applicationpolicyservice.dto.PolicyExperienceSummaryResponse;
import com.insurance.applicationpolicyservice.service.ContractService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalContractController {

    private final ContractService contractService;

    @GetMapping("/customers/{customerId}/claim-history-summary")
    public ResponseEntity<PolicyExperienceSummaryResponse> getClaimHistorySummary(
            @PathVariable("customerId") UUID customerId,
            @RequestParam("productType") String productType) {
        PolicyExperienceSummaryResponse response = contractService.getExperienceSummary(customerId, productType);
        return ResponseEntity.ok(response);
    }
}
