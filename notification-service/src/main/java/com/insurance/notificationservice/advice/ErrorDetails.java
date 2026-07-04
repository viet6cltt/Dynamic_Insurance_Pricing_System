package com.insurance.notificationservice.advice;

import java.time.Instant;

public record ErrorDetails(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path
) {}
