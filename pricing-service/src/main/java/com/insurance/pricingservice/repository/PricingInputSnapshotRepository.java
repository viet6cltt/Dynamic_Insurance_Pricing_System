package com.insurance.pricingservice.repository;

import com.insurance.pricingservice.model.PricingInputSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PricingInputSnapshotRepository extends JpaRepository<PricingInputSnapshot, UUID> {
    Optional<PricingInputSnapshot> findByQuoteQuoteId(UUID quoteId);
}
