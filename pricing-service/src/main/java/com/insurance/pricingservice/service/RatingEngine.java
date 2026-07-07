package com.insurance.pricingservice.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class RatingEngine {

    public static final int MONEY_SCALE = 2;
    public static final int FREQUENCY_SCALE = 6;
    public static final int RATE_SCALE = 4;
    public static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    public BigDecimal calculatePurePremium(BigDecimal predictedFrequency, BigDecimal predictedSeverity) {
        if (predictedFrequency == null) {
            throw new IllegalArgumentException("Predicted frequency cannot be null");
        }
        if (predictedSeverity == null) {
            throw new IllegalArgumentException("Predicted severity cannot be null");
        }
        if (predictedFrequency.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Predicted frequency cannot be negative");
        }
        if (predictedSeverity.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Predicted severity cannot be negative");
        }
        return predictedFrequency.multiply(predictedSeverity).setScale(MONEY_SCALE, ROUNDING_MODE);
    }

    public BigDecimal calculateFinalPremium(BigDecimal purePremium, BigDecimal loadingRate) {
        if (purePremium == null) {
            throw new IllegalArgumentException("Pure premium cannot be null");
        }
        if (loadingRate == null) {
            throw new IllegalArgumentException("Loading rate cannot be null");
        }
        if (purePremium.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Pure premium cannot be negative");
        }
        if (loadingRate.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Loading rate cannot be negative");
        }
        return purePremium.multiply(BigDecimal.ONE.add(loadingRate)).setScale(MONEY_SCALE, ROUNDING_MODE);
    }
}
