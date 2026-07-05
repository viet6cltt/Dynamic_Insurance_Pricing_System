package com.insurance.productservice.service;

import com.insurance.productservice.dto.CreateInsuranceProductRequest;
import com.insurance.productservice.dto.InsuranceProductResponse;
import com.insurance.productservice.dto.PagedResponse;
import com.insurance.productservice.dto.UpdateInsuranceProductRequest;
import com.insurance.productservice.model.InsuranceProduct;
import com.insurance.productservice.repository.InsuranceProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InsuranceProductService {

    private final InsuranceProductRepository productRepository;

    @Transactional(readOnly = true)
    public PagedResponse<InsuranceProductResponse> getProducts(String productType, String status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<InsuranceProduct> productPage;

        if (productType != null && !productType.isBlank() && status != null && !status.isBlank()) {
            productPage = productRepository.findByProductTypeAndStatus(productType.toUpperCase(), status.toUpperCase(), pageable);
        } else if (productType != null && !productType.isBlank()) {
            productPage = productRepository.findByProductType(productType.toUpperCase(), pageable);
        } else if (status != null && !status.isBlank()) {
            productPage = productRepository.findByStatus(status.toUpperCase(), pageable);
        } else {
            productPage = productRepository.findAll(pageable);
        }

        return new PagedResponse<>(
                productPage.getContent().stream().map(this::mapToResponse).toList(),
                productPage.getNumber(),
                productPage.getSize(),
                productPage.getTotalElements(),
                productPage.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public InsuranceProductResponse getProductById(UUID productId) {
        return productRepository.findById(productId)
                .map(this::mapToResponse)
                .orElseThrow(() -> new IllegalArgumentException("Insurance product not found with ID: " + productId));
    }

    @Transactional
    public InsuranceProductResponse createProduct(CreateInsuranceProductRequest request) {
        if (request.productType() == null || request.productType().isBlank()) {
            throw new IllegalArgumentException("Product type cannot be null or empty");
        }
        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("Product name cannot be null or empty");
        }

        InsuranceProduct product = InsuranceProduct.builder()
                .productType(request.productType().toUpperCase())
                .name(request.name())
                .description(request.description())
                .imageUrl(request.imageUrl())
                .status(request.status() != null ? request.status().toUpperCase() : "ACTIVE")
                .build();

        return mapToResponse(productRepository.save(product));
    }

    @Transactional
    public InsuranceProductResponse updateProduct(UUID productId, UpdateInsuranceProductRequest request) {
        InsuranceProduct product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Insurance product not found with ID: " + productId));

        if (request.name() != null && !request.name().isBlank()) {
            product.setName(request.name());
        }
        if (request.description() != null) {
            product.setDescription(request.description());
        }
        if (request.status() != null && !request.status().isBlank()) {
            product.setStatus(request.status().toUpperCase());
        }
        if (request.imageUrl() != null) {
            product.setImageUrl(request.imageUrl());
        }

        return mapToResponse(productRepository.save(product));
    }

    @Transactional
    public InsuranceProductResponse updateProductStatus(UUID productId, String status) {
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("Status cannot be null or empty");
        }

        InsuranceProduct product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Insurance product not found with ID: " + productId));

        product.setStatus(status.toUpperCase());
        return mapToResponse(productRepository.save(product));
    }

    @Transactional
    public InsuranceProductResponse updateProductImageUrl(UUID productId, String imageUrl) {
        InsuranceProduct product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Insurance product not found with ID: " + productId));

        product.setImageUrl(imageUrl);
        return mapToResponse(productRepository.save(product));
    }

    private InsuranceProductResponse mapToResponse(InsuranceProduct p) {
        return new InsuranceProductResponse(
                p.getProductId(),
                p.getProductType(),
                p.getName(),
                p.getDescription(),
                p.getStatus(),
                p.getImageUrl(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }
}
