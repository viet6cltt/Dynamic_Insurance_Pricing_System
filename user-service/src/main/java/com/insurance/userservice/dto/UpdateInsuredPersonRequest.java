package com.insurance.userservice.dto;

import java.time.LocalDate;
import java.util.UUID;

public record UpdateInsuredPersonRequest(
    String fullName,
    LocalDate dateOfBirth,
    String gender,
    String identityNumber,
    String relationshipToOwner,
    UUID linkedUserProfileId
) {}
