package com.insurance.notificationservice.service;

import com.insurance.notificationservice.dto.NotificationResponse;
import com.insurance.notificationservice.dto.PagedResponse;
import com.insurance.notificationservice.model.Notification;
import com.insurance.notificationservice.model.NotificationStatus;
import com.insurance.notificationservice.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationQueryService {

    private final NotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper;

    @Transactional(readOnly = true)
    public PagedResponse<NotificationResponse> list(UUID userId, String status, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Notification> result;
        if (status == null || status.isBlank()) {
            result = notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(userId, pageRequest);
        } else {
            NotificationStatus notificationStatus = NotificationStatus.valueOf(status.toUpperCase());
            result = notificationRepository.findByRecipientUserIdAndStatusOrderByCreatedAtDesc(userId, notificationStatus, pageRequest);
        }
        return new PagedResponse<>(
                result.getContent().stream().map(notificationMapper::toResponse).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return notificationRepository.countByRecipientUserIdAndStatus(userId, NotificationStatus.UNREAD);
    }

    @Transactional
    public NotificationResponse markRead(UUID userId, UUID notificationId) {
        Notification notification = findOwned(userId, notificationId);
        if (notification.getStatus() == NotificationStatus.UNREAD) {
            notification.setStatus(NotificationStatus.READ);
            notification.setReadAt(Instant.now());
        }
        return notificationMapper.toResponse(notificationRepository.save(notification));
    }

    @Transactional
    public int markAllRead(UUID userId) {
        return notificationRepository.markAllRead(userId, Instant.now());
    }

    @Transactional
    public NotificationResponse archive(UUID userId, UUID notificationId) {
        Notification notification = findOwned(userId, notificationId);
        notification.setStatus(NotificationStatus.ARCHIVED);
        return notificationMapper.toResponse(notificationRepository.save(notification));
    }

    private Notification findOwned(UUID userId, UUID notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found: " + notificationId));
        if (!notification.getRecipientUserId().equals(userId)) {
            throw new IllegalArgumentException("Notification not found: " + notificationId);
        }
        return notification;
    }
}
