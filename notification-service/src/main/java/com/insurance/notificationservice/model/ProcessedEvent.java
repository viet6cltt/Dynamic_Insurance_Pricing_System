package com.insurance.notificationservice.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processed_events", uniqueConstraints = {
        @UniqueConstraint(name = "uk_notification_processed_event", columnNames = {"event_id", "consumer_name"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessedEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "processed_event_id", nullable = false, updatable = false)
    private UUID processedEventId;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "consumer_name", nullable = false, length = 120)
    private String consumerName;

    @Column(name = "event_type", nullable = false, length = 120)
    private String eventType;

    @Column(name = "aggregate_type", length = 80)
    private String aggregateType;

    @Column(name = "aggregate_id")
    private UUID aggregateId;

    @CreationTimestamp
    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;
}
