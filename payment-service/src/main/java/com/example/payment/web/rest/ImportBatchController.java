package com.example.payment.web.rest;

import com.example.payment.dto.ImportBatchCompleteRequest;
import com.example.payment.dto.ImportBatchResponse;
import com.example.payment.dto.ImportBatchStartRequest;
import com.example.payment.service.ImportBatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/import-batches")
@Tag(name = "Import batches")
@RequiredArgsConstructor
public class ImportBatchController {

    private final ImportBatchService importBatchService;

    @PostMapping("/start")
    @Operation(summary = "Register a file hash before streaming import (idempotent when completed)")
    public ResponseEntity<ImportBatchResponse> start(@Valid @RequestBody ImportBatchStartRequest request) {
        ImportBatchResponse response = importBatchService.start(request.fileSha256());
        if (response.alreadyProcessed()) {
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{fileSha256}/complete")
    @Operation(summary = "Finalize import statistics after streaming parse")
    public ImportBatchResponse complete(
            @PathVariable String fileSha256, @Valid @RequestBody ImportBatchCompleteRequest request) {
        return importBatchService.complete(fileSha256, request);
    }
}
