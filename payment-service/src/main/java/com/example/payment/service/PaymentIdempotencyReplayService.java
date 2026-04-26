package com.example.payment.service;

import com.example.payment.dto.PaymentResponse;
import java.util.Optional;

/**
 * Loads a payment by contract + idempotency key in a dedicated transaction (see implementation for
 * {@code REQUIRES_NEW}), for safe replay after a constraint failure on insert.
 */
public interface PaymentIdempotencyReplayService {

    Optional<PaymentResponse> findReplayForIdempotencyKey(Long contractId, String idempotencyKey);
}
