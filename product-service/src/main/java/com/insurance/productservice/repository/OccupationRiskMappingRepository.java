package com.insurance.productservice.repository;

import com.insurance.productservice.model.OccupationRiskMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OccupationRiskMappingRepository extends JpaRepository<OccupationRiskMapping, UUID> {
    List<OccupationRiskMapping> findByProductProductId(UUID productId);
    List<OccupationRiskMapping> findByProductProductIdAndStatus(UUID productId, String status);
    List<OccupationRiskMapping> findByProductProductTypeAndStatus(String productType, String status);
    Optional<OccupationRiskMapping> findByProductProductTypeAndOccupationCodeAndStatus(String productType, String occupationCode, String status);
}
