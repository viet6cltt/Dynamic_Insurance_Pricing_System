package com.insurance.userservice.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record InsuredPersonResponse(
    UUID insuredPersonId,
    UUID ownerUserProfileId,
    UUID linkedUserProfileId,
    String fullName,
    LocalDate dateOfBirth,
    String gender,
    String identityNumber,
    String relationshipToOwner,
    String status,
    Instant createdAt,
    Instant updatedAt
) {}
