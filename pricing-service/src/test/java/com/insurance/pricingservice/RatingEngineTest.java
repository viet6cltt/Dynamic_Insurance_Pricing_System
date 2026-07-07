package com.insurance.pricingservice;

import com.insurance.pricingservice.service.RatingEngine;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RatingEngineTest {

    private final RatingEngine ratingEngine = new RatingEngine();

    @Test
    void calculatesPurePremium() {
        BigDecimal purePremium = ratingEngine.calculatePurePremium(
                new BigDecimal("10.500000"),
                new BigDecimal("450000.00"));

        assertEquals(new BigDecimal("4725000.00"), purePremium);
    }

    @Test
    void calculatesFinalPremiumWithLoadingRate() {
        BigDecimal finalPremium = ratingEngine.calculateFinalPremium(
                new BigDecimal("5000000.00"),
                new BigDecimal("0.2000"));

        assertEquals(new BigDecimal("6000000.00"), finalPremium);
    }

    @Test
    void loadingRateZeroKeepsPurePremium() {
        BigDecimal finalPremium = ratingEngine.calculateFinalPremium(
                new BigDecimal("5000000.00"),
                BigDecimal.ZERO);

        assertEquals(new BigDecimal("5000000.00"), finalPremium);
    }

    @Test
    void frequencyZeroProducesZeroPurePremium() {
        BigDecimal purePremium = ratingEngine.calculatePurePremium(
                BigDecimal.ZERO,
                new BigDecimal("450000.00"));

        assertEquals(new BigDecimal("0.00"), purePremium);
    }

    @Test
    void roundsMoneyHalfUp() {
        BigDecimal purePremium = ratingEngine.calculatePurePremium(
                new BigDecimal("3.333333"),
                new BigDecimal("100.00"));

        assertEquals(new BigDecimal("333.33"), purePremium);
    }

    @Test
    void rejectsNegativeLoadingRate() {
        assertThrows(IllegalArgumentException.class,
                () -> ratingEngine.calculateFinalPremium(new BigDecimal("100.00"), new BigDecimal("-0.0100")));
    }

    @Test
    void rejectsNegativeSeverity() {
        assertThrows(IllegalArgumentException.class,
                () -> ratingEngine.calculatePurePremium(BigDecimal.ONE, new BigDecimal("-1.00")));
    }
}
