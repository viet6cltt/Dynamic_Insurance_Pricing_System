package com.insurance.productservice.service;

import com.insurance.productservice.dto.CreateRiskInputSchemaRequest;
import com.insurance.productservice.dto.RiskInputSchemaResponse;
import com.insurance.productservice.dto.UpdateRiskInputSchemaRequest;
import com.insurance.productservice.model.InsuranceProduct;
import com.insurance.productservice.model.RiskInputSchema;
import com.insurance.productservice.repository.InsuranceProductRepository;
import com.insurance.productservice.repository.RiskInputSchemaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RiskInputSchemaService {

    private final RiskInputSchemaRepository schemaRepository;
    private final InsuranceProductRepository productRepository;

    @Transactional(readOnly = true)
    public RiskInputSchemaResponse getRiskInputSchemaByProduct(UUID productId) {
        return schemaRepository.findFirstByProductProductIdAndStatusOrderByCreatedAtDesc(productId, "ACTIVE")
                .map(this::mapToResponse)
                .orElseThrow(() -> new IllegalArgumentException("Active risk input schema not found for product ID: " + productId));
    }

    @Transactional(readOnly = true)
    public RiskInputSchemaResponse getRiskInputSchemaByProductType(String productType) {
        if (productType == null || productType.isBlank()) {
            throw new IllegalArgumentException("Product type cannot be null or empty");
        }

        return schemaRepository.findFirstByProductProductTypeAndStatusOrderByCreatedAtDesc(productType.toUpperCase(), "ACTIVE")
                .map(this::mapToResponse)
                .orElseThrow(() -> new IllegalArgumentException("Active risk input schema not found for product type: " + productType));
    }

    @Transactional
    public RiskInputSchemaResponse createRiskInputSchema(UUID productId, CreateRiskInputSchemaRequest request) {
        InsuranceProduct product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Insurance product not found with ID: " + productId));

        if (request.schemaVersion() == null || request.schemaVersion().isBlank()) {
            throw new IllegalArgumentException("Schema version cannot be null or empty");
        }
        if (request.schemaDefinition() == null) {
            throw new IllegalArgumentException("Schema definition cannot be null");
        }

        RiskInputSchema schema = RiskInputSchema.builder()
                .product(product)
                .schemaVersion(request.schemaVersion())
                .schemaDefinition(request.schemaDefinition())
                .status(request.status() != null ? request.status().toUpperCase() : "ACTIVE")
                .build();

        return mapToResponse(schemaRepository.save(schema));
    }

    @Transactional
    public RiskInputSchemaResponse updateRiskInputSchema(UUID schemaId, UpdateRiskInputSchemaRequest request) {
        RiskInputSchema schema = schemaRepository.findById(schemaId)
                .orElseThrow(() -> new IllegalArgumentException("Risk input schema not found with ID: " + schemaId));

        if (request.schemaVersion() != null && !request.schemaVersion().isBlank()) {
            schema.setSchemaVersion(request.schemaVersion());
        }
        if (request.schemaDefinition() != null) {
            schema.setSchemaDefinition(request.schemaDefinition());
        }
        if (request.status() != null && !request.status().isBlank()) {
            schema.setStatus(request.status().toUpperCase());
        }

        return mapToResponse(schemaRepository.save(schema));
    }

    @Transactional
    public RiskInputSchemaResponse updateRiskInputSchemaStatus(UUID schemaId, String status) {
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("Status cannot be null or empty");
        }

        RiskInputSchema schema = schemaRepository.findById(schemaId)
                .orElseThrow(() -> new IllegalArgumentException("Risk input schema not found with ID: " + schemaId));

        schema.setStatus(status.toUpperCase());
        return mapToResponse(schemaRepository.save(schema));
    }

    private RiskInputSchemaResponse mapToResponse(RiskInputSchema schema) {
        return new RiskInputSchemaResponse(
                schema.getSchemaId(),
                schema.getProduct().getProductId(),
                schema.getProduct().getProductType(),
                schema.getSchemaVersion(),
                schema.getSchemaDefinition(),
                schema.getStatus(),
                schema.getCreatedAt(),
                schema.getUpdatedAt()
        );
    }
}
