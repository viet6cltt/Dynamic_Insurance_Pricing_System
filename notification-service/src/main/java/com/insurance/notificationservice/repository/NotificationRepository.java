package com.insurance.notificationservice.repository;

import com.insurance.notificationservice.model.Notification;
import com.insurance.notificationservice.model.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    Page<Notification> findByRecipientUserIdOrderByCreatedAtDesc(UUID recipientUserId, Pageable pageable);
    Page<Notification> findByRecipientUserIdAndStatusOrderByCreatedAtDesc(UUID recipientUserId, NotificationStatus status, Pageable pageable);
    long countByRecipientUserIdAndStatus(UUID recipientUserId, NotificationStatus status);

    @Modifying
    @Query("""
            update Notification n
            set n.status = com.insurance.notificationservice.model.NotificationStatus.READ,
                n.readAt = :readAt
            where n.recipientUserId = :userId
              and n.status = com.insurance.notificationservice.model.NotificationStatus.UNREAD
            """)
    int markAllRead(@Param("userId") UUID userId, @Param("readAt") Instant readAt);
}
