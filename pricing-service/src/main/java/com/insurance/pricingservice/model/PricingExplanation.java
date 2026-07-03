package com.insurance.pricingservice.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pricing_explanations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PricingExplanation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "explanation_id", updatable = false, nullable = false)
    private UUID explanationId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quote_id", nullable = false)
    private PremiumQuote quote;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "portfolio_explanation")
    private JsonNode portfolioExplanation;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "health_explanation")
    private JsonNode healthExplanation;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "top_risk_factors")
    private JsonNode topRiskFactors;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "shap_values")
    private JsonNode shapValues;

    @Column(name = "explanation_method")
    private String explanationMethod;

    @Column(name = "approximate")
    private Boolean approximate;

    @CreationTimestamp
    @Column(name = "generated_at", nullable = false, updatable = false)
    private Instant generatedAt;
}
