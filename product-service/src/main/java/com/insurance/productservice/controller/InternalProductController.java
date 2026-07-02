package com.insurance.productservice.controller;

import com.insurance.productservice.dto.InternalCoveragePlanResponse;
import com.insurance.productservice.dto.ResolveOccupationRiskResponse;
import com.insurance.productservice.service.CoveragePlanService;
import com.insurance.productservice.service.OccupationRiskMappingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalProductController {

    private final CoveragePlanService planService;
    private final OccupationRiskMappingService mappingService;

    @GetMapping("/coverage-plans/{coveragePlanId}")
    public ResponseEntity<InternalCoveragePlanResponse> getInternalCoveragePlan(@PathVariable UUID coveragePlanId) {
        return ResponseEntity.ok(planService.getInternalCoveragePlan(coveragePlanId));
    }

    @GetMapping("/occupation-risk-mappings/resolve")
    public ResponseEntity<ResolveOccupationRiskResponse> resolveOccupationRisk(
            @RequestParam String productType,
            @RequestParam String occupationCode) {
        return ResponseEntity.ok(mappingService.resolveOccupationRisk(productType, occupationCode));
    }
}
