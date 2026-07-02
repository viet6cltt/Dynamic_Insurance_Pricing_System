package com.insurance.productservice.dto;

import java.util.List;

public record ListResponse<T>(
    List<T> items
) {}
