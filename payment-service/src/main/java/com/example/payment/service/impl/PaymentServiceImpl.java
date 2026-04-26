package com.example.payment.service.impl;

import com.example.payment.domain.Contract;
import com.example.payment.domain.Payment;
import com.example.payment.dto.PaymentCreationResult;
import com.example.payment.dto.PaymentRequest;
import com.example.payment.dto.PaymentResponse;
import com.example.payment.exception.BadRequestException;
import com.example.payment.exception.NotFoundException;
import com.example.payment.mapper.PaymentMapper;
import com.example.payment.repository.PaymentRepository;
import com.example.payment.service.ContractQueryService;
import com.example.payment.service.PaymentService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final ContractQueryService contractQueryService;
    private final PaymentMapper paymentMapper;
    private final MeterRegistry meterRegistry;

    private Counter paymentsCreated;
    private Timer paymentCreateTimer;

    @PostConstruct
    public void initMeters() {
        paymentsCreated = meterRegistry.counter("payments.created");
        paymentCreateTimer = meterRegistry.timer("payments.create.latency");
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentResponse> listForContract(Long contractId) {
        contractQueryService.getByIdOrThrow(contractId);
        return paymentRepository.findAllForContract(contractId).stream().map(paymentMapper::toResponse).toList();
    }

    @Override
    @Transactional
    public PaymentCreationResult create(Long contractId, PaymentRequest request, Optional<String> idempotencyKey) {
        return paymentCreateTimer.record(() -> doCreate(contractId, request, idempotencyKey));
    }

    private PaymentCreationResult doCreate(Long contractId, PaymentRequest request, Optional<String> idempotencyKey) {
        if (idempotencyKey.isPresent()) {
            Optional<Payment> existing =
                    paymentRepository.findByContractIdAndIdempotencyKey(contractId, idempotencyKey.get());
            if (existing.isPresent()) {
                log.info("Idempotent replay: payment already exists for contractId={}, idempotencyKey={}", 
                        contractId, idempotencyKey.get());
                return new PaymentCreationResult(paymentMapper.toResponse(existing.get()), true);
            }
        }

        Contract contract = contractQueryService.getByIdOrThrow(contractId);
        if (!contract.getClient().getId().equals(request.clientId())) {
            log.warn("Contract/Client mismatch: contractId={} belongs to clientId={}, but request has clientId={}",
                    contractId, contract.getClient().getId(), request.clientId());
            throw new BadRequestException("Client does not belong to contract");
        }

        Payment payment = new Payment();
        payment.setContract(contract);
        payment.setAmount(request.amount());
        payment.setType(request.type());
        payment.setPaymentDate(request.paymentDate());
        idempotencyKey.ifPresent(payment::setIdempotencyKey);

        try {
            Payment saved = paymentRepository.save(payment);
            paymentsCreated.increment();
            MDC.put("paymentId", String.valueOf(saved.getId()));
            log.info("Payment created: id={}, contractId={}, amount={}, type={}", 
                    saved.getId(), contractId, saved.getAmount(), saved.getType());
            return new PaymentCreationResult(paymentMapper.toResponse(saved), false);
        } catch (DataIntegrityViolationException ex) {
            if (idempotencyKey.isPresent()) {
                log.info("Concurrent idempotency hit: re-fetching existing payment for contractId={}, key={}", 
                        contractId, idempotencyKey.get());
                PaymentResponse replay = paymentRepository
                        .findByContractIdAndIdempotencyKey(contractId, idempotencyKey.get())
                        .map(paymentMapper::toResponse)
                        .orElseThrow(() -> ex);
                return new PaymentCreationResult(replay, true);
            }
            throw new BadRequestException("Could not create payment");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getByIdOrThrow(Long paymentId) {
        Payment p = paymentRepository
                .findDetailedById(paymentId)
                .orElseThrow(() -> new NotFoundException("Payment not found"));
        return paymentMapper.toResponse(p);
    }
}
