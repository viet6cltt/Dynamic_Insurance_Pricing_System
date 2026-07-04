package com.insurance.notificationservice.controller;

import com.insurance.notificationservice.dto.NotificationResponse;
import com.insurance.notificationservice.dto.PagedResponse;
import com.insurance.notificationservice.dto.UnreadCountResponse;
import com.insurance.notificationservice.service.NotificationQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationQueryService notificationQueryService;

    @GetMapping
    public ResponseEntity<PagedResponse<NotificationResponse>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(notificationQueryService.list(currentUserId(), status, page, size));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<UnreadCountResponse> unreadCount() {
        return ResponseEntity.ok(new UnreadCountResponse(notificationQueryService.unreadCount(currentUserId())));
    }

    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<NotificationResponse> markRead(@PathVariable UUID notificationId) {
        return ResponseEntity.ok(notificationQueryService.markRead(currentUserId(), notificationId));
    }

    @PatchMapping("/read-all")
    public ResponseEntity<Map<String, Integer>> markAllRead() {
        int updated = notificationQueryService.markAllRead(currentUserId());
        return ResponseEntity.ok(Map.of("updated", updated));
    }

    @PatchMapping("/{notificationId}/archive")
    public ResponseEntity<NotificationResponse> archive(@PathVariable UUID notificationId) {
        return ResponseEntity.ok(notificationQueryService.archive(currentUserId(), notificationId));
    }

    private UUID currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return UUID.fromString(authentication.getName());
    }
}
