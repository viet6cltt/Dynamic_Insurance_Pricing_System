package com.insurance.productservice.dto;

public record CreateInsuranceProductRequest(
    String productType,
    String name,
    String description,
    String status,
    String imageUrl
) {}
