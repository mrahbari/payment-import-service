package com.example.payment.service;

import com.example.payment.domain.Contract;
import com.example.payment.dto.ContractSummaryResponse;
import java.util.Optional;

public interface ContractQueryService {
    Contract getByIdOrThrow(Long id);
    Optional<ContractSummaryResponse> findByContractNumber(String contractNumber);
}
