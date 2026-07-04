package com.insurance.notificationservice.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record UserProfileResponse(
        UUID userProfileId,
        UUID authUserId,
        String email,
        String phoneNumber,
        String fullName,
        String identityNumber,
        LocalDate dateOfBirth,
        String gender,
        String customerStatus,
        Instant createdAt,
        Instant updatedAt
) {}
