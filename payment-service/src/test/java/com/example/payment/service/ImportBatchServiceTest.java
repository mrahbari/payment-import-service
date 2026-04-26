package com.example.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.payment.domain.ImportBatch;
import com.example.payment.domain.enumeration.ImportBatchStatus;
import com.example.payment.dto.ImportBatchCompleteRequest;
import com.example.payment.dto.ImportBatchResponse;
import com.example.payment.exception.ConflictException;
import com.example.payment.exception.NotFoundException;
import com.example.payment.repository.ImportBatchRepository;
import com.example.payment.service.ImportBatchRaceLookupService;
import com.example.payment.service.impl.ImportBatchRaceLookupServiceImpl;
import com.example.payment.service.impl.ImportBatchServiceImpl;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class ImportBatchServiceTest {

    @Mock
    private ImportBatchRepository importBatchRepository;

    private ImportBatchServiceImpl importBatchService;

    @BeforeEach
    void setUp() {
        ImportBatchRaceLookupService raceLookup = new ImportBatchRaceLookupServiceImpl(importBatchRepository);
        importBatchService = new ImportBatchServiceImpl(importBatchRepository, raceLookup, new SimpleMeterRegistry());
        importBatchService.initMeters();
    }

    @Test
    void start_newBatch() {
        when(importBatchRepository.findByFileSha256("hash1")).thenReturn(Optional.empty());
        ImportBatch saved = new ImportBatch();
        saved.setFileSha256("hash1");
        saved.setStatus(ImportBatchStatus.PENDING);
        when(importBatchRepository.save(any(ImportBatch.class))).thenReturn(saved);

        ImportBatchResponse res = importBatchService.start("hash1");

        assertThat(res.fileSha256()).isEqualTo("hash1");
        assertThat(res.status()).isEqualTo(ImportBatchStatus.PENDING);
        assertThat(res.alreadyProcessed()).isFalse();
    }

    @Test
    void start_alreadyCompleted() {
        ImportBatch existing = new ImportBatch();
        existing.setFileSha256("hash2");
        existing.setStatus(ImportBatchStatus.COMPLETED);
        when(importBatchRepository.findByFileSha256("hash2")).thenReturn(Optional.of(existing));

        ImportBatchResponse res = importBatchService.start("hash2");

        assertThat(res.alreadyProcessed()).isTrue();
        assertThat(res.status()).isEqualTo(ImportBatchStatus.COMPLETED);
    }

    @Test
    void start_pendingConflict() {
        ImportBatch existing = new ImportBatch();
        existing.setFileSha256("hash3");
        existing.setStatus(ImportBatchStatus.PENDING);
        when(importBatchRepository.findByFileSha256("hash3")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> importBatchService.start("hash3"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void complete_updatesStatus() {
        ImportBatch existing = new ImportBatch();
        existing.setFileSha256("hash4");
        existing.setStatus(ImportBatchStatus.PENDING);
        when(importBatchRepository.findByFileSha256("hash4")).thenReturn(Optional.of(existing));
        when(importBatchRepository.save(any(ImportBatch.class))).thenAnswer(i -> i.getArgument(0));

        ImportBatchCompleteRequest req = new ImportBatchCompleteRequest(10, 2);
        ImportBatchResponse res = importBatchService.complete("hash4", req);

        assertThat(res.status()).isEqualTo(ImportBatchStatus.COMPLETED);
        assertThat(res.rowsAccepted()).isEqualTo(10);
        assertThat(res.rowsRejected()).isEqualTo(2);
    }

    @Test
    void complete_notFoundThrows() {
        when(importBatchRepository.findByFileSha256("none")).thenReturn(Optional.empty());
        ImportBatchCompleteRequest req = new ImportBatchCompleteRequest(1, 0);

        assertThatThrownBy(() -> importBatchService.complete("none", req))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void complete_idempotentWhenAlreadyCompleted() {
        ImportBatch done = new ImportBatch();
        done.setFileSha256("hash-done");
        done.setStatus(ImportBatchStatus.COMPLETED);
        when(importBatchRepository.findByFileSha256("hash-done")).thenReturn(Optional.of(done));

        ImportBatchResponse res = importBatchService.complete("hash-done", new ImportBatchCompleteRequest(0, 0));

        assertThat(res.alreadyProcessed()).isTrue();
        assertThat(res.status()).isEqualTo(ImportBatchStatus.COMPLETED);
    }
}
