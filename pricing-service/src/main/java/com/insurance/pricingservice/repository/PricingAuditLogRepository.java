package com.insurance.pricingservice.repository;

import com.insurance.pricingservice.model.PricingAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PricingAuditLogRepository extends JpaRepository<PricingAuditLog, UUID> {
    List<PricingAuditLog> findByQuoteQuoteIdOrderByCreatedAtDesc(UUID quoteId);
}
