package com.insurance.productservice.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "risk_input_schemas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RiskInputSchema {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "schema_id", updatable = false, nullable = false)
    private UUID schemaId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private InsuranceProduct product;

    @Column(name = "schema_version", nullable = false)
    private String schemaVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "schema_definition", nullable = false)
    private JsonNode schemaDefinition;

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
