package com.insurance.notificationservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.notificationservice.client.UserServiceClient;
import com.insurance.notificationservice.dto.EventEnvelope;
import com.insurance.notificationservice.dto.UserProfileResponse;
import com.insurance.notificationservice.model.*;
import com.insurance.notificationservice.repository.EmailDeliveryRepository;
import com.insurance.notificationservice.repository.NotificationRepository;
import com.insurance.notificationservice.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationEventProcessor {

    private static final String CONSUMER_NAME = "notification-service-consumer";

    private final UserServiceClient userServiceClient;
    private final NotificationDefinitionFactory definitionFactory;
    private final NotificationRepository notificationRepository;
    private final EmailDeliveryRepository emailDeliveryRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void process(EventEnvelope event) {
        if (event == null || event.eventId() == null || event.eventType() == null) {
            throw new IllegalArgumentException("Invalid notification event");
        }
        if (processedEventRepository.existsByEventIdAndConsumerName(event.eventId(), CONSUMER_NAME)) {
            log.info("Notification event {} already processed", event.eventId());
            return;
        }

        UUID recipientUserId = resolveRecipientUserId(event);
        if (recipientUserId == null) {
            markProcessed(event);
            log.info("Skipping event {} because no recipient user id was found", event.eventId());
            return;
        }

        UserProfileResponse profile = userServiceClient.getUserProfileByAuthUserId(
                recipientUserId,
                "SYSTEM",
                "SYSTEM"
        );

        NotificationDefinition definition = definitionFactory.create(event, profile)
                .orElse(null);
        if (definition == null) {
            markProcessed(event);
            log.info("Skipping unsupported notification event type {}", event.eventType());
            return;
        }

        Notification notification = Notification.builder()
                .sourceEventId(event.eventId())
                .eventType(event.eventType())
                .aggregateType(event.aggregateType())
                .aggregateId(event.aggregateId())
                .recipientUserId(recipientUserId)
                .title(definition.title())
                .message(definition.message())
                .status(NotificationStatus.UNREAD)
                .channelPolicy(definition.channelPolicy())
                .metadata(event.payload())
                .correlationId(event.correlationId())
                .build();
        Notification savedNotification = notificationRepository.save(notification);

        if (definition.channelPolicy() == ChannelPolicy.IN_APP_AND_EMAIL) {
            createEmailDelivery(savedNotification, definition, profile);
        }

        markProcessed(event);
    }

    private void createEmailDelivery(Notification notification,
                                     NotificationDefinition definition,
                                     UserProfileResponse profile) {
        String email = profile.email();
        EmailDeliveryStatus status = EmailDeliveryStatus.PENDING;
        String lastError = null;
        if (email == null || email.isBlank()) {
            status = EmailDeliveryStatus.SKIPPED;
            lastError = "Recipient email missing";
        }

        EmailDelivery delivery = EmailDelivery.builder()
                .notification(notification)
                .recipientEmail(email == null || email.isBlank() ? "missing-email" : email)
                .recipientName(profile.fullName())
                .subject(definition.emailSubject())
                .templateName(definition.templateName())
                .templateModel(objectMapper.valueToTree(definition.templateModel()))
                .status(status)
                .nextAttemptAt(status == EmailDeliveryStatus.PENDING ? Instant.now() : null)
                .lastError(lastError)
                .build();
        emailDeliveryRepository.save(delivery);
    }

    private UUID resolveRecipientUserId(EventEnvelope event) {
        String raw = firstText(event, "customerId", "buyerUserId", "userId", "applicantUserId");
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return UUID.fromString(raw);
    }

    private String firstText(EventEnvelope event, String... fieldNames) {
        for (String fieldName : fieldNames) {
            if (event.payload().hasNonNull(fieldName) && !event.payload().path(fieldName).asText().isBlank()) {
                return event.payload().path(fieldName).asText();
            }
        }
        return null;
    }

    private void markProcessed(EventEnvelope event) {
        processedEventRepository.save(ProcessedEvent.builder()
                .eventId(event.eventId())
                .consumerName(CONSUMER_NAME)
                .eventType(event.eventType())
                .aggregateType(event.aggregateType())
                .aggregateId(event.aggregateId())
                .build());
    }
}
