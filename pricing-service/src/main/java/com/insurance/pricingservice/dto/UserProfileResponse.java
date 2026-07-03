package com.insurance.pricingservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record UserProfileResponse(
    @JsonProperty("userProfileId") UUID userId,
    UUID authUserId,
    String fullName,
    String phoneNumber,
    String email,
    String dateOfBirth,
    String gender,
    @JsonProperty("identityNumber") String idNumber,
    String address,
    @JsonProperty("customerStatus") String status
) {}
