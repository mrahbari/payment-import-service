package com.example.payment.web.rest;

import com.example.payment.dto.PaymentCreationResult;
import com.example.payment.dto.PaymentRequest;
import com.example.payment.dto.PaymentResponse;
import com.example.payment.service.PaymentService;
import io.micrometer.core.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/contracts/{contractId}/payments")
@Tag(name = "Payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping
    @Timed(value = "payments.list", histogram = true)
    @Operation(summary = "List payments for a contract")
    public List<PaymentResponse> list(@PathVariable Long contractId) {
        return paymentService.listForContract(contractId);
    }

    @PostMapping
    @Timed(value = "payments.create", histogram = true)
    @Operation(
            summary = "Create payment",
            responses = {
                    @ApiResponse(
                            responseCode = "201",
                            description = "Created",
                            headers = @Header(name = "Idempotency-Key", description = "Optional idempotency key")),
                    @ApiResponse(
                            responseCode = "200",
                            description = "Replayed idempotent request",
                            content = @Content(schema = @Schema(implementation = PaymentResponse.class)))
            })
    public ResponseEntity<PaymentResponse> create(
            @PathVariable Long contractId,
            @Valid @RequestBody PaymentRequest request,
            @Parameter(description = "Idempotency key for safe retries")
            @RequestHeader(value = "Idempotency-Key", required = false)
            String idempotencyKey) {
        Optional<String> key = normalizeKey(idempotencyKey);
        PaymentCreationResult result = paymentService.create(contractId, request, key);
        HttpStatus status = result.idempotentReplay() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(result.payment());
    }

    private Optional<String> normalizeKey(String key) {
        return Optional.ofNullable(key)
                .filter(k -> !k.isBlank());
    }
}
