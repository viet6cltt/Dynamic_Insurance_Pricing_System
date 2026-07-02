package com.insurance.productservice.controller;

import com.insurance.productservice.dto.InsuranceProductResponse;
import com.insurance.productservice.dto.PagedResponse;
import com.insurance.productservice.service.InsuranceProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class InsuranceProductController {

    private final InsuranceProductService productService;

    @GetMapping
    public ResponseEntity<PagedResponse<InsuranceProductResponse>> getProducts(
            @RequestParam(required = false) String productType,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(productService.getProducts(productType, status, page, size));
    }

    @GetMapping("/{productId}")
    public ResponseEntity<InsuranceProductResponse> getProductById(@PathVariable UUID productId) {
        return ResponseEntity.ok(productService.getProductById(productId));
    }
}
