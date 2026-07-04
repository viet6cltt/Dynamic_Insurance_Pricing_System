package com.insurance.notificationservice.repository;

import com.insurance.notificationservice.model.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {
    boolean existsByEventIdAndConsumerName(UUID eventId, String consumerName);
}
