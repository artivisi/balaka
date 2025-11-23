package com.artivisi.accountingfinance.repository;

import com.artivisi.accountingfinance.entity.TransactionSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionSequenceRepository extends JpaRepository<TransactionSequence, UUID> {

    Optional<TransactionSequence> findBySequenceTypeAndYear(String sequenceType, Integer year);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM TransactionSequence s WHERE s.sequenceType = :type AND s.year = :year")
    Optional<TransactionSequence> findBySequenceTypeAndYearForUpdate(@Param("type") String sequenceType, @Param("year") Integer year);
}
