package com.insurance.pricingservice.repository;

import com.insurance.pricingservice.model.PremiumQuote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PremiumQuoteRepository extends JpaRepository<PremiumQuote, UUID> {
    List<PremiumQuote> findByBuyerUserIdOrderByCreatedAtDesc(UUID buyerUserId);
}
