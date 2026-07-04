package com.insurance.notificationservice.repository;

import com.insurance.notificationservice.model.EmailDelivery;
import com.insurance.notificationservice.model.EmailDeliveryStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface EmailDeliveryRepository extends JpaRepository<EmailDelivery, UUID> {
    List<EmailDelivery> findByStatusOrderByCreatedAtDesc(EmailDeliveryStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select d from EmailDelivery d
            join fetch d.notification
            where d.status = com.insurance.notificationservice.model.EmailDeliveryStatus.PENDING
              and (d.nextAttemptAt is null or d.nextAttemptAt <= :now)
            order by d.createdAt asc
            """)
    List<EmailDelivery> findPendingForUpdate(@Param("now") Instant now, Pageable pageable);
}
