package com.insurance.productservice.service;

import com.insurance.productservice.dto.CreateInsuranceProductRequest;
import com.insurance.productservice.dto.UpdateInsuranceProductRequest;
import com.insurance.productservice.model.InsuranceProduct;
import com.insurance.productservice.repository.InsuranceProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InsuranceProductServiceTest {

    @Mock
    private InsuranceProductRepository productRepository;

    @InjectMocks
    private InsuranceProductService service;

    @Test
    void getProductsUsesCombinedFilterWhenTypeAndStatusProvided() {
        InsuranceProduct product = product();
        when(productRepository.findByProductTypeAndStatus(eq("HEALTH"), eq("ACTIVE"), any()))
                .thenReturn(new PageImpl<>(List.of(product)));

        var response = service.getProducts("health", "active", 0, 20);

        assertEquals(1, response.items().size());
        assertEquals("HEALTH", response.items().getFirst().productType());
    }

    @Test
    void createProductNormalizesTypeAndDefaultStatus() {
        when(productRepository.save(any(InsuranceProduct.class))).thenAnswer(invocation -> {
            InsuranceProduct product = invocation.getArgument(0);
            product.setProductId(UUID.randomUUID());
            return product;
        });

        var response = service.createProduct(new CreateInsuranceProductRequest(
                "health", "Health Insurance", "Desc", null, "image.png"));

        assertEquals("HEALTH", response.productType());
        assertEquals("ACTIVE", response.status());
    }

    @Test
    void createProductRejectsMissingProductType() {
        assertThrows(IllegalArgumentException.class,
                () -> service.createProduct(new CreateInsuranceProductRequest("", "Health", null, null, null)));
    }

    @Test
    void getProductByIdThrowsWhenMissing() {
        UUID productId = UUID.randomUUID();
        when(productRepository.findById(productId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.getProductById(productId));
    }

    @Test
    void updateProductOnlyAppliesPresentFields() {
        UUID productId = UUID.randomUUID();
        InsuranceProduct product = product();
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);

        var response = service.updateProduct(productId, new UpdateInsuranceProductRequest(
                "Updated Health", null, "inactive", "new.png"));

        assertEquals("Updated Health", response.name());
        assertEquals("HEALTH", response.productType());
        assertEquals("INACTIVE", response.status());
        assertEquals("new.png", response.imageUrl());
    }

    @Test
    void updateProductStatusRejectsBlankStatus() {
        assertThrows(IllegalArgumentException.class,
                () -> service.updateProductStatus(UUID.randomUUID(), " "));
    }

    private InsuranceProduct product() {
        return InsuranceProduct.builder()
                .productId(UUID.randomUUID())
                .productType("HEALTH")
                .name("Health Insurance")
                .description("Desc")
                .imageUrl("image.png")
                .status("ACTIVE")
                .build();
    }
}
