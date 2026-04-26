package com.example.payment.service.impl;

import com.example.payment.domain.Contract;
import com.example.payment.dto.ContractSummaryResponse;
import com.example.payment.exception.NotFoundException;
import com.example.payment.repository.ContractRepository;
import com.example.payment.service.ContractQueryService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ContractQueryServiceImpl implements ContractQueryService {

    private final ContractRepository contractRepository;

    @Override
    @Transactional(readOnly = true)
    public Contract getByIdOrThrow(Long id) {
        return contractRepository
                .findByIdWithClient(id)
                .orElseThrow(() -> new NotFoundException("Contract not found"));
    }

    @Override
    @CircuitBreaker(name = "contractLookup")
    @Transactional(readOnly = true)
    public Optional<ContractSummaryResponse> findByContractNumber(String contractNumber) {
        return contractRepository
                .findByContractNumberWithClient(contractNumber)
                .map(c -> new ContractSummaryResponse(c.getId(), c.getClient().getId(), c.getContractNumber()));
    }
}
