package com.insurance.productservice.repository;

import com.insurance.productservice.model.CoveragePlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CoveragePlanRepository extends JpaRepository<CoveragePlan, UUID> {
    List<CoveragePlan> findByProductProductId(UUID productId);
    List<CoveragePlan> findByProductProductIdAndStatus(UUID productId, String status);
}
