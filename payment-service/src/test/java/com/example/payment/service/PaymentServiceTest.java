package com.example.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.payment.domain.Client;
import com.example.payment.domain.Contract;
import com.example.payment.domain.Payment;
import com.example.payment.domain.enumeration.PaymentType;
import com.example.payment.dto.PaymentCreationResult;
import com.example.payment.dto.PaymentRequest;
import com.example.payment.dto.PaymentResponse;
import com.example.payment.exception.BadRequestException;
import com.example.payment.exception.NotFoundException;
import com.example.payment.mapper.PaymentMapper;
import com.example.payment.repository.PaymentRepository;
import com.example.payment.service.impl.PaymentServiceImpl;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private ContractQueryService contractQueryService;

    private PaymentServiceImpl paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentServiceImpl(
                paymentRepository, contractQueryService, new PaymentMapper(), new SimpleMeterRegistry());
        paymentService.initMeters();
    }


    @Test
    void listForContract_notFound() {
        when(contractQueryService.getByIdOrThrow(9L))
                .thenThrow(new NotFoundException("Contract not found"));

        assertThatThrownBy(() -> paymentService.listForContract(9L)).isInstanceOf(NotFoundException.class);
        verify(paymentRepository, never()).findAllForContract(anyLong());
    }

    @Test
    void listForContract_mapsPayments() {
        Client client = clientEntity(1L);
        Contract contract = contractEntity(2L, client);
        Payment row = paymentEntity(10L, contract, new BigDecimal("1.00"), LocalDate.of(2024, 1, 2));

        when(contractQueryService.getByIdOrThrow(2L)).thenReturn(contract);
        when(paymentRepository.findAllForContract(2L)).thenReturn(List.of(row));

        List<PaymentResponse> out = paymentService.listForContract(2L);

        assertThat(out).hasSize(1);
        assertThat(out.getFirst().id()).isEqualTo(10L);
        assertThat(out.getFirst().contractId()).isEqualTo(2L);
        assertThat(out.getFirst().clientId()).isEqualTo(1L);
    }

    @Test
    void getByIdOrThrow_notFound() {
        when(paymentRepository.findDetailedById(3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getByIdOrThrow(3L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void getByIdOrThrow_returnsMapped() {
        Client client = clientEntity(5L);
        Contract contract = contractEntity(1L, client);
        Payment p = paymentEntity(7L, contract, new BigDecimal("3.00"), LocalDate.of(2024, 3, 4));
        when(paymentRepository.findDetailedById(7L)).thenReturn(Optional.of(p));

        com.example.payment.dto.PaymentResponse r = paymentService.getByIdOrThrow(7L);

        assertThat(r.id()).isEqualTo(7L);
        assertThat(r.clientId()).isEqualTo(5L);
    }

    @Test
    void create_persistsNewPaymentWithoutIdempotency() {
        Client client = clientEntity(1L);
        Contract contract = contractEntity(2L, client);
        when(contractQueryService.getByIdOrThrow(2L)).thenReturn(contract);
        when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(invocation -> {
                    Payment argument = invocation.getArgument(0);
                    argument.setId(99L);
                    return argument;
                });

        PaymentRequest req = new PaymentRequest(1L, new BigDecimal("10.25"), PaymentType.INCOMING, LocalDate.of(2024, 2, 1));
        PaymentCreationResult result = paymentService.create(2L, req, Optional.empty());

        assertThat(result.idempotentReplay()).isFalse();
        assertThat(result.payment().id()).isEqualTo(99L);
        assertThat(result.payment().amount()).isEqualByComparingTo("10.25");
    }

    @Test
    void create_savesIdempotencyKeyOnFirstWrite() {
        Client client = clientEntity(1L);
        Contract contract = contractEntity(2L, client);
        when(contractQueryService.getByIdOrThrow(2L)).thenReturn(contract);
        when(paymentRepository.findByContractIdAndIdempotencyKey(2L, "key-a")).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(invocation -> {
                    Payment argument = invocation.getArgument(0);
                    argument.setId(101L);
                    return argument;
                });

        paymentService.create(2L, new PaymentRequest(1L, new BigDecimal("1.00"), PaymentType.OUTGOING, LocalDate.now()), Optional.of("key-a"));

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertThat(captor.getValue().getIdempotencyKey()).isEqualTo("key-a");
    }

    @Test
    void create_dataIntegrityWithoutIdempotencyKey_throwsBadRequest() {
        Client client = clientEntity(1L);
        Contract contract = contractEntity(2L, client);
        when(contractQueryService.getByIdOrThrow(2L)).thenReturn(contract);
        when(paymentRepository.save(any(Payment.class)))
                .thenThrow(new DataIntegrityViolationException("constraint"));

        PaymentRequest req = new PaymentRequest(1L, new BigDecimal("1.00"), PaymentType.INCOMING, LocalDate.now());

        assertThatThrownBy(() -> paymentService.create(2L, req, Optional.empty()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Could not create");
    }

    @Test
    void create_rejectsClientMismatch() {
        Client client = mock(Client.class);
        when(client.getId()).thenReturn(1L);
        Contract contract = mock(Contract.class);
        when(contract.getClient()).thenReturn(client);
        when(contractQueryService.getByIdOrThrow(10L)).thenReturn(contract);

        PaymentRequest req = new PaymentRequest(999L, new BigDecimal("10.00"), PaymentType.INCOMING, LocalDate.now());

        assertThatThrownBy(() -> paymentService.create(10L, req, Optional.empty()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Client does not belong");
    }

    @Test
    void create_idempotentReplayDoesNotLoadContract() {
        Payment existing = mock(Payment.class);
        Contract contract = mock(Contract.class);
        Client client = mock(Client.class);
        when(existing.getContract()).thenReturn(contract);
        when(contract.getId()).thenReturn(2L);
        when(contract.getClient()).thenReturn(client);
        when(client.getId()).thenReturn(3L);
        when(existing.getAmount()).thenReturn(new BigDecimal("5.00"));
        when(existing.getType()).thenReturn(PaymentType.INCOMING);
        when(existing.getPaymentDate()).thenReturn(LocalDate.of(2024, 1, 1));
        when(existing.getCreatedAt()).thenReturn(Instant.parse("2024-01-01T00:00:00Z"));
        when(existing.getId()).thenReturn(1L);

        when(paymentRepository.findByContractIdAndIdempotencyKey(2L, "k1")).thenReturn(Optional.of(existing));

        PaymentRequest req = new PaymentRequest(99L, new BigDecimal("1.00"), PaymentType.OUTGOING, LocalDate.now());
        PaymentCreationResult result = paymentService.create(2L, req, Optional.of("k1"));

        assertThat(result.idempotentReplay()).isTrue();
        assertThat(result.payment().id()).isEqualTo(1L);
        verify(contractQueryService, never()).getByIdOrThrow(anyLong());
    }

    @Test
    void create_duplicateKeyHandled() {
        when(paymentRepository.findByContractIdAndIdempotencyKey(20L, "k2"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(mockedWinnerPayment()));

        Client client = mock(Client.class);
        when(client.getId()).thenReturn(3L);
        Contract contract = mock(Contract.class);
        when(contract.getClient()).thenReturn(client);
        when(contract.getId()).thenReturn(20L);
        when(contractQueryService.getByIdOrThrow(20L)).thenReturn(contract);

        when(paymentRepository.save(any(Payment.class))).thenThrow(new DataIntegrityViolationException("dup"));

        PaymentRequest req = new PaymentRequest(3L, new BigDecimal("2.00"), PaymentType.INCOMING, LocalDate.now());
        PaymentCreationResult result = paymentService.create(20L, req, Optional.of("k2"));

        assertThat(result.idempotentReplay()).isTrue();
        assertThat(result.payment().id()).isEqualTo(42L);
    }

    private static Client clientEntity(long id) {
        Client c = new Client();
        c.setId(id);
        c.setName("test-client");
        return c;
    }

    private static Contract contractEntity(long id, Client client) {
        Contract t = new Contract();
        t.setId(id);
        t.setClient(client);
        t.setContractNumber("CNT-" + id);
        return t;
    }

    private static Payment paymentEntity(
            long id, Contract contract, BigDecimal amount, LocalDate paymentDate) {
        Payment p = new Payment();
        p.setId(id);
        p.setContract(contract);
        p.setAmount(amount);
        p.setType(PaymentType.INCOMING);
        p.setPaymentDate(paymentDate);
        p.setCreatedAt(Instant.parse("2024-01-15T00:00:00Z"));
        return p;
    }

    private static Payment mockedWinnerPayment() {
        Payment p = mock(Payment.class);
        Contract c = mock(Contract.class);
        Client cl = mock(Client.class);
        when(p.getContract()).thenReturn(c);
        when(c.getId()).thenReturn(20L);
        when(c.getClient()).thenReturn(cl);
        when(cl.getId()).thenReturn(3L);
        when(p.getId()).thenReturn(42L);
        when(p.getAmount()).thenReturn(new BigDecimal("2.00"));
        when(p.getType()).thenReturn(PaymentType.INCOMING);
        when(p.getPaymentDate()).thenReturn(LocalDate.now());
        when(p.getCreatedAt()).thenReturn(Instant.now());
        return p;
    }
}
