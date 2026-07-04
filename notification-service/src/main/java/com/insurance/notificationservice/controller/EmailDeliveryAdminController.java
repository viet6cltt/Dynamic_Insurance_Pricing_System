package com.insurance.notificationservice.controller;

import com.insurance.notificationservice.dto.EmailDeliveryResponse;
import com.insurance.notificationservice.service.EmailDeliveryAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/notifications/admin/email-deliveries")
@RequiredArgsConstructor
public class EmailDeliveryAdminController {

    private final EmailDeliveryAdminService emailDeliveryAdminService;

    @GetMapping
    public ResponseEntity<List<EmailDeliveryResponse>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(emailDeliveryAdminService.list(status, size));
    }

    @PostMapping("/{deliveryId}/retry")
    public ResponseEntity<EmailDeliveryResponse> retry(@PathVariable UUID deliveryId) {
        return ResponseEntity.ok(emailDeliveryAdminService.retry(deliveryId));
    }
}
