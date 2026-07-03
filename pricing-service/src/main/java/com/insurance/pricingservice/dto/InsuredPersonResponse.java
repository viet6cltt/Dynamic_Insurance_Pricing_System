package com.insurance.pricingservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record InsuredPersonResponse(
    UUID insuredPersonId,
    @JsonProperty("ownerUserProfileId") UUID userId,
    String firstName,
    String lastName,
    String dateOfBirth,
    String gender,
    @JsonProperty("relationshipToOwner") String relationship,
    String phoneNumber,
    String email,
    String status
) {}
