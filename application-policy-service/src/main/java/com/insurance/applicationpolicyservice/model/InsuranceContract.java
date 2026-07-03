package com.insurance.applicationpolicyservice.model;

import com.insurance.applicationpolicyservice.dto.ContractStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "insurance_contracts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InsuranceContract {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "contract_id")
    private UUID contractId;

    @Column(name = "applicant_user_id", nullable = false)
    private UUID applicantUserId;

    @Column(name = "insured_person_id", nullable = false)
    private UUID insuredPersonId;

    @Column(name = "quote_id", nullable = false, unique = true)
    private UUID quoteId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "coverage_plan_id", nullable = false)
    private UUID coveragePlanId;

    @Column(name = "product_type", nullable = false)
    private String productType;

    @Column(name = "type_policy")
    private String typePolicy;

    @Column(name = "reimbursement")
    private String reimbursement;

    @Column(name = "distribution_channel")
    private String distributionChannel;

    @Column(name = "quoted_premium", nullable = false)
    private BigDecimal quotedPremium;

    @Column(name = "base_premium", nullable = false)
    private BigDecimal basePremium;

    @Column(name = "sum_insured", nullable = false)
    private BigDecimal sumInsured;

    @Column(name = "effective_date")
    private LocalDate effectiveDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "exposure_time")
    private BigDecimal exposureTime;

    @Column(name = "new_business")
    private Boolean newBusiness;

    @Column(name = "policy_year")
    private Integer policyYear;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ContractStatus status;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "issued_at")
    private Instant issuedAt;

    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "payment_status", length = 30)
    private String paymentStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
