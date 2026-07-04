package com.insurance.notificationservice.service;

import com.insurance.notificationservice.dto.EmailDeliveryResponse;
import com.insurance.notificationservice.model.EmailDelivery;
import com.insurance.notificationservice.model.EmailDeliveryStatus;
import com.insurance.notificationservice.repository.EmailDeliveryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmailDeliveryAdminService {

    private final EmailDeliveryRepository emailDeliveryRepository;
    private final NotificationMapper notificationMapper;

    @Transactional(readOnly = true)
    public List<EmailDeliveryResponse> list(String status, int size) {
        EmailDeliveryStatus deliveryStatus = status == null || status.isBlank()
                ? EmailDeliveryStatus.FAILED
                : EmailDeliveryStatus.valueOf(status.toUpperCase());
        return emailDeliveryRepository.findByStatusOrderByCreatedAtDesc(deliveryStatus, PageRequest.of(0, size))
                .stream()
                .map(notificationMapper::toResponse)
                .toList();
    }

    @Transactional
    public EmailDeliveryResponse retry(UUID deliveryId) {
        EmailDelivery delivery = emailDeliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new IllegalArgumentException("Email delivery not found: " + deliveryId));
        delivery.setStatus(EmailDeliveryStatus.PENDING);
        delivery.setNextAttemptAt(Instant.now());
        delivery.setLastError(null);
        delivery.setFailedAt(null);
        return notificationMapper.toResponse(emailDeliveryRepository.save(delivery));
    }
}
