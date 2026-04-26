package com.example.payment.web.rest;

import com.example.payment.dto.ContractSummaryResponse;
import com.example.payment.exception.NotFoundException;
import com.example.payment.service.ContractQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/contracts")
@Tag(name = "Contracts")
@RequiredArgsConstructor
public class ContractController {

    private final ContractQueryService contractQueryService;

    @GetMapping("/by-number/{contractNumber}")
    @Operation(summary = "Resolve contract by business number (used by import service)")
    public ContractSummaryResponse byNumber(@PathVariable String contractNumber) {
        return contractQueryService
                .findByContractNumber(contractNumber)
                .orElseThrow(() -> new NotFoundException("Contract not found"));
    }
}
