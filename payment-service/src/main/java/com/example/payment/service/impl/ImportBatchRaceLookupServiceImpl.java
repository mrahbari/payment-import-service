package com.example.payment.service.impl;

import com.example.payment.domain.ImportBatch;
import com.example.payment.repository.ImportBatchRepository;
import com.example.payment.service.ImportBatchRaceLookupService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads an import batch by file hash in a <strong>new</strong> transaction, used to recover
 * from {@code DataIntegrityViolationException} on unique {@code file_sha256} without reusing
 * a rollback-only session.
 */
@Service
@RequiredArgsConstructor
public class ImportBatchRaceLookupServiceImpl implements ImportBatchRaceLookupService {

    private final ImportBatchRepository importBatchRepository;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<ImportBatch> findByFileSha256(String fileSha256) {
        return importBatchRepository.findByFileSha256(fileSha256);
    }
}
