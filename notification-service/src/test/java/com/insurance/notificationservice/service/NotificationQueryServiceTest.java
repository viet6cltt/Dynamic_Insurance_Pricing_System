package com.insurance.notificationservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.notificationservice.model.ChannelPolicy;
import com.insurance.notificationservice.model.Notification;
import com.insurance.notificationservice.model.NotificationStatus;
import com.insurance.notificationservice.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationQueryServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    private NotificationQueryService service;

    @BeforeEach
    void setUp() {
        service = new NotificationQueryService(notificationRepository, new NotificationMapper());
    }

    @Test
    void listFiltersByStatusWhenProvided() {
        UUID userId = UUID.randomUUID();
        when(notificationRepository.findByRecipientUserIdAndStatusOrderByCreatedAtDesc(eq(userId), eq(NotificationStatus.UNREAD), any()))
                .thenReturn(new PageImpl<>(List.of(notification(userId))));

        var response = service.list(userId, "unread", 0, 10);

        assertEquals(1, response.content().size());
        assertEquals("UNREAD", response.content().getFirst().status());
    }

    @Test
    void unreadCountDelegatesToRepository() {
        UUID userId = UUID.randomUUID();
        when(notificationRepository.countByRecipientUserIdAndStatus(userId, NotificationStatus.UNREAD)).thenReturn(3L);

        assertEquals(3L, service.unreadCount(userId));
    }

    @Test
    void markReadSetsReadAtOnlyForUnreadNotification() {
        UUID userId = UUID.randomUUID();
        Notification notification = notification(userId);
        when(notificationRepository.findById(notification.getNotificationId())).thenReturn(Optional.of(notification));
        when(notificationRepository.save(notification)).thenReturn(notification);

        var response = service.markRead(userId, notification.getNotificationId());

        assertEquals("READ", response.status());
        assertNotNull(response.readAt());
    }

    @Test
    void archiveRejectsNotificationOwnedByAnotherUser() {
        Notification notification = notification(UUID.randomUUID());
        when(notificationRepository.findById(notification.getNotificationId())).thenReturn(Optional.of(notification));

        assertThrows(IllegalArgumentException.class,
                () -> service.archive(UUID.randomUUID(), notification.getNotificationId()));
    }

    @Test
    void markAllReadReturnsUpdatedRowCount() {
        UUID userId = UUID.randomUUID();
        when(notificationRepository.markAllRead(eq(userId), any())).thenReturn(5);

        assertEquals(5, service.markAllRead(userId));
    }

    private Notification notification(UUID userId) {
        return Notification.builder()
                .notificationId(UUID.randomUUID())
                .sourceEventId(UUID.randomUUID())
                .eventType("payment.succeeded")
                .aggregateType("Payment")
                .aggregateId(UUID.randomUUID())
                .recipientUserId(userId)
                .title("Thanh toan thanh cong")
                .message("Message")
                .status(NotificationStatus.UNREAD)
                .channelPolicy(ChannelPolicy.IN_APP_AND_EMAIL)
                .metadata(new ObjectMapper().createObjectNode())
                .createdAt(Instant.now())
                .build();
    }
}
