package com.example.payment.repository;

import com.example.payment.domain.Payment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @Query("""
            SELECT p FROM Payment p
            JOIN FETCH p.contract c
            JOIN FETCH c.client
            WHERE p.contract.id = :contractId
            ORDER BY p.paymentDate DESC, p.id DESC
            """)
    List<Payment> findAllForContract(@Param("contractId") Long contractId);

    @Query( """
            SELECT p FROM Payment p
            JOIN FETCH p.contract c
            JOIN FETCH c.client
            WHERE c.id = :contractId AND p.idempotencyKey = :key
            ORDER BY p.paymentDate DESC, p.id DESC
            """)
    Optional<Payment> findByContractIdAndIdempotencyKey(
            @Param("contractId") Long contractId, @Param("key") String idempotencyKey);

    @Query( """
            SELECT p FROM Payment p
            JOIN FETCH p.contract c
            JOIN FETCH c.client
            WHERE p.id = :id
            """)
    Optional<Payment> findDetailedById(@Param("id") Long id);
}
