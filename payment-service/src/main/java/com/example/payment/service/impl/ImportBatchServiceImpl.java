package com.example.payment.service.impl;

import com.example.payment.domain.ImportBatch;
import com.example.payment.domain.enumeration.ImportBatchStatus;
import com.example.payment.dto.ImportBatchCompleteRequest;
import com.example.payment.dto.ImportBatchResponse;
import com.example.payment.exception.BadRequestException;
import com.example.payment.exception.ConflictException;
import com.example.payment.exception.NotFoundException;
import com.example.payment.repository.ImportBatchRepository;
import com.example.payment.service.ImportBatchService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImportBatchServiceImpl implements ImportBatchService {

    private final ImportBatchRepository importBatchRepository;
    private final MeterRegistry meterRegistry;

    private Timer importFinalizeTimer;

    @PostConstruct
    public void initMeters() {
        importFinalizeTimer = meterRegistry.timer("import.batch.finalize.latency");
    }

    @Override
    @Transactional
    public ImportBatchResponse start(String fileSha256) {
        Optional<ImportBatch> existing = importBatchRepository.findByFileSha256(fileSha256);
        if (existing.isPresent()) {
            ImportBatch b = existing.get();
            if (b.getStatus() == ImportBatchStatus.COMPLETED) {
                log.info("Duplicate import detected: fileSha256={} was already completed at {}", 
                        fileSha256, b.getCompletedAt());
                return toResponse(b, true);
            }
            log.warn("Concurrent import attempt: fileSha256={} is still PENDING", fileSha256);
            throw new ConflictException("Import already registered for this file; wait for completion or use a new file");
        }
        ImportBatch batch = new ImportBatch();
        batch.setFileSha256(fileSha256);
        batch.setStatus(ImportBatchStatus.PENDING);
        try {
            ImportBatch saved = importBatchRepository.save(batch);
            return toResponse(saved, false);
        } catch (DataIntegrityViolationException ex) {
            ImportBatch raced = importBatchRepository
                    .findByFileSha256(fileSha256)
                    .orElseThrow(() -> new BadRequestException("Could not register import batch"));
            if (raced.getStatus() == ImportBatchStatus.COMPLETED) {
                return toResponse(raced, true);
            }
            throw new ConflictException("Import already registered for this file; wait for completion or use a new file");
        }
    }

    @Override
    @Transactional
    public ImportBatchResponse complete(String fileSha256, ImportBatchCompleteRequest request) {
        return importFinalizeTimer.record(() -> doComplete(fileSha256, request));
    }

    private ImportBatchResponse doComplete(String fileSha256, ImportBatchCompleteRequest request) {
        ImportBatch batch = importBatchRepository
                .findByFileSha256(fileSha256)
                .orElseThrow(() -> new NotFoundException("Import batch not found"));
        if (batch.getStatus() == ImportBatchStatus.COMPLETED) {
            return toResponse(batch, true);
        }
        batch.setRowsAccepted(request.rowsAccepted());
        batch.setRowsRejected(request.rowsRejected());
        batch.setStatus(ImportBatchStatus.COMPLETED);
        batch.setCompletedAt(Instant.now());
        return toResponse(importBatchRepository.save(batch), false);
    }

    private static ImportBatchResponse toResponse(ImportBatch b, boolean alreadyProcessed) {
        return new ImportBatchResponse(
                b.getFileSha256(),
                b.getStatus(),
                b.getRowsAccepted(),
                b.getRowsRejected(),
                b.getCreatedAt(),
                b.getCompletedAt(),
                alreadyProcessed);
    }
}
