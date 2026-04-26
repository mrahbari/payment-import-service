package com.example.shared.error;

import java.time.Instant;

public record ErrorResponse(
    Instant timestamp,
    String code,
    String message,
    String path,
    String requestId
) {}
