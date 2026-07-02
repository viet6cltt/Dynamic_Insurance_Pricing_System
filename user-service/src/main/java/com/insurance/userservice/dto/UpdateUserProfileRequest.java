package com.insurance.userservice.dto;

import java.time.LocalDate;

public record UpdateUserProfileRequest(
    String phoneNumber,
    String fullName,
    String identityNumber,
    LocalDate dateOfBirth,
    String gender
) {}
