package com.insurance.productservice.controller;

import com.insurance.productservice.dto.RiskInputSchemaResponse;
import com.insurance.productservice.service.RiskInputSchemaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class RiskInputSchemaController {

    private final RiskInputSchemaService schemaService;

    @GetMapping("/products/{productId}/risk-input-schema")
    public ResponseEntity<RiskInputSchemaResponse> getRiskInputSchemaByProduct(@PathVariable UUID productId) {
        return ResponseEntity.ok(schemaService.getRiskInputSchemaByProduct(productId));
    }

    @GetMapping("/risk-input-schemas/by-product-type/{productType}")
    public ResponseEntity<RiskInputSchemaResponse> getRiskInputSchemaByProductType(@PathVariable String productType) {
        return ResponseEntity.ok(schemaService.getRiskInputSchemaByProductType(productType));
    }
}
