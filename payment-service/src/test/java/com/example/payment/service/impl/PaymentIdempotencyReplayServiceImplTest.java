package com.example.payment.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.payment.domain.Client;
import com.example.payment.domain.Contract;
import com.example.payment.domain.Payment;
import com.example.payment.dto.PaymentResponse;
import com.example.payment.mapper.PaymentMapper;
import com.example.payment.repository.PaymentRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentIdempotencyReplayServiceImplTest {

    @Mock
    private PaymentRepository paymentRepository;

    private PaymentIdempotencyReplayServiceImpl service;
    private final PaymentMapper mapper = new PaymentMapper();

    @BeforeEach
    void setUp() {
        service = new PaymentIdempotencyReplayServiceImpl(paymentRepository, mapper);
    }

    @Test
    void shouldFindReplayForIdempotencyKey() {
        Long contractId = 1L;
        String key = "key-123";

        Client client = new Client();
        client.setId(5L);

        Contract contract = new Contract();
        contract.setId(contractId);
        contract.setClient(client);

        Payment payment = new Payment();
        payment.setId(10L);
        payment.setIdempotencyKey(key);
        payment.setContract(contract);

        when(paymentRepository.findByContractIdAndIdempotencyKey(contractId, key))
                .thenReturn(Optional.of(payment));

        Optional<PaymentResponse> result = service.findReplayForIdempotencyKey(contractId, key);

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(10L);
    }

    @Test
    void shouldReturnEmptyWhenNotFound() {
        when(paymentRepository.findByContractIdAndIdempotencyKey(1L, "missing"))
                .thenReturn(Optional.empty());

        assertThat(service.findReplayForIdempotencyKey(1L, "missing")).isEmpty();
    }
}
