package com.insurance.productservice.dto;

public record UpdateInsuranceProductRequest(
    String name,
    String description,
    String status,
    String imageUrl
) {}
