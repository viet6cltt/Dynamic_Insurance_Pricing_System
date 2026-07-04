package com.insurance.userservice.controller;

import com.insurance.userservice.dto.UserProfileResponse;
import com.insurance.userservice.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/system")
@RequiredArgsConstructor
public class SystemUserProfileController {

    private final UserProfileService userProfileService;

    @GetMapping("/{userId}")
    public ResponseEntity<String> findUserStatus(@PathVariable String userId) {
        try {
            UUID authUserId = UUID.fromString(userId);
            UserProfileResponse response = userProfileService.getUserProfileByAuthUserId(authUserId);
            return ResponseEntity.ok(response.customerStatus());
        } catch (IllegalArgumentException | NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
