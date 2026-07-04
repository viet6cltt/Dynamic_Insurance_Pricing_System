package com.insurance.pricingservice.repository;

import com.insurance.pricingservice.model.OutboxEvent;
import com.insurance.pricingservice.model.OutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(OutboxStatus status, Pageable pageable);
    List<OutboxEvent> findByEventType(String eventType);
}
