package com.example.payment.service;

import com.example.payment.domain.ImportBatch;
import java.util.Optional;

/**
 * Loads an import batch by file hash in a dedicated transaction, for recovery after a unique-key
 * race on insert.
 */
public interface ImportBatchRaceLookupService {

    Optional<ImportBatch> findByFileSha256(String fileSha256);
}
