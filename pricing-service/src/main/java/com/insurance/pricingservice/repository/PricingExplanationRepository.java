package com.insurance.pricingservice.repository;

import com.insurance.pricingservice.model.PricingExplanation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PricingExplanationRepository extends JpaRepository<PricingExplanation, UUID> {
    Optional<PricingExplanation> findByQuoteQuoteId(UUID quoteId);
}
