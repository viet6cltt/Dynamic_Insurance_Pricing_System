package com.insurance.productservice.service;

import com.insurance.productservice.config.CacheConfig;
import com.insurance.productservice.dto.CreateCoveragePlanRequest;
import com.insurance.productservice.dto.CoveragePlanResponse;
import com.insurance.productservice.dto.InternalCoveragePlanResponse;
import com.insurance.productservice.dto.ListResponse;
import com.insurance.productservice.dto.UpdateLoadingRateRequest;
import com.insurance.productservice.dto.UpdateCoveragePlanRequest;
import com.insurance.productservice.model.CoveragePlan;
import com.insurance.productservice.model.InsuranceProduct;
import com.insurance.productservice.repository.CoveragePlanRepository;
import com.insurance.productservice.repository.InsuranceProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CoveragePlanService {

    private final CoveragePlanRepository planRepository;
    private final InsuranceProductRepository productRepository;

    @Transactional(readOnly = true)
    public ListResponse<CoveragePlanResponse> getCoveragePlansByProduct(UUID productId, String status) {
        List<CoveragePlan> plans;
        if (status != null && !status.isBlank()) {
            plans = planRepository.findByProductProductIdAndStatus(productId, status.toUpperCase());
        } else {
            plans = planRepository.findByProductProductId(productId);
        }

        return new ListResponse<>(plans.stream().map(this::mapToResponse).toList());
    }

    @Transactional(readOnly = true)
    @Cacheable(value = CacheConfig.COVERAGE_PLAN_CACHE, key = "'public:' + #coveragePlanId")
    public CoveragePlanResponse getCoveragePlanById(UUID coveragePlanId) {
        return planRepository.findById(coveragePlanId)
                .map(this::mapToResponse)
                .orElseThrow(() -> new IllegalArgumentException("Coverage plan not found with ID: " + coveragePlanId));
    }

    @Transactional(readOnly = true)
    @Cacheable(value = CacheConfig.COVERAGE_PLAN_CACHE, key = "'internal:' + #coveragePlanId")
    public InternalCoveragePlanResponse getInternalCoveragePlan(UUID coveragePlanId) {
        return planRepository.findById(coveragePlanId)
                .map(this::mapToInternalResponse)
                .orElseThrow(() -> new IllegalArgumentException("Coverage plan not found with ID: " + coveragePlanId));
    }

    @Transactional
    public CoveragePlanResponse createCoveragePlan(UUID productId, CreateCoveragePlanRequest request) {
        InsuranceProduct product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Insurance product not found with ID: " + productId));

        if (request.planName() == null || request.planName().isBlank()) {
            throw new IllegalArgumentException("Plan name cannot be null or empty");
        }
        if (request.sumInsured() == null) {
            throw new IllegalArgumentException("Sum insured cannot be null");
        }
        validateLoadingRate(request.loadingRate());

        CoveragePlan plan = CoveragePlan.builder()
                .product(product)
                .planName(request.planName())
                .description(request.description())
                .sumInsured(request.sumInsured())
                .loadingRate(request.loadingRate())
                .reimbursementEnabled(request.reimbursementEnabled() != null ? request.reimbursementEnabled() : false)
                .status(request.status() != null ? request.status().toUpperCase() : "ACTIVE")
                .build();

        return mapToResponse(planRepository.save(plan));
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheConfig.COVERAGE_PLAN_CACHE, key = "'public:' + #coveragePlanId"),
            @CacheEvict(value = CacheConfig.COVERAGE_PLAN_CACHE, key = "'internal:' + #coveragePlanId")
    })
    public CoveragePlanResponse updateCoveragePlan(UUID coveragePlanId, UpdateCoveragePlanRequest request) {
        CoveragePlan plan = planRepository.findById(coveragePlanId)
                .orElseThrow(() -> new IllegalArgumentException("Coverage plan not found with ID: " + coveragePlanId));

        if (request.planName() != null && !request.planName().isBlank()) {
            plan.setPlanName(request.planName());
        }
        if (request.description() != null) {
            plan.setDescription(request.description());
        }
        if (request.sumInsured() != null) {
            plan.setSumInsured(request.sumInsured());
        }
        if (request.loadingRate() != null) {
            validateLoadingRate(request.loadingRate());
            plan.setLoadingRate(request.loadingRate());
        }
        if (request.reimbursementEnabled() != null) {
            plan.setReimbursementEnabled(request.reimbursementEnabled());
        }
        if (request.status() != null && !request.status().isBlank()) {
            plan.setStatus(request.status().toUpperCase());
        }

        return mapToResponse(planRepository.save(plan));
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheConfig.COVERAGE_PLAN_CACHE, key = "'public:' + #coveragePlanId"),
            @CacheEvict(value = CacheConfig.COVERAGE_PLAN_CACHE, key = "'internal:' + #coveragePlanId")
    })
    public CoveragePlanResponse updateCoveragePlanStatus(UUID coveragePlanId, String status) {
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("Status cannot be null or empty");
        }

        CoveragePlan plan = planRepository.findById(coveragePlanId)
                .orElseThrow(() -> new IllegalArgumentException("Coverage plan not found with ID: " + coveragePlanId));

        plan.setStatus(status.toUpperCase());
        return mapToResponse(planRepository.save(plan));
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheConfig.COVERAGE_PLAN_CACHE, key = "'public:' + #coveragePlanId"),
            @CacheEvict(value = CacheConfig.COVERAGE_PLAN_CACHE, key = "'internal:' + #coveragePlanId")
    })
    public CoveragePlanResponse updateLoadingRate(UUID coveragePlanId, UpdateLoadingRateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Loading rate request cannot be null");
        }
        validateLoadingRate(request.loadingRate());

        CoveragePlan plan = planRepository.findById(coveragePlanId)
                .orElseThrow(() -> new IllegalArgumentException("Coverage plan not found with ID: " + coveragePlanId));

        plan.setLoadingRate(request.loadingRate());
        return mapToResponse(planRepository.save(plan));
    }

    private void validateLoadingRate(BigDecimal loadingRate) {
        if (loadingRate == null) {
            throw new IllegalArgumentException("Loading rate cannot be null");
        }
        if (loadingRate.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Loading rate cannot be negative");
        }
        if (loadingRate.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("Loading rate cannot exceed 1.00");
        }
    }

    private CoveragePlanResponse mapToResponse(CoveragePlan plan) {
        return new CoveragePlanResponse(
                plan.getCoveragePlanId(),
                plan.getProduct().getProductId(),
                plan.getProduct().getProductType(),
                plan.getPlanName(),
                plan.getDescription(),
                plan.getSumInsured(),
                plan.getLoadingRate(),
                plan.getReimbursementEnabled(),
                plan.getStatus(),
                plan.getCreatedAt(),
                plan.getUpdatedAt()
        );
    }

    private InternalCoveragePlanResponse mapToInternalResponse(CoveragePlan plan) {
        return new InternalCoveragePlanResponse(
                plan.getCoveragePlanId(),
                plan.getProduct().getProductId(),
                plan.getProduct().getProductType(),
                plan.getPlanName(),
                plan.getSumInsured(),
                plan.getLoadingRate(),
                plan.getReimbursementEnabled(),
                plan.getStatus()
        );
    }
}
