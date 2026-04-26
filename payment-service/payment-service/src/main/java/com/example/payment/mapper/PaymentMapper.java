package com.example.payment.mapper;

import com.example.payment.domain.Payment;
import com.example.payment.dto.PaymentResponse;
import org.springframework.stereotype.Component;

@Component
public class PaymentMapper {

    public PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getContract().getId(),
                payment.getContract().getClient().getId(),
                payment.getAmount(),
                payment.getType(),
                payment.getPaymentDate(),
                payment.getCreatedAt()
        );
    }
}
