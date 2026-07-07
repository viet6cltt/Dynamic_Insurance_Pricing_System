package com.insurance.productservice.service;

import com.insurance.productservice.dto.CreateCoveragePlanRequest;
import com.insurance.productservice.dto.UpdateCoveragePlanRequest;
import com.insurance.productservice.dto.UpdateLoadingRateRequest;
import com.insurance.productservice.model.CoveragePlan;
import com.insurance.productservice.model.InsuranceProduct;
import com.insurance.productservice.repository.CoveragePlanRepository;
import com.insurance.productservice.repository.InsuranceProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CoveragePlanServiceTest {

    @Mock
    private CoveragePlanRepository planRepository;

    @Mock
    private InsuranceProductRepository productRepository;

    @InjectMocks
    private CoveragePlanService service;

    @Test
    void getCoveragePlansByProductFiltersByStatus() {
        UUID productId = UUID.randomUUID();
        when(planRepository.findByProductProductIdAndStatus(eq(productId), eq("ACTIVE")))
                .thenReturn(List.of(plan(product())));

        var response = service.getCoveragePlansByProduct(productId, "active");

        assertEquals(1, response.items().size());
        assertEquals("ACTIVE", response.items().getFirst().status());
    }

    @Test
    void getInternalCoveragePlanMapsPricingFields() {
        CoveragePlan plan = plan(product());
        when(planRepository.findById(plan.getCoveragePlanId())).thenReturn(Optional.of(plan));

        var response = service.getInternalCoveragePlan(plan.getCoveragePlanId());

        assertEquals(plan.getCoveragePlanId(), response.coveragePlanId());
        assertEquals(new BigDecimal("0.2000"), response.loadingRate());
    }

    @Test
    void createCoveragePlanDefaultsStatusAndValidatesLoadingRate() {
        InsuranceProduct product = product();
        when(productRepository.findById(product.getProductId())).thenReturn(Optional.of(product));
        when(planRepository.save(any(CoveragePlan.class))).thenAnswer(invocation -> {
            CoveragePlan plan = invocation.getArgument(0);
            plan.setCoveragePlanId(UUID.randomUUID());
            return plan;
        });

        var response = service.createCoveragePlan(product.getProductId(), new CreateCoveragePlanRequest(
                "Gold", "Gold desc", new BigDecimal("100000000.00"), new BigDecimal("0.1500"), true, null));

        assertEquals("Gold", response.planName());
        assertEquals("ACTIVE", response.status());
    }

    @Test
    void createCoveragePlanRejectsLoadingRateAboveOne() {
        InsuranceProduct product = product();
        when(productRepository.findById(product.getProductId())).thenReturn(Optional.of(product));

        assertThrows(IllegalArgumentException.class, () -> service.createCoveragePlan(
                product.getProductId(),
                new CreateCoveragePlanRequest("Gold", "Gold desc", BigDecimal.TEN, new BigDecimal("1.1000"), false, null)));
    }

    @Test
    void updateCoveragePlanUpdatesOnlyPresentFields() {
        CoveragePlan plan = plan(product());
        when(planRepository.findById(plan.getCoveragePlanId())).thenReturn(Optional.of(plan));
        when(planRepository.save(plan)).thenReturn(plan);

        var response = service.updateCoveragePlan(plan.getCoveragePlanId(), new UpdateCoveragePlanRequest(
                "Updated Gold", null, null, new BigDecimal("0.2500"), true, "inactive"));

        assertEquals("Updated Gold", response.planName());
        assertEquals(new BigDecimal("0.2500"), response.loadingRate());
        assertEquals("INACTIVE", response.status());
    }

    @Test
    void updateLoadingRateRejectsNullRequest() {
        assertThrows(IllegalArgumentException.class, () -> service.updateLoadingRate(UUID.randomUUID(), null));
    }

    @Test
    void updateCoveragePlanStatusRejectsBlankStatus() {
        assertThrows(IllegalArgumentException.class,
                () -> service.updateCoveragePlanStatus(UUID.randomUUID(), ""));
    }

    private InsuranceProduct product() {
        return InsuranceProduct.builder()
                .productId(UUID.randomUUID())
                .productType("HEALTH")
                .name("Health Insurance")
                .status("ACTIVE")
                .build();
    }

    private CoveragePlan plan(InsuranceProduct product) {
        return CoveragePlan.builder()
                .coveragePlanId(UUID.randomUUID())
                .product(product)
                .planName("Gold")
                .description("Gold desc")
                .sumInsured(new BigDecimal("100000000.00"))
                .loadingRate(new BigDecimal("0.2000"))
                .status("ACTIVE")
                .build();
    }
}
