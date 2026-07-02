package com.insurance.productservice.repository;

import com.insurance.productservice.model.RiskInputSchema;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RiskInputSchemaRepository extends JpaRepository<RiskInputSchema, UUID> {
    Optional<RiskInputSchema> findFirstByProductProductIdAndStatusOrderByCreatedAtDesc(UUID productId, String status);
    Optional<RiskInputSchema> findFirstByProductProductTypeAndStatusOrderByCreatedAtDesc(String productType, String status);
    Optional<RiskInputSchema> findFirstByProductProductIdOrderByCreatedAtDesc(UUID productId);
}
