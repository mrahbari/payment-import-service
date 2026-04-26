package com.example.payment.repository;

import com.example.payment.domain.Contract;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContractRepository extends JpaRepository<Contract, Long> {

    @Query("""
            SELECT c FROM Contract c 
            JOIN FETCH c.client 
            WHERE c.id = :id
            """)
    Optional<Contract> findByIdWithClient(@Param("id") Long id);

    Optional<Contract> findByContractNumber(String contractNumber);

    @Query("""
            SELECT c FROM Contract c 
            JOIN FETCH c.client 
            WHERE c.contractNumber = :number
            """)
    Optional<Contract> findByContractNumberWithClient(@Param("number") String contractNumber);
}
