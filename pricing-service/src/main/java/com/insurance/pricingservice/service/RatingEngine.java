package com.insurance.pricingservice.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class RatingEngine {

    public BigDecimal calculateFinalPremium(
            BigDecimal basePremium,
            BigDecimal portfolioRiskFactor,
            BigDecimal healthRiskFactor,
            BigDecimal underwritingAdjustmentFactor,
            BigDecimal businessAdjustmentFactor) {

        if (basePremium == null) {
            throw new IllegalArgumentException("Base premium cannot be null");
        }

        BigDecimal pFactor = portfolioRiskFactor != null ? portfolioRiskFactor : BigDecimal.ONE;
        BigDecimal hFactor = healthRiskFactor != null ? healthRiskFactor : BigDecimal.ONE;
        BigDecimal uFactor = underwritingAdjustmentFactor != null ? underwritingAdjustmentFactor : BigDecimal.ONE;
        BigDecimal bFactor = businessAdjustmentFactor != null ? businessAdjustmentFactor : BigDecimal.ONE;

        // Formula: basePremium * portfolioRiskFactor * healthRiskFactor * underwritingAdjustmentFactor * businessAdjustmentFactor
        BigDecimal finalPremium = basePremium
                .multiply(pFactor)
                .multiply(hFactor)
                .multiply(uFactor)
                .multiply(bFactor);

        // Apply minimum premium limit of 0.00
        if (finalPremium.compareTo(BigDecimal.ZERO) < 0) {
            finalPremium = BigDecimal.ZERO;
        }

        // Round to 2 decimal places
        return finalPremium.setScale(2, RoundingMode.HALF_UP);
    }
}
