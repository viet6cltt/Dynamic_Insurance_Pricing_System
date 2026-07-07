package com.insurance.productservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.productservice.dto.CreateRiskInputSchemaRequest;
import com.insurance.productservice.dto.UpdateRiskInputSchemaRequest;
import com.insurance.productservice.model.InsuranceProduct;
import com.insurance.productservice.model.RiskInputSchema;
import com.insurance.productservice.repository.InsuranceProductRepository;
import com.insurance.productservice.repository.RiskInputSchemaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RiskInputSchemaServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private RiskInputSchemaRepository schemaRepository;

    @Mock
    private InsuranceProductRepository productRepository;

    @InjectMocks
    private RiskInputSchemaService service;

    @Test
    void getRiskInputSchemaByProductTypeNormalizesProductType() {
        RiskInputSchema schema = schema(product());
        when(schemaRepository.findFirstByProductProductTypeAndStatusOrderByCreatedAtDesc("HEALTH", "ACTIVE"))
                .thenReturn(Optional.of(schema));

        var response = service.getRiskInputSchemaByProductType("health");

        assertEquals("v1", response.schemaVersion());
    }

    @Test
    void createRiskInputSchemaDefaultsStatus() {
        InsuranceProduct product = product();
        when(productRepository.findById(product.getProductId())).thenReturn(Optional.of(product));
        when(schemaRepository.save(any(RiskInputSchema.class))).thenAnswer(invocation -> {
            RiskInputSchema schema = invocation.getArgument(0);
            schema.setSchemaId(UUID.randomUUID());
            return schema;
        });

        var response = service.createRiskInputSchema(product.getProductId(), new CreateRiskInputSchemaRequest(
                "v2", objectMapper.createObjectNode().put("age", "number"), null));

        assertEquals("v2", response.schemaVersion());
        assertEquals("ACTIVE", response.status());
    }

    @Test
    void createRiskInputSchemaRejectsMissingDefinition() {
        InsuranceProduct product = product();
        when(productRepository.findById(product.getProductId())).thenReturn(Optional.of(product));

        assertThrows(IllegalArgumentException.class, () -> service.createRiskInputSchema(
                product.getProductId(), new CreateRiskInputSchemaRequest("v1", null, null)));
    }

    @Test
    void updateRiskInputSchemaAppliesProvidedValues() {
        RiskInputSchema schema = schema(product());
        when(schemaRepository.findById(schema.getSchemaId())).thenReturn(Optional.of(schema));
        when(schemaRepository.save(schema)).thenReturn(schema);

        var response = service.updateRiskInputSchema(schema.getSchemaId(), new UpdateRiskInputSchemaRequest(
                "v3", objectMapper.createObjectNode().put("bmi", "number"), "inactive"));

        assertEquals("v3", response.schemaVersion());
        assertEquals("INACTIVE", response.status());
        assertEquals("number", response.schemaDefinition().get("bmi").asText());
    }

    @Test
    void updateRiskInputSchemaStatusRejectsBlankStatus() {
        assertThrows(IllegalArgumentException.class,
                () -> service.updateRiskInputSchemaStatus(UUID.randomUUID(), " "));
    }

    private InsuranceProduct product() {
        return InsuranceProduct.builder()
                .productId(UUID.randomUUID())
                .productType("HEALTH")
                .name("Health Insurance")
                .status("ACTIVE")
                .build();
    }

    private RiskInputSchema schema(InsuranceProduct product) {
        return RiskInputSchema.builder()
                .schemaId(UUID.randomUUID())
                .product(product)
                .schemaVersion("v1")
                .schemaDefinition(objectMapper.createObjectNode().put("age", "number"))
                .status("ACTIVE")
                .build();
    }
}
