package com.insurance.userservice.dto;

import java.util.UUID;

public record DeactivateResponse(
    UUID insuredPersonId,
    String status,
    String message
) {}
