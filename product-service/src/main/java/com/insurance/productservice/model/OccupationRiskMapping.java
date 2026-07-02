package com.insurance.productservice.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "occupation_risk_mappings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OccupationRiskMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "mapping_id", updatable = false, nullable = false)
    private UUID mappingId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private InsuranceProduct product;

    @Column(name = "occupation_code", nullable = false)
    private String occupationCode;

    @Column(name = "occupation_name", nullable = false)
    private String occupationName;

    @Column(name = "risk_level", nullable = false, length = 50)
    private String riskLevel;

    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "ACTIVE";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
