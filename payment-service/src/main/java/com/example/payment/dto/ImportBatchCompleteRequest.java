package com.example.payment.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ImportBatchCompleteRequest(
        @NotNull @Min(0) Integer rowsAccepted,
        @NotNull @Min(0) Integer rowsRejected
) {
}
