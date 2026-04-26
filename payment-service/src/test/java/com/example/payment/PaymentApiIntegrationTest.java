package com.example.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.payment.domain.Client;
import com.example.payment.domain.Contract;
import com.example.payment.repository.ClientRepository;
import com.example.payment.repository.ContractRepository;
import com.example.payment.repository.ImportBatchRepository;
import com.example.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Requires Docker (Testcontainers). Default {@code mvn test} skips this class; run {@code mvn test -Pintegration-tests}.
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class PaymentApiIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        if (postgres.isRunning()) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl);
            registry.add("spring.datasource.username", postgres::getUsername);
            registry.add("spring.datasource.password", postgres::getPassword);
        } else {
            // Fallback to H2 if Docker is missing
            registry.add("spring.datasource.url", () -> "jdbc:h2:mem:payment_db;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE");
            registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
            registry.add("spring.datasource.username", () -> "sa");
            registry.add("spring.datasource.password", () -> "");
            registry.add("spring.jpa.hibernate.ddl-auto", () -> "update");
            registry.add("spring.flyway.enabled", () -> "false");
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private ContractRepository contractRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private ImportBatchRepository importBatchRepository;

    private Long contractId;
    private Long clientId;

    @BeforeEach
    void seed() {
        importBatchRepository.deleteAll();
        paymentRepository.deleteAll();
        contractRepository.deleteAll();
        clientRepository.deleteAll();
        Client c = new Client();
        c.setName("Demo Corp");
        c = clientRepository.save(c);
        clientId = c.getId();
        Contract ct = new Contract();
        ct.setClient(c);
        ct.setContractNumber("CNT-1001");
        ct = contractRepository.save(ct);
        contractId = ct.getId();
    }

    @Test
    void createPayment_idempotentHeader() throws Exception {
        String body = """
                {"clientId": %d, "amount": "100.50", "type": "INCOMING", "paymentDate": "2024-06-01"}
                """
                .formatted(clientId);

        mockMvc.perform(post("/api/v1/contracts/{cid}/payments", contractId)
                        .header("Idempotency-Key", "idem-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(100.5))
                .andExpect(jsonPath("$.contractId").value(contractId.intValue()));

        mockMvc.perform(post("/api/v1/contracts/{cid}/payments", contractId)
                        .header("Idempotency-Key", "idem-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(100.5));

        mockMvc.perform(get("/api/v1/contracts/{cid}/payments", contractId)).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").exists());
    }

    @Test
    void listPayments_emptyContract404WhenMissing() throws Exception {
        mockMvc.perform(get("/api/v1/contracts/{cid}/payments", 999_999L)).andExpect(status().isNotFound());
    }

    @Test
    void createPayment_sameIdempotencyKeyDifferentContracts_createsBoth() throws Exception {
        Contract ct2 = new Contract();
        ct2.setClient(clientRepository.findById(clientId).orElseThrow());
        ct2.setContractNumber("CNT-1002");
        ct2 = contractRepository.save(ct2);
        Long contractId2 = ct2.getId();

        String body1 = """
                {"clientId": %d, "amount": "10.00", "type": "INCOMING", "paymentDate": "2024-06-01"}
                """
                .formatted(clientId);
        String body2 = """
                {"clientId": %d, "amount": "20.00", "type": "OUTGOING", "paymentDate": "2024-06-02"}
                """
                .formatted(clientId);

        mockMvc.perform(post("/api/v1/contracts/{cid}/payments", contractId)
                        .header("Idempotency-Key", "shared-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body1))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(10.0));

        mockMvc.perform(post("/api/v1/contracts/{cid}/payments", contractId2)
                        .header("Idempotency-Key", "shared-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body2))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(20.0));

        assertThat(paymentRepository.count()).isEqualTo(2);
    }

    @Test
    void importBatch_idempotent() throws Exception {
        String hash = "a".repeat(64);
        mockMvc.perform(post("/api/v1/import-batches/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileSha256\": \"%s\"}".formatted(hash)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/import-batches/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileSha256\": \"%s\"}".formatted(hash)))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/import-batches/%s/complete".formatted(hash))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rowsAccepted\": 2, \"rowsRejected\": 1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rowsAccepted").value(2));

        mockMvc.perform(post("/api/v1/import-batches/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileSha256\": \"%s\"}".formatted(hash)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyProcessed").value(true));
    }
}
