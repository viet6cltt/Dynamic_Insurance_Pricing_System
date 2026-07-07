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

    @Column(name = "sum_insured", nullable = false, precision = 15, scale = 2)
    private BigDecimal sumInsured;

    @Column(name = "predicted_frequency", nullable = false, precision = 15, scale = 6)
    private BigDecimal predictedFrequency;

    @Column(name = "predicted_severity", nullable = false, precision = 15, scale = 2)
    private BigDecimal predictedSeverity;

    @Column(name = "pure_premium", nullable = false, precision = 15, scale = 2)
    private BigDecimal purePremium;

    @Column(name = "loading_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal loadingRate;

    @Column(name = "final_premium", nullable = false, precision = 15, scale = 2)
    private BigDecimal finalPremium;

    @Column(name = "risk_level", length = 50)
    private String riskLevel;

    @Column(name = "frequency_model_version")
    private String frequencyModelVersion;

    @Column(name = "severity_model_version")
    private String severityModelVersion;

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
