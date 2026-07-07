package com.insurance.notificationservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.notificationservice.model.ChannelPolicy;
import com.insurance.notificationservice.model.EmailDelivery;
import com.insurance.notificationservice.model.EmailDeliveryStatus;
import com.insurance.notificationservice.model.Notification;
import com.insurance.notificationservice.model.NotificationStatus;
import com.insurance.notificationservice.repository.EmailDeliveryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailDeliveryAdminServiceTest {

    @Mock
    private EmailDeliveryRepository emailDeliveryRepository;

    private EmailDeliveryAdminService service;

    @BeforeEach
    void setUp() {
        service = new EmailDeliveryAdminService(emailDeliveryRepository, new NotificationMapper());
    }

    @Test
    void listDefaultsToFailedStatus() {
        EmailDelivery delivery = delivery();
        when(emailDeliveryRepository.findByStatusOrderByCreatedAtDesc(eq(EmailDeliveryStatus.FAILED), any()))
                .thenReturn(List.of(delivery));

        var response = service.list(null, 20);

        assertEquals(1, response.size());
        assertEquals("FAILED", response.getFirst().status());
    }

    @Test
    void retryResetsDeliveryToPending() {
        EmailDelivery delivery = delivery();
        UUID deliveryId = delivery.getDeliveryId();
        when(emailDeliveryRepository.findById(deliveryId)).thenReturn(Optional.of(delivery));
        when(emailDeliveryRepository.save(delivery)).thenReturn(delivery);

        var response = service.retry(deliveryId);

        assertEquals("PENDING", response.status());
        assertNull(response.lastError());
        assertNull(response.failedAt());
    }

    @Test
    void retryThrowsWhenDeliveryMissing() {
        UUID deliveryId = UUID.randomUUID();
        when(emailDeliveryRepository.findById(deliveryId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.retry(deliveryId));
    }

    private EmailDelivery delivery() {
        return EmailDelivery.builder()
                .deliveryId(UUID.randomUUID())
                .notification(Notification.builder()
                        .notificationId(UUID.randomUUID())
                        .sourceEventId(UUID.randomUUID())
                        .eventType("payment.failed")
                        .recipientUserId(UUID.randomUUID())
                        .title("Title")
                        .message("Message")
                        .status(NotificationStatus.UNREAD)
                        .channelPolicy(ChannelPolicy.IN_APP_AND_EMAIL)
                        .metadata(new ObjectMapper().createObjectNode())
                        .build())
                .recipientEmail("buyer@example.com")
                .subject("Subject")
                .templateName("payment-failed")
                .templateModel(new ObjectMapper().createObjectNode())
                .status(EmailDeliveryStatus.FAILED)
                .retryCount(1)
                .failedAt(Instant.now())
                .lastError("SMTP down")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }
}
