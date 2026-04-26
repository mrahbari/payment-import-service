package com.example.payment.web.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.payment.dto.ContractSummaryResponse;
import com.example.payment.dto.ImportBatchResponse;
import com.example.payment.dto.ImportBatchStartRequest;
import com.example.payment.dto.PaymentCreationResult;
import com.example.payment.dto.PaymentResponse;
import com.example.payment.domain.enumeration.ImportBatchStatus;
import com.example.payment.domain.enumeration.PaymentType;
import com.example.shared.error.GlobalExceptionHandler;
import com.example.payment.service.ContractQueryService;
import com.example.payment.service.ImportBatchService;
import com.example.payment.service.PaymentService;
import com.example.shared.config.WebMvcConfig;
import com.example.payment.web.filter.CorrelationIdFilter;
import com.example.payment.web.filter.RateLimitingFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = {
            PaymentController.class,
            ContractController.class,
            ImportBatchController.class,
            RootController.class
        })
@Import({WebMvcConfig.class, CorrelationIdFilter.class, RateLimitingFilter.class, GlobalExceptionHandler.class})
class ControllersWebMvcTest {

    private static final String HEX64 = "a".repeat(64);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PaymentService paymentService;

    @MockBean
    private ContractQueryService contractQueryService;

    @MockBean
    private ImportBatchService importBatchService;

    @Test
    void rootListsEntryPoints() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("payment-service"))
                .andExpect(jsonPath("$.api").value("/api/v1"));
    }

    @Test
    void correlationHeadersPopulated() throws Exception {
        mockMvc.perform(get("/").header(CorrelationIdFilter.HEADER_TRACE, "trace-fixed"))
                .andExpect(status().isOk())
                .andExpect(header().string(CorrelationIdFilter.HEADER_TRACE, "trace-fixed"))
                .andExpect(header().exists(CorrelationIdFilter.HEADER_REQUEST));
    }

    @Test
    void listPaymentsDelegatesToService() throws Exception {
        PaymentResponse row =
                new PaymentResponse(1L, 2L, 3L, new BigDecimal("1.00"), PaymentType.INCOMING, LocalDate.now(), Instant.now());
        when(paymentService.listForContract(2L)).thenReturn(List.of(row));

        mockMvc.perform(get("/api/v1/contracts/2/payments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].contractId").value(2));
    }

    @Test
    void createPaymentReturns201() throws Exception {
        PaymentResponse body = new PaymentResponse(
                9L, 1L, 1L, new BigDecimal("5.00"), PaymentType.INCOMING, LocalDate.of(2024, 1, 1), Instant.now());
        when(paymentService.create(eq(1L), any(), any()))
                .thenReturn(new PaymentCreationResult(body, false));

        mockMvc.perform(
                        post("/api/v1/contracts/1/payments")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"clientId":1,"amount":"5.00","type":"INCOMING","paymentDate":"2024-01-01"}
                                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(9));
    }

    @Test
    void createPaymentIdempotentReplayReturns200() throws Exception {
        PaymentResponse body = new PaymentResponse(
                9L, 1L, 1L, new BigDecimal("5.00"), PaymentType.INCOMING, LocalDate.of(2024, 1, 1), Instant.now());
        when(paymentService.create(eq(1L), any(), any()))
                .thenReturn(new PaymentCreationResult(body, true));

        mockMvc.perform(
                        post("/api/v1/contracts/1/payments")
                                .header("Idempotency-Key", "k1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"clientId":1,"amount":"5.00","type":"INCOMING","paymentDate":"2024-01-01"}
                                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(9));
    }

    @Test
    void createPaymentInvalidBodyReturns400() throws Exception {
        mockMvc.perform(
                        post("/api/v1/contracts/1/payments")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"clientId\":null,\"amount\":\"-1\",\"type\":\"INCOMING\",\"paymentDate\":\"2024-01-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void contractByNumberReturnsSummary() throws Exception {
        when(contractQueryService.findByContractNumber("CNT-1"))
                .thenReturn(Optional.of(new ContractSummaryResponse(10L, 20L, "CNT-1")));

        mockMvc.perform(get("/api/v1/contracts/by-number/CNT-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.contractNumber").value("CNT-1"));
    }

    @Test
    void contractByNumberNotFoundReturns404() throws Exception {
        when(contractQueryService.findByContractNumber("missing")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/contracts/by-number/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void importBatchStartReturns201() throws Exception {
        ImportBatchResponse res =
                new ImportBatchResponse(HEX64, ImportBatchStatus.PENDING, 0, 0, Instant.now(), null, false);
        when(importBatchService.start(HEX64)).thenReturn(res);

        mockMvc.perform(
                        post("/api/v1/import-batches/start")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new ImportBatchStartRequest(HEX64))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void importBatchStartWhenAlreadyProcessedReturns200() throws Exception {
        ImportBatchResponse res = new ImportBatchResponse(
                HEX64, ImportBatchStatus.COMPLETED, 1, 0, Instant.now(), Instant.now(), true);
        when(importBatchService.start(HEX64)).thenReturn(res);

        mockMvc.perform(
                        post("/api/v1/import-batches/start")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new ImportBatchStartRequest(HEX64))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyProcessed").value(true));
    }

    @Test
    void importBatchStartInvalidShaReturns400() throws Exception {
        mockMvc.perform(
                        post("/api/v1/import-batches/start")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"fileSha256\":\"not-hex\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void importBatchCompleteDelegates() throws Exception {
        ImportBatchResponse res = new ImportBatchResponse(
                HEX64, ImportBatchStatus.COMPLETED, 2, 1, Instant.now(), Instant.now(), false);
        when(importBatchService.complete(eq(HEX64), any()))
                .thenReturn(res);

        mockMvc.perform(
                        post("/api/v1/import-batches/{hash}/complete", HEX64)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"rowsAccepted\":2,\"rowsRejected\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rowsAccepted").value(2));
    }
}
