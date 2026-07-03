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
@Table(name = "pricing_input_snapshots")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PricingInputSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "snapshot_id", updatable = false, nullable = false)
    private UUID snapshotId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quote_id", nullable = false)
    private PremiumQuote quote;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "coverage_plan_snapshot")
    private JsonNode coveragePlanSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "insured_person_snapshot")
    private JsonNode insuredPersonSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "risk_profile_snapshot")
    private JsonNode riskProfileSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "claim_history_snapshot")
    private JsonNode claimHistorySnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ai_request_snapshot")
    private JsonNode aiRequestSnapshot;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
