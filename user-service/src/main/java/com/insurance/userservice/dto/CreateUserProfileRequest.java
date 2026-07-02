package com.insurance.userservice.dto;

import java.time.LocalDate;
import java.util.UUID;

public record CreateUserProfileRequest(
    UUID authUserId,
    String email,
    String phoneNumber,
    String fullName,
    String identityNumber,
    LocalDate dateOfBirth,
    String gender
) {}
