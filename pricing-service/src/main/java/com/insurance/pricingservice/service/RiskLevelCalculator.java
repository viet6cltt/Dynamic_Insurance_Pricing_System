package com.insurance.pricingservice.service;

import com.insurance.pricingservice.dto.RiskLevel;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class RiskLevelCalculator {

    public RiskLevel calculateCombinedRiskLevel(BigDecimal portfolioRiskFactor, BigDecimal healthRiskFactor) {
        BigDecimal pFactor = portfolioRiskFactor != null ? portfolioRiskFactor : BigDecimal.ONE;
        BigDecimal hFactor = healthRiskFactor != null ? healthRiskFactor : BigDecimal.ONE;

        BigDecimal combinedFactor = pFactor.multiply(hFactor);
        double value = combinedFactor.doubleValue();

        if (value < 0.9) {
            return RiskLevel.LOW;
        } else if (value < 1.2) {
            return RiskLevel.STANDARD;
        } else if (value < 1.6) {
            return RiskLevel.MEDIUM;
        } else if (value < 2.2) {
            return RiskLevel.HIGH;
        } else {
            return RiskLevel.VERY_HIGH;
        }
    }
}
