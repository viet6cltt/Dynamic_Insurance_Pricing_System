package com.insurance.notificationservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.insurance.notificationservice.client.UserServiceClient;
import com.insurance.notificationservice.dto.EventEnvelope;
import com.insurance.notificationservice.dto.UserProfileResponse;
import com.insurance.notificationservice.model.EmailDelivery;
import com.insurance.notificationservice.model.EmailDeliveryStatus;
import com.insurance.notificationservice.model.Notification;
import com.insurance.notificationservice.repository.EmailDeliveryRepository;
import com.insurance.notificationservice.repository.NotificationRepository;
import com.insurance.notificationservice.repository.ProcessedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationEventProcessorTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Mock
    private UserServiceClient userServiceClient;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private EmailDeliveryRepository emailDeliveryRepository;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    private NotificationEventProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new NotificationEventProcessor(
                userServiceClient,
                new NotificationDefinitionFactory(objectMapper),
                notificationRepository,
                emailDeliveryRepository,
                processedEventRepository,
                objectMapper);
    }

    @Test
    void processRejectsInvalidEvent() {
        assertThrows(IllegalArgumentException.class, () -> processor.process(null));
    }

    @Test
    void processSkipsAlreadyProcessedEvent() {
        EventEnvelope event = event("payment.succeeded", UUID.randomUUID());
        when(processedEventRepository.existsByEventIdAndConsumerName(eq(event.eventId()), any()))
                .thenReturn(true);

        processor.process(event);

        verify(notificationRepository, never()).save(any());
        verify(emailDeliveryRepository, never()).save(any());
    }

    @Test
    void processMarksEventProcessedWhenRecipientCannotBeResolved() {
        EventEnvelope event = new EventEnvelope(
                UUID.randomUUID(), "payment.succeeded", 1, Instant.now(), "payment-service",
                "Payment", UUID.randomUUID(), null, null,
                objectMapper.createObjectNode().put("amount", "1000000"));

        processor.process(event);

        verify(processedEventRepository).save(any());
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void processCreatesNotificationAndPendingEmailForSupportedEvent() {
        UUID customerId = UUID.randomUUID();
        EventEnvelope event = event("payment.succeeded", customerId);
        when(userServiceClient.getUserProfileByAuthUserId(customerId, "SYSTEM", "SYSTEM"))
                .thenReturn(profile(customerId, "buyer@example.com"));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            notification.setNotificationId(UUID.randomUUID());
            return notification;
        });

        processor.process(event);

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(notificationCaptor.capture());
        assertEquals("payment.succeeded", notificationCaptor.getValue().getEventType());
        assertEquals(customerId, notificationCaptor.getValue().getRecipientUserId());

        ArgumentCaptor<EmailDelivery> deliveryCaptor = ArgumentCaptor.forClass(EmailDelivery.class);
        verify(emailDeliveryRepository).save(deliveryCaptor.capture());
        assertEquals(EmailDeliveryStatus.PENDING, deliveryCaptor.getValue().getStatus());
        assertEquals("buyer@example.com", deliveryCaptor.getValue().getRecipientEmail());
        verify(processedEventRepository).save(any());
    }

    @Test
    void processSkipsEmailWhenProfileEmailMissing() {
        UUID customerId = UUID.randomUUID();
        EventEnvelope event = event("payment.failed", customerId);
        when(userServiceClient.getUserProfileByAuthUserId(customerId, "SYSTEM", "SYSTEM"))
                .thenReturn(profile(customerId, " "));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            notification.setNotificationId(UUID.randomUUID());
            return notification;
        });

        processor.process(event);

        ArgumentCaptor<EmailDelivery> deliveryCaptor = ArgumentCaptor.forClass(EmailDelivery.class);
        verify(emailDeliveryRepository).save(deliveryCaptor.capture());
        assertEquals(EmailDeliveryStatus.SKIPPED, deliveryCaptor.getValue().getStatus());
        assertEquals("missing-email", deliveryCaptor.getValue().getRecipientEmail());
    }

    @Test
    void processMarksUnsupportedEventWithoutCreatingNotification() {
        UUID customerId = UUID.randomUUID();
        EventEnvelope event = event("unknown.event", customerId);
        when(userServiceClient.getUserProfileByAuthUserId(customerId, "SYSTEM", "SYSTEM"))
                .thenReturn(profile(customerId, "buyer@example.com"));

        processor.process(event);

        verify(notificationRepository, never()).save(any());
        verify(processedEventRepository).save(any());
    }

    private EventEnvelope event(String eventType, UUID customerId) {
        return new EventEnvelope(
                UUID.randomUUID(),
                eventType,
                1,
                Instant.now(),
                "payment-service",
                "Payment",
                UUID.randomUUID(),
                "corr-1",
                null,
                objectMapper.createObjectNode()
                        .put("customerId", customerId.toString())
                        .put("contractId", UUID.randomUUID().toString())
                        .put("amount", "1000000")
                        .put("currency", "VND"));
    }

    private UserProfileResponse profile(UUID authUserId, String email) {
        return new UserProfileResponse(
                UUID.randomUUID(),
                authUserId,
                email,
                "0900000000",
                "Nguyen Van A",
                "ID-1",
                LocalDate.of(1990, 1, 1),
                "MALE",
                "ACTIVE",
                Instant.now(),
                Instant.now());
    }
}
