package com.example.payment.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.payment.domain.ImportBatch;
import com.example.payment.repository.ImportBatchRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ImportBatchRaceLookupServiceImplTest {

    @Mock
    private ImportBatchRepository repository;

    private ImportBatchRaceLookupServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ImportBatchRaceLookupServiceImpl(repository);
    }

    @Test
    void shouldFindByFileSha256() {
        String sha = "abc-123";
        ImportBatch batch = new ImportBatch();
        batch.setFileSha256(sha);

        when(repository.findByFileSha256(sha)).thenReturn(Optional.of(batch));

        Optional<ImportBatch> result = service.findByFileSha256(sha);

        assertThat(result).isPresent();
        assertThat(result.get().getFileSha256()).isEqualTo(sha);
    }

    @Test
    void shouldReturnEmptyWhenNotFound() {
        when(repository.findByFileSha256("none")).thenReturn(Optional.empty());
        assertThat(service.findByFileSha256("none")).isEmpty();
    }
}
