package com.example.payment.dto;

import com.example.payment.domain.enumeration.PaymentType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record PaymentRequest(
        @NotNull Long clientId,
        @NotNull @DecimalMin(value = "0.0001", inclusive = true) BigDecimal amount,
        @NotNull PaymentType type,
        @NotNull LocalDate paymentDate
) {
}
