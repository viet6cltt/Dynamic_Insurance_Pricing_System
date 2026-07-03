package com.insurance.pricingservice.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "premium_quotes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PremiumQuote {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "quote_id", updatable = false, nullable = false)
    private UUID quoteId;

    @Column(name = "buyer_user_id", nullable = false)
    private UUID buyerUserId;

    @Column(name = "insured_person_id", nullable = false)
    private UUID insuredPersonId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "coverage_plan_id", nullable = false)
    private UUID coveragePlanId;

    @Column(name = "product_type", nullable = false)
    private String productType;

    @Column(name = "plan_name", nullable = false)
    private String planName;

    @Column(name = "base_premium", nullable = false, precision = 15, scale = 2)
    private BigDecimal basePremium;

    @Column(name = "sum_insured", nullable = false, precision = 15, scale = 2)
    private BigDecimal sumInsured;

    @Column(name = "predicted_annual_claim_cost", precision = 15, scale = 2)
    private BigDecimal predictedAnnualClaimCost;

    @Column(name = "predicted_health_cost", precision = 15, scale = 2)
    private BigDecimal predictedHealthCost;

    @Column(name = "baseline_health_cost", precision = 15, scale = 2)
    private BigDecimal baselineHealthCost;

    @Column(name = "raw_portfolio_risk_factor", precision = 10, scale = 4)
    private BigDecimal rawPortfolioRiskFactor;

    @Column(name = "portfolio_risk_factor", precision = 10, scale = 4)
    private BigDecimal portfolioRiskFactor;

    @Column(name = "raw_health_risk_factor", precision = 10, scale = 4)
    private BigDecimal rawHealthRiskFactor;

    @Column(name = "health_risk_factor", precision = 10, scale = 4)
    private BigDecimal healthRiskFactor;

    @Column(name = "underwriting_adjustment_factor", nullable = false, precision = 10, scale = 4)
    @Builder.Default
    private BigDecimal underwritingAdjustmentFactor = BigDecimal.ONE;

    @Column(name = "business_adjustment_factor", nullable = false, precision = 10, scale = 4)
    @Builder.Default
    private BigDecimal businessAdjustmentFactor = BigDecimal.ONE;

    @Column(name = "final_premium", nullable = false, precision = 15, scale = 2)
    private BigDecimal finalPremium;

    @Column(name = "risk_level", length = 50)
    private String riskLevel;

    @Column(name = "portfolio_model_version")
    private String portfolioModelVersion;

    @Column(name = "health_model_version")
    private String healthModelVersion;

    @Column(name = "pricing_rule_version")
    private String pricingRuleVersion;

    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "GENERATED";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expired_at", nullable = false)
    private Instant expiredAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
