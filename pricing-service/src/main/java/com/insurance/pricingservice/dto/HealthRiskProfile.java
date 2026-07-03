package com.insurance.pricingservice.dto;

public record HealthRiskProfile(
    double age,
    String sex,
    double bmi,
    int children,
    String smoker,
    double bloodPressure,
    String exerciseFrequency,
    boolean preExistingCondition,
    String occupationRisk
) {}
