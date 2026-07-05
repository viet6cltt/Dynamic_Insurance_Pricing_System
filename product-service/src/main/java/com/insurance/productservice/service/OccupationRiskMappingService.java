package com.insurance.productservice.service;

import com.insurance.productservice.config.CacheConfig;
import com.insurance.productservice.dto.CreateOccupationRiskMappingRequest;
import com.insurance.productservice.dto.ListResponse;
import com.insurance.productservice.dto.OccupationRiskMappingResponse;
import com.insurance.productservice.dto.ResolveOccupationRiskResponse;
import com.insurance.productservice.dto.UpdateOccupationRiskMappingRequest;
import com.insurance.productservice.model.InsuranceProduct;
import com.insurance.productservice.model.OccupationRiskMapping;
import com.insurance.productservice.repository.InsuranceProductRepository;
import com.insurance.productservice.repository.OccupationRiskMappingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OccupationRiskMappingService {

    private final OccupationRiskMappingRepository mappingRepository;
    private final InsuranceProductRepository productRepository;

    @Transactional(readOnly = true)
    public ListResponse<OccupationRiskMappingResponse> getMappingsByProduct(UUID productId, String status) {
        List<OccupationRiskMapping> mappings;
        if (status != null && !status.isBlank()) {
            mappings = mappingRepository.findByProductProductIdAndStatus(productId, status.toUpperCase());
        } else {
            mappings = mappingRepository.findByProductProductId(productId);
        }
        return new ListResponse<>(mappings.stream().map(this::mapToResponse).toList());
    }

    @Transactional(readOnly = true)
    public ListResponse<OccupationRiskMappingResponse> getMappingsByProductType(String productType, String status) {
        if (productType == null || productType.isBlank()) {
            throw new IllegalArgumentException("Product type cannot be null or empty");
        }

        String statusVal = (status == null || status.isBlank()) ? "ACTIVE" : status.toUpperCase();
        List<OccupationRiskMapping> mappings = mappingRepository.findByProductProductTypeAndStatus(productType.toUpperCase(), statusVal);
        return new ListResponse<>(mappings.stream().map(this::mapToResponse).toList());
    }

    @Transactional(readOnly = true)
    @Cacheable(value = CacheConfig.OCCUPATION_RISK_CACHE,
            key = "#productType.toUpperCase() + ':' + #occupationCode.toUpperCase()")
    public ResolveOccupationRiskResponse resolveOccupationRisk(String productType, String occupationCode) {
        if (productType == null || productType.isBlank()) {
            throw new IllegalArgumentException("Product type cannot be null or empty");
        }
        if (occupationCode == null || occupationCode.isBlank()) {
            throw new IllegalArgumentException("Occupation code cannot be null or empty");
        }

        OccupationRiskMapping mapping = mappingRepository.findByProductProductTypeAndOccupationCodeAndStatus(
                productType.toUpperCase(),
                occupationCode.toUpperCase(),
                "ACTIVE"
        ).orElseThrow(() -> new IllegalArgumentException(
                "Active occupation mapping not found for product type: " + productType + " and code: " + occupationCode
        ));

        return new ResolveOccupationRiskResponse(
                mapping.getProduct().getProductType(),
                mapping.getOccupationCode(),
                mapping.getOccupationName(),
                mapping.getRiskLevel()
        );
    }

    @Transactional
    public OccupationRiskMappingResponse createMapping(UUID productId, CreateOccupationRiskMappingRequest request) {
        InsuranceProduct product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Insurance product not found with ID: " + productId));

        if (request.occupationCode() == null || request.occupationCode().isBlank()) {
            throw new IllegalArgumentException("Occupation code cannot be null or empty");
        }
        if (request.occupationName() == null || request.occupationName().isBlank()) {
            throw new IllegalArgumentException("Occupation name cannot be null or empty");
        }
        if (request.riskLevel() == null || request.riskLevel().isBlank()) {
            throw new IllegalArgumentException("Risk level cannot be null or empty");
        }

        OccupationRiskMapping mapping = OccupationRiskMapping.builder()
                .product(product)
                .occupationCode(request.occupationCode().toUpperCase())
                .occupationName(request.occupationName())
                .riskLevel(request.riskLevel().toUpperCase())
                .status(request.status() != null ? request.status().toUpperCase() : "ACTIVE")
                .build();

        return mapToResponse(mappingRepository.save(mapping));
    }

    @Transactional
    @CacheEvict(value = CacheConfig.OCCUPATION_RISK_CACHE, allEntries = true)
    public OccupationRiskMappingResponse updateMapping(UUID mappingId, UpdateOccupationRiskMappingRequest request) {
        OccupationRiskMapping mapping = mappingRepository.findById(mappingId)
                .orElseThrow(() -> new IllegalArgumentException("Occupation mapping not found with ID: " + mappingId));

        if (request.occupationName() != null && !request.occupationName().isBlank()) {
            mapping.setOccupationName(request.occupationName());
        }
        if (request.riskLevel() != null && !request.riskLevel().isBlank()) {
            mapping.setRiskLevel(request.riskLevel().toUpperCase());
        }
        if (request.status() != null && !request.status().isBlank()) {
            mapping.setStatus(request.status().toUpperCase());
        }

        return mapToResponse(mappingRepository.save(mapping));
    }

    @Transactional
    @CacheEvict(value = CacheConfig.OCCUPATION_RISK_CACHE, allEntries = true)
    public OccupationRiskMappingResponse updateMappingStatus(UUID mappingId, String status) {
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("Status cannot be null or empty");
        }

        OccupationRiskMapping mapping = mappingRepository.findById(mappingId)
                .orElseThrow(() -> new IllegalArgumentException("Occupation mapping not found with ID: " + mappingId));

        mapping.setStatus(status.toUpperCase());
        return mapToResponse(mappingRepository.save(mapping));
    }

    private OccupationRiskMappingResponse mapToResponse(OccupationRiskMapping mapping) {
        return new OccupationRiskMappingResponse(
                mapping.getMappingId(),
                mapping.getProduct().getProductId(),
                mapping.getProduct().getProductType(),
                mapping.getOccupationCode(),
                mapping.getOccupationName(),
                mapping.getRiskLevel(),
                mapping.getStatus(),
                mapping.getCreatedAt(),
                mapping.getUpdatedAt()
        );
    }
}
