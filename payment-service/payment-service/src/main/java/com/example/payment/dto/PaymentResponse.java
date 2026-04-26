package com.example.payment.dto;

import com.example.payment.domain.enumeration.PaymentType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record PaymentResponse(
        Long id,
        Long contractId,
        Long clientId,
        BigDecimal amount,
        PaymentType type,
        LocalDate paymentDate,
        Instant createdAt
) {
}
