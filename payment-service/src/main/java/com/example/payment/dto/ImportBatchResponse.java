package com.example.payment.dto;

import com.example.payment.domain.enumeration.ImportBatchStatus;
import java.time.Instant;

public record ImportBatchResponse(
        String fileSha256,
        ImportBatchStatus status,
        int rowsAccepted,
        int rowsRejected,
        Instant createdAt,
        Instant completedAt,
        boolean alreadyProcessed
) {
}
