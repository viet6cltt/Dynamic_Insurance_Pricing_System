package com.insurance.applicationpolicyservice.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "claim_experience_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClaimExperienceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "record_id")
    private UUID recordId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id")
    private InsuranceContract contract;

    @Column(name = "insured_person_id", nullable = false)
    private UUID insuredPersonId;

    @Column(name = "policyholder_user_id", nullable = false)
    private UUID policyholderUserId;

    @Column(name = "product_type", nullable = false)
    private String productType;

    @Column(name = "experience_date", nullable = false)
    private LocalDate experienceDate;

    @Column(name = "claim_amount", nullable = false)
    private BigDecimal claimAmount;

    @Column(name = "n_medical_services", nullable = false)
    private Integer nMedicalServices;

    @Column(name = "had_claim_or_service", nullable = false)
    private Boolean hadClaimOrService;

    @Column(name = "severity_level")
    private String severityLevel;

    @Column(name = "source")
    private String source;

    @Column(name = "imported_at", nullable = false, updatable = false)
    private Instant importedAt;

    @PrePersist
    protected void onCreate() {
        this.importedAt = Instant.now();
    }
}
