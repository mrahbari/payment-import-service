package com.example.payment.service;

import com.example.payment.dto.PaymentCreationResult;
import com.example.payment.dto.PaymentRequest;
import com.example.payment.dto.PaymentResponse;
import java.util.List;
import java.util.Optional;

public interface PaymentService {
    List<PaymentResponse> listForContract(Long contractId);
    PaymentCreationResult create(Long contractId, PaymentRequest request, Optional<String> idempotencyKey);
    PaymentResponse getByIdOrThrow(Long paymentId);
}
