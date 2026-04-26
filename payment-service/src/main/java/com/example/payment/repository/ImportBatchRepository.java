package com.example.payment.repository;

import com.example.payment.domain.ImportBatch;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportBatchRepository extends JpaRepository<ImportBatch, Long> {

    Optional<ImportBatch> findByFileSha256(String fileSha256);
}
