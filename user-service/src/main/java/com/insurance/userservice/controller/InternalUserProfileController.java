package com.insurance.userservice.controller;

import com.insurance.userservice.dto.CreateUserProfileRequest;
import com.insurance.userservice.dto.UserProfileResponse;
import com.insurance.userservice.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class InternalUserProfileController {

    private final UserProfileService userProfileService;

    @PostMapping
    public ResponseEntity<UserProfileResponse> createUserProfile(@RequestBody CreateUserProfileRequest request) {
        UserProfileResponse response = userProfileService.createUserProfile(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/by-auth-user/{authUserId}")
    public ResponseEntity<UserProfileResponse> getUserProfileByAuthUserId(@PathVariable UUID authUserId) {
        UserProfileResponse response = userProfileService.getUserProfileByAuthUserId(authUserId);
        return ResponseEntity.ok(response);
    }
}
