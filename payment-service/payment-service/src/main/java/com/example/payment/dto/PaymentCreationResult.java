package com.example.payment.dto;

public record PaymentCreationResult(PaymentResponse payment, boolean idempotentReplay) {
}
