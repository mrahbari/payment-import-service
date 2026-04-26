package com.example.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ImportBatchStartRequest(
        @NotBlank
        @Pattern(regexp = "[a-fA-F0-9]{64}", message = "fileSha256 must be a 64-char hex string")
        String fileSha256
) {
}
