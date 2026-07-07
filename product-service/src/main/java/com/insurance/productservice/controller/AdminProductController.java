package com.insurance.productservice.controller;

import com.insurance.productservice.dto.*;
import com.insurance.productservice.service.CoveragePlanService;
import com.insurance.productservice.service.InsuranceProductService;
import com.insurance.productservice.service.MinioStorageService;
import com.insurance.productservice.service.OccupationRiskMappingService;
import com.insurance.productservice.service.RiskInputSchemaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminProductController {

    private final InsuranceProductService productService;
    private final CoveragePlanService planService;
    private final RiskInputSchemaService schemaService;
    private final OccupationRiskMappingService mappingService;
    private final MinioStorageService storageService;

    // --- Product Admin ---

    @PostMapping("/products")
    public ResponseEntity<InsuranceProductResponse> createProduct(@RequestBody CreateInsuranceProductRequest request) {
        return new ResponseEntity<>(productService.createProduct(request), HttpStatus.CREATED);
    }

    @PutMapping("/products/{productId}")
    public ResponseEntity<InsuranceProductResponse> updateProduct(
            @PathVariable UUID productId,
            @RequestBody UpdateInsuranceProductRequest request) {
        return ResponseEntity.ok(productService.updateProduct(productId, request));
    }

    @PostMapping("/products/{productId}/image")
    public ResponseEntity<InsuranceProductResponse> uploadProductImage(
            @PathVariable UUID productId,
            @RequestParam("file") MultipartFile file) {
        String imageUrl = storageService.uploadFile(file);
        return ResponseEntity.ok(productService.updateProductImageUrl(productId, imageUrl));
    }

    @PatchMapping("/products/{productId}/status")
    public ResponseEntity<InsuranceProductResponse> updateProductStatus(
            @PathVariable UUID productId,
            @RequestBody Map<String, String> body) {
        String status = body.get("status");
        return ResponseEntity.ok(productService.updateProductStatus(productId, status));
    }

    // --- Coverage Plan Admin ---

    @PostMapping("/products/{productId}/coverage-plans")
    public ResponseEntity<CoveragePlanResponse> createCoveragePlan(
            @PathVariable UUID productId,
            @RequestBody CreateCoveragePlanRequest request) {
        return new ResponseEntity<>(planService.createCoveragePlan(productId, request), HttpStatus.CREATED);
    }

    @PutMapping("/coverage-plans/{coveragePlanId}")
    public ResponseEntity<CoveragePlanResponse> updateCoveragePlan(
            @PathVariable UUID coveragePlanId,
            @RequestBody UpdateCoveragePlanRequest request) {
        return ResponseEntity.ok(planService.updateCoveragePlan(coveragePlanId, request));
    }

    @PatchMapping("/coverage-plans/{coveragePlanId}/status")
    public ResponseEntity<CoveragePlanResponse> updateCoveragePlanStatus(
            @PathVariable UUID coveragePlanId,
            @RequestBody Map<String, String> body) {
        String status = body.get("status");
        return ResponseEntity.ok(planService.updateCoveragePlanStatus(coveragePlanId, status));
    }

    @PatchMapping("/coverage-plans/{coveragePlanId}/loading-rate")
    public ResponseEntity<CoveragePlanResponse> updateCoveragePlanLoadingRate(
            @PathVariable UUID coveragePlanId,
            @RequestBody UpdateLoadingRateRequest request) {
        return ResponseEntity.ok(planService.updateLoadingRate(coveragePlanId, request));
    }

    // --- Risk Input Schema Admin ---

    @PostMapping("/products/{productId}/risk-input-schemas")
    public ResponseEntity<RiskInputSchemaResponse> createRiskInputSchema(
            @PathVariable UUID productId,
            @RequestBody CreateRiskInputSchemaRequest request) {
        return new ResponseEntity<>(schemaService.createRiskInputSchema(productId, request), HttpStatus.CREATED);
    }

    @PutMapping("/risk-input-schemas/{schemaId}")
    public ResponseEntity<RiskInputSchemaResponse> updateRiskInputSchema(
            @PathVariable UUID schemaId,
            @RequestBody UpdateRiskInputSchemaRequest request) {
        return ResponseEntity.ok(schemaService.updateRiskInputSchema(schemaId, request));
    }

    @PatchMapping("/risk-input-schemas/{schemaId}/status")
    public ResponseEntity<RiskInputSchemaResponse> updateRiskInputSchemaStatus(
            @PathVariable UUID schemaId,
            @RequestBody Map<String, String> body) {
        String status = body.get("status");
        return ResponseEntity.ok(schemaService.updateRiskInputSchemaStatus(schemaId, status));
    }

    // --- Occupation Risk Mapping Admin ---

    @PostMapping("/products/{productId}/occupation-risk-mappings")
    public ResponseEntity<OccupationRiskMappingResponse> createMapping(
            @PathVariable UUID productId,
            @RequestBody CreateOccupationRiskMappingRequest request) {
        return new ResponseEntity<>(mappingService.createMapping(productId, request), HttpStatus.CREATED);
    }

    @PutMapping("/occupation-risk-mappings/{mappingId}")
    public ResponseEntity<OccupationRiskMappingResponse> updateMapping(
            @PathVariable UUID mappingId,
            @RequestBody UpdateOccupationRiskMappingRequest request) {
        return ResponseEntity.ok(mappingService.updateMapping(mappingId, request));
    }

    @PatchMapping("/occupation-risk-mappings/{mappingId}/status")
    public ResponseEntity<OccupationRiskMappingResponse> updateMappingStatus(
            @PathVariable UUID mappingId,
            @RequestBody Map<String, String> body) {
        String status = body.get("status");
        return ResponseEntity.ok(mappingService.updateMappingStatus(mappingId, status));
    }
}
