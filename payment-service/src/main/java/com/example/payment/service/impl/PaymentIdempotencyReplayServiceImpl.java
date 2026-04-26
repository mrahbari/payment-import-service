package com.example.payment.service.impl;

import com.example.payment.dto.PaymentResponse;
import com.example.payment.mapper.PaymentMapper;
import com.example.payment.repository.PaymentRepository;
import com.example.payment.service.PaymentIdempotencyReplayService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refetches a payment by idempotency key in a <strong>new</strong> transaction. Used after
 * {@code DataIntegrityViolationException} on insert so the persistence context is not
 * &quot;rollback-only&quot; from the failed flush.
 */
@Service
@RequiredArgsConstructor
public class PaymentIdempotencyReplayServiceImpl implements PaymentIdempotencyReplayService {

    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<PaymentResponse> findReplayForIdempotencyKey(Long contractId, String idempotencyKey) {
        return paymentRepository
                .findByContractIdAndIdempotencyKey(contractId, idempotencyKey)
                .map(paymentMapper::toResponse);
    }
}
