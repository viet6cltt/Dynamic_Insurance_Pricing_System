package com.insurance.userservice.controller;

import com.insurance.userservice.dto.UpdateUserProfileRequest;
import com.insurance.userservice.dto.UserProfileResponse;
import com.insurance.userservice.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService userProfileService;

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getCurrentUserProfile() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UUID authUserId = UUID.fromString(authentication.getName());
        UserProfileResponse response = userProfileService.getUserProfileByAuthUserId(authUserId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/me")
    public ResponseEntity<UserProfileResponse> updateCurrentUserProfile(@RequestBody UpdateUserProfileRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UUID authUserId = UUID.fromString(authentication.getName());
        UserProfileResponse response = userProfileService.updateCurrentUserProfile(authUserId, request);
        return ResponseEntity.ok(response);
    }
}
