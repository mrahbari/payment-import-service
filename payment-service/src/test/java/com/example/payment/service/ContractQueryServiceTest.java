package com.example.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.example.payment.domain.Client;
import com.example.payment.domain.Contract;
import com.example.payment.dto.ContractSummaryResponse;
import com.example.payment.exception.NotFoundException;
import com.example.payment.repository.ContractRepository;
import com.example.payment.service.impl.ContractQueryServiceImpl;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContractQueryServiceTest {

    @Mock
    private ContractRepository contractRepository;

    @InjectMocks
    private ContractQueryServiceImpl contractQueryService;

    @Test
    void getByIdOrThrow_notFound() {
        when(contractRepository.findByIdWithClient(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> contractQueryService.getByIdOrThrow(1L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void getByIdOrThrow_returnsContract() {
        Client client = new Client();
        client.setId(5L);
        client.setName("c");
        Contract c = new Contract();
        c.setId(2L);
        c.setClient(client);
        c.setContractNumber("X-1");
        when(contractRepository.findByIdWithClient(2L)).thenReturn(Optional.of(c));

        Contract out = contractQueryService.getByIdOrThrow(2L);

        assertThat(out.getId()).isEqualTo(2L);
        assertThat(out.getClient().getId()).isEqualTo(5L);
    }

    @Test
    void findByContractNumber_empty() {
        when(contractRepository.findByContractNumberWithClient("missing")).thenReturn(Optional.empty());

        assertThat(contractQueryService.findByContractNumber("missing")).isEmpty();
    }

    @Test
    void findByContractNumber_mapsToSummary() {
        Client client = new Client();
        client.setId(9L);
        client.setName("c");
        Contract c = new Contract();
        c.setId(3L);
        c.setClient(client);
        c.setContractNumber("CNT-9");
        when(contractRepository.findByContractNumberWithClient("CNT-9")).thenReturn(Optional.of(c));

        Optional<ContractSummaryResponse> r = contractQueryService.findByContractNumber("CNT-9");

        assertThat(r).isPresent();
        assertThat(r.get().id()).isEqualTo(3L);
        assertThat(r.get().clientId()).isEqualTo(9L);
        assertThat(r.get().contractNumber()).isEqualTo("CNT-9");
    }
}
