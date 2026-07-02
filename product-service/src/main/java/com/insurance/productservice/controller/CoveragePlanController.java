package com.insurance.productservice.controller;

import com.insurance.productservice.dto.CoveragePlanResponse;
import com.insurance.productservice.dto.ListResponse;
import com.insurance.productservice.service.CoveragePlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class CoveragePlanController {

    private final CoveragePlanService planService;

    @GetMapping("/products/{productId}/coverage-plans")
    public ResponseEntity<ListResponse<CoveragePlanResponse>> getCoveragePlansByProduct(
            @PathVariable UUID productId,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(planService.getCoveragePlansByProduct(productId, status));
    }

    @GetMapping("/coverage-plans/{coveragePlanId}")
    public ResponseEntity<CoveragePlanResponse> getCoveragePlanById(@PathVariable UUID coveragePlanId) {
        return ResponseEntity.ok(planService.getCoveragePlanById(coveragePlanId));
    }
}
