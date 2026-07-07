package com.insurance.productservice.service;

import com.insurance.productservice.dto.CreateOccupationRiskMappingRequest;
import com.insurance.productservice.dto.UpdateOccupationRiskMappingRequest;
import com.insurance.productservice.model.InsuranceProduct;
import com.insurance.productservice.model.OccupationRiskMapping;
import com.insurance.productservice.repository.InsuranceProductRepository;
import com.insurance.productservice.repository.OccupationRiskMappingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OccupationRiskMappingServiceTest {

    @Mock
    private OccupationRiskMappingRepository mappingRepository;

    @Mock
    private InsuranceProductRepository productRepository;

    @InjectMocks
    private OccupationRiskMappingService service;

    @Test
    void resolveOccupationRiskNormalizesLookupKeys() {
        OccupationRiskMapping mapping = mapping(product());
        when(mappingRepository.findByProductProductTypeAndOccupationCodeAndStatus("HEALTH", "DEV", "ACTIVE"))
                .thenReturn(Optional.of(mapping));

        var response = service.resolveOccupationRisk("health", "dev");

        assertEquals("HIGH", response.riskLevel());
        assertEquals("DEV", response.occupationCode());
    }

    @Test
    void createMappingNormalizesCodeRiskAndDefaultStatus() {
        InsuranceProduct product = product();
        when(productRepository.findById(product.getProductId())).thenReturn(Optional.of(product));
        when(mappingRepository.save(any(OccupationRiskMapping.class))).thenAnswer(invocation -> {
            OccupationRiskMapping mapping = invocation.getArgument(0);
            mapping.setMappingId(UUID.randomUUID());
            return mapping;
        });

        var response = service.createMapping(product.getProductId(), new CreateOccupationRiskMappingRequest(
                "dev", "Developer", "high", null));

        assertEquals("DEV", response.occupationCode());
        assertEquals("HIGH", response.riskLevel());
        assertEquals("ACTIVE", response.status());
    }

    @Test
    void getMappingsByProductTypeDefaultsStatusToActive() {
        when(mappingRepository.findByProductProductTypeAndStatus(eq("HEALTH"), eq("ACTIVE")))
                .thenReturn(List.of(mapping(product())));

        var response = service.getMappingsByProductType("health", null);

        assertEquals(1, response.items().size());
    }

    @Test
    void updateMappingAppliesEditableFields() {
        OccupationRiskMapping mapping = mapping(product());
        when(mappingRepository.findById(mapping.getMappingId())).thenReturn(Optional.of(mapping));
        when(mappingRepository.save(mapping)).thenReturn(mapping);

        var response = service.updateMapping(mapping.getMappingId(),
                new UpdateOccupationRiskMappingRequest("Engineer", "medium", "inactive"));

        assertEquals("Engineer", response.occupationName());
        assertEquals("MEDIUM", response.riskLevel());
        assertEquals("INACTIVE", response.status());
    }

    @Test
    void resolveOccupationRiskRejectsBlankOccupationCode() {
        assertThrows(IllegalArgumentException.class, () -> service.resolveOccupationRisk("HEALTH", " "));
    }

    private InsuranceProduct product() {
        return InsuranceProduct.builder()
                .productId(UUID.randomUUID())
                .productType("HEALTH")
                .name("Health Insurance")
                .status("ACTIVE")
                .build();
    }

    private OccupationRiskMapping mapping(InsuranceProduct product) {
        return OccupationRiskMapping.builder()
                .mappingId(UUID.randomUUID())
                .product(product)
                .occupationCode("DEV")
                .occupationName("Developer")
                .riskLevel("HIGH")
                .status("ACTIVE")
                .build();
    }
}
