package com.insurance.productservice.controller;

import com.insurance.productservice.dto.ListResponse;
import com.insurance.productservice.dto.OccupationRiskMappingResponse;
import com.insurance.productservice.service.OccupationRiskMappingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class OccupationRiskMappingController {

    private final OccupationRiskMappingService mappingService;

    @GetMapping("/products/{productId}/occupation-risk-mappings")
    public ResponseEntity<ListResponse<OccupationRiskMappingResponse>> getMappingsByProduct(
            @PathVariable UUID productId,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(mappingService.getMappingsByProduct(productId, status));
    }

    @GetMapping("/occupation-risk-mappings")
    public ResponseEntity<ListResponse<OccupationRiskMappingResponse>> getMappingsByProductType(
            @RequestParam String productType,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(mappingService.getMappingsByProductType(productType, status));
    }
}
