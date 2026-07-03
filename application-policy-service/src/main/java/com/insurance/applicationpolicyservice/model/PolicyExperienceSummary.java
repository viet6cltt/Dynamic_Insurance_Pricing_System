package com.insurance.applicationpolicyservice.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "policy_experience_summaries", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"insured_person_id", "product_type"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PolicyExperienceSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "summary_id")
    private UUID summaryId;

    @Column(name = "insured_person_id", nullable = false)
    private UUID insuredPersonId;

    @Column(name = "policyholder_user_id", nullable = false)
    private UUID policyholderUserId;

    @Column(name = "product_type", nullable = false)
    private String productType;

    @Column(name = "prev_cost_claims_year", nullable = false)
    private BigDecimal prevCostClaimsYear;

    @Column(name = "prev_n_medical_services", nullable = false)
    private Integer prevNMedicalServices;

    @Column(name = "prev_had_claim_or_service", nullable = false)
    private Boolean prevHadClaimOrService;

    @Column(name = "claim_free_previous_year", nullable = false)
    private Boolean claimFreePreviousYear;

    @Column(name = "total_past_claim_amount", nullable = false)
    private BigDecimal totalPastClaimAmount;

    @Column(name = "past_claim_count", nullable = false)
    private Integer pastClaimCount;

    @Column(name = "claim_free_years", nullable = false)
    private Integer claimFreeYears;

    @Column(name = "recency_weighted_claim_score", nullable = false)
    private BigDecimal recencyWeightedClaimScore;

    @Column(name = "last_claim_date")
    private LocalDate lastClaimDate;

    @Column(name = "claim_severity_level")
    private String claimSeverityLevel;

    @Column(name = "seniority_insured", nullable = false)
    private BigDecimal seniorityInsured;

    @Column(name = "active_policy_count", nullable = false)
    private Integer activePolicyCount;

    @Column(name = "completed_policy_count", nullable = false)
    private Integer completedPolicyCount;

    @Column(name = "calculated_at", nullable = false)
    private Instant calculatedAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        this.calculatedAt = Instant.now();
        this.updatedAt = this.calculatedAt;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
