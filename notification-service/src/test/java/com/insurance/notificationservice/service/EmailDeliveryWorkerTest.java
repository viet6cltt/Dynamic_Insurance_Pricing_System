package com.insurance.notificationservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.notificationservice.model.ChannelPolicy;
import com.insurance.notificationservice.model.EmailDelivery;
import com.insurance.notificationservice.model.EmailDeliveryStatus;
import com.insurance.notificationservice.model.Notification;
import com.insurance.notificationservice.model.NotificationStatus;
import com.insurance.notificationservice.repository.EmailDeliveryRepository;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.StringTemplateResolver;

import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailDeliveryWorkerTest {

    @Mock
    private EmailDeliveryRepository emailDeliveryRepository;

    @Mock
    private JavaMailSender mailSender;

    private EmailDeliveryWorker worker;

    @BeforeEach
    void setUp() {
        SpringTemplateEngine templateEngine = new SpringTemplateEngine();
        templateEngine.setTemplateResolver(new StringTemplateResolver());
        worker = new EmailDeliveryWorker(emailDeliveryRepository, mailSender, templateEngine, new ObjectMapper());
        ReflectionTestUtils.setField(worker, "batchSize", 25);
        ReflectionTestUtils.setField(worker, "maxRetries", 5);
        ReflectionTestUtils.setField(worker, "fromAddress", "no-reply@example.com");
        ReflectionTestUtils.setField(worker, "fromName", "Dynamic Insurance");
    }

    @Test
    void sendPendingEmailsMarksDeliverySent() {
        EmailDelivery delivery = delivery(EmailDeliveryStatus.PENDING, 0);
        when(emailDeliveryRepository.findPendingForUpdate(any(), any())).thenReturn(List.of(delivery));
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));

        int count = worker.sendPendingEmails();

        assertEquals(1, count);
        assertEquals(EmailDeliveryStatus.SENT, delivery.getStatus());
        assertNotNull(delivery.getSentAt());
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void sendPendingEmailsMarksDeliveryFailedAfterMaxRetries() {
        EmailDelivery delivery = delivery(EmailDeliveryStatus.PENDING, 4);
        when(emailDeliveryRepository.findPendingForUpdate(any(), any())).thenReturn(List.of(delivery));
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        doThrow(new RuntimeException("SMTP down")).when(mailSender).send(any(MimeMessage.class));

        int count = worker.sendPendingEmails();

        assertEquals(1, count);
        assertEquals(EmailDeliveryStatus.FAILED, delivery.getStatus());
        assertEquals(5, delivery.getRetryCount());
        assertNotNull(delivery.getFailedAt());
    }

    private EmailDelivery delivery(EmailDeliveryStatus status, int retryCount) {
        return EmailDelivery.builder()
                .deliveryId(UUID.randomUUID())
                .notification(Notification.builder()
                        .notificationId(UUID.randomUUID())
                        .sourceEventId(UUID.randomUUID())
                        .eventType("payment.succeeded")
                        .recipientUserId(UUID.randomUUID())
                        .title("Title")
                        .message("Message")
                        .status(NotificationStatus.UNREAD)
                        .channelPolicy(ChannelPolicy.IN_APP_AND_EMAIL)
                        .build())
                .recipientEmail("buyer@example.com")
                .recipientName("Buyer")
                .subject("Subject")
                .templateName("payment-succeeded")
                .templateModel(new ObjectMapper().createObjectNode().put("customerName", "Buyer"))
                .status(status)
                .retryCount(retryCount)
                .nextAttemptAt(Instant.now())
                .build();
    }

}
