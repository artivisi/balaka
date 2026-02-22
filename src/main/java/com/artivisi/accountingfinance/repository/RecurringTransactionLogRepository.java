package com.artivisi.accountingfinance.repository;

import com.artivisi.accountingfinance.entity.RecurringTransactionLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RecurringTransactionLogRepository extends JpaRepository<RecurringTransactionLog, UUID> {

    List<RecurringTransactionLog> findByRecurringTransactionIdOrderByScheduledDateDesc(UUID recurringTransactionId);

    Page<RecurringTransactionLog> findByRecurringTransactionIdOrderByScheduledDateDesc(UUID recurringTransactionId, Pageable pageable);
}
