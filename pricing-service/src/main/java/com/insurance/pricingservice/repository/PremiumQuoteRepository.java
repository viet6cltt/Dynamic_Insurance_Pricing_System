package com.insurance.pricingservice.repository;

import com.insurance.pricingservice.model.PremiumQuote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface PremiumQuoteRepository extends JpaRepository<PremiumQuote, UUID> {
    List<PremiumQuote> findByBuyerUserIdOrderByCreatedAtDesc(UUID buyerUserId);

    @Query("""
            select q from PremiumQuote q
            where q.status in ('GENERATED', 'ACCEPTED')
              and q.expiredAt < :now
            order by q.expiredAt asc
            """)
    List<PremiumQuote> findActiveExpiredQuotes(Instant now);
}
