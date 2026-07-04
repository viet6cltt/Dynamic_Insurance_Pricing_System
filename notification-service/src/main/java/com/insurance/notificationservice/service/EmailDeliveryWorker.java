package com.insurance.notificationservice.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.notificationservice.model.EmailDelivery;
import com.insurance.notificationservice.model.EmailDeliveryStatus;
import com.insurance.notificationservice.repository.EmailDeliveryRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailDeliveryWorker {

    private final EmailDeliveryRepository emailDeliveryRepository;
    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final ObjectMapper objectMapper;

    @Value("${app.email.outbox.batch-size:25}")
    private int batchSize;

    @Value("${app.email.outbox.max-retries:5}")
    private int maxRetries;

    @Value("${app.mail.from:no-reply@insurance.local}")
    private String fromAddress;

    @Value("${app.mail.from-name:Dynamic Insurance}")
    private String fromName;

    @Scheduled(fixedDelayString = "${app.email.outbox.poll-delay-ms:1000}")
    @Transactional
    public int sendPendingEmails() {
        var deliveries = emailDeliveryRepository.findPendingForUpdate(
                Instant.now(),
                PageRequest.of(0, batchSize)
        );
        for (EmailDelivery delivery : deliveries) {
            sendOne(delivery);
        }
        return deliveries.size();
    }

    private void sendOne(EmailDelivery delivery) {
        delivery.setStatus(EmailDeliveryStatus.SENDING);
        try {
            Map<String, Object> model = objectMapper.convertValue(
                    delivery.getTemplateModel(),
                    new TypeReference<>() {}
            );
            Context context = new Context();
            context.setVariables(model);
            String html = templateEngine.process("emails/" + delivery.getTemplateName(), context);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message,
                    MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                    StandardCharsets.UTF_8.name()
            );
            helper.setFrom(fromAddress, fromName);
            helper.setTo(delivery.getRecipientEmail());
            helper.setSubject(delivery.getSubject());
            helper.setText(html, true);
            mailSender.send(message);

            delivery.setStatus(EmailDeliveryStatus.SENT);
            delivery.setSentAt(Instant.now());
            delivery.setLastError(null);
            log.info("Sent email delivery {} to {}", delivery.getDeliveryId(), delivery.getRecipientEmail());
        } catch (Exception ex) {
            int nextRetryCount = delivery.getRetryCount() + 1;
            delivery.setRetryCount(nextRetryCount);
            delivery.setLastError(ex.getMessage());
            if (nextRetryCount >= maxRetries) {
                delivery.setStatus(EmailDeliveryStatus.FAILED);
                delivery.setFailedAt(Instant.now());
            } else {
                delivery.setStatus(EmailDeliveryStatus.PENDING);
                delivery.setNextAttemptAt(Instant.now().plus(backoffSeconds(nextRetryCount), ChronoUnit.SECONDS));
            }
            log.warn("Failed email delivery {} retry {}/{}: {}",
                    delivery.getDeliveryId(), nextRetryCount, maxRetries, ex.getMessage());
        }
    }

    private long backoffSeconds(int retryCount) {
        return Math.min(300, (long) Math.pow(2, retryCount) * 10);
    }
}
