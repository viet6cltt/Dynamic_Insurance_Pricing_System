package com.insurance.notificationservice.service;

import com.insurance.notificationservice.dto.EmailDeliveryResponse;
import com.insurance.notificationservice.dto.NotificationResponse;
import com.insurance.notificationservice.model.EmailDelivery;
import com.insurance.notificationservice.model.Notification;
import org.springframework.stereotype.Component;

@Component
public class NotificationMapper {

    public NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getNotificationId(),
                notification.getEventType(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getStatus().name(),
                notification.getChannelPolicy().name(),
                notification.getMetadata(),
                notification.getCreatedAt(),
                notification.getReadAt()
        );
    }

    public EmailDeliveryResponse toResponse(EmailDelivery delivery) {
        return new EmailDeliveryResponse(
                delivery.getDeliveryId(),
                delivery.getNotification().getNotificationId(),
                delivery.getRecipientEmail(),
                delivery.getSubject(),
                delivery.getTemplateName(),
                delivery.getStatus().name(),
                delivery.getRetryCount(),
                delivery.getNextAttemptAt(),
                delivery.getSentAt(),
                delivery.getFailedAt(),
                delivery.getLastError(),
                delivery.getCreatedAt(),
                delivery.getUpdatedAt()
        );
    }
}
