package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.FiscalPeriod;
import com.artivisi.accountingfinance.enums.FiscalPeriodStatus;
import com.artivisi.accountingfinance.enums.TransactionStatus;
import com.artivisi.accountingfinance.repository.FiscalPeriodRepository;
import com.artivisi.accountingfinance.repository.TransactionRepository;
import com.artivisi.accountingfinance.security.CurrentUser;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FiscalPeriodService {

    private final FiscalPeriodRepository fiscalPeriodRepository;
    private final TransactionRepository transactionRepository;

    public FiscalPeriod findById(UUID id) {
        return fiscalPeriodRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Fiscal period not found with id: " + id));
    }

    public Optional<FiscalPeriod> findByYearAndMonth(Integer year, Integer month) {
        return fiscalPeriodRepository.findByYearAndMonth(year, month);
    }

    public Optional<FiscalPeriod> findByDate(LocalDate date) {
        return fiscalPeriodRepository.findByDate(date);
    }

    public Page<FiscalPeriod> findAll(Pageable pageable) {
        return fiscalPeriodRepository.findAllOrderByYearMonthDesc(pageable);
    }

    public Page<FiscalPeriod> findByFilters(Integer year, FiscalPeriodStatus status, Pageable pageable) {
        return fiscalPeriodRepository.findByFilters(year, status, pageable);
    }

    public List<FiscalPeriod> findByYear(Integer year) {
        return fiscalPeriodRepository.findByYear(year);
    }

    public List<FiscalPeriod> findOpenPeriods() {
        return fiscalPeriodRepository.findOpenPeriods();
    }

    public List<Integer> findDistinctYears() {
        return fiscalPeriodRepository.findDistinctYears();
    }

    @Transactional
    public FiscalPeriod create(Integer year, Integer month) {
        if (fiscalPeriodRepository.existsByYearAndMonth(year, month)) {
            throw new IllegalArgumentException("Fiscal period already exists for " + year + "-" + month);
        }

        FiscalPeriod period = new FiscalPeriod();
        period.setYear(year);
        period.setMonth(month);
        period.setStatus(FiscalPeriodStatus.OPEN);

        return fiscalPeriodRepository.save(period);
    }

    @Transactional
    public FiscalPeriod getOrCreate(Integer year, Integer month) {
        return fiscalPeriodRepository.findByYearAndMonth(year, month)
                .orElseGet(() -> {
                    FiscalPeriod period = new FiscalPeriod();
                    period.setYear(year);
                    period.setMonth(month);
                    period.setStatus(FiscalPeriodStatus.OPEN);
                    return fiscalPeriodRepository.save(period);
                });
    }

    @Transactional
    public FiscalPeriod closeMonth(UUID id, String notes) {
        FiscalPeriod period = findById(id);

        if (!period.canCloseMonth()) {
            throw new IllegalStateException("Cannot close month for period with status: " + period.getStatus());
        }

        long draftCount = transactionRepository.countByStatusAndTransactionDateBetween(
                TransactionStatus.DRAFT, period.getStartDate(), period.getEndDate());
        if (draftCount > 0) {
            throw new IllegalStateException("Tidak dapat menutup periode " + period.getPeriodDisplayName() +
                    ". Masih ada " + draftCount + " transaksi draft yang belum diposting.");
        }

        period.setStatus(FiscalPeriodStatus.MONTH_CLOSED);
        period.setMonthClosedAt(LocalDateTime.now());
        period.setMonthClosedBy(getCurrentUsername());
        if (notes != null && !notes.isBlank()) {
            period.setNotes(notes);
        }

        return fiscalPeriodRepository.save(period);
    }

    @Transactional
    public FiscalPeriod fileTax(UUID id, String notes) {
        FiscalPeriod period = findById(id);

        if (!period.canFileTax()) {
            throw new IllegalStateException("Cannot file tax for period with status: " + period.getStatus() +
                    ". Period must be in MONTH_CLOSED status.");
        }

        period.setStatus(FiscalPeriodStatus.TAX_FILED);
        period.setTaxFiledAt(LocalDateTime.now());
        period.setTaxFiledBy(getCurrentUsername());
        if (notes != null && !notes.isBlank()) {
            period.setNotes(notes);
        }

        return fiscalPeriodRepository.save(period);
    }

    @Transactional
    public FiscalPeriod reopen(UUID id, String reason) {
        FiscalPeriod period = findById(id);

        if (!period.canReopen()) {
            throw new IllegalStateException("Cannot reopen period with status: " + period.getStatus() +
                    ". Only MONTH_CLOSED periods can be reopened.");
        }

        period.setStatus(FiscalPeriodStatus.OPEN);
        period.setMonthClosedAt(null);
        period.setMonthClosedBy(null);
        period.setNotes(reason);

        return fiscalPeriodRepository.save(period);
    }

    public boolean isPeriodOpenForPosting(LocalDate date) {
        Optional<FiscalPeriod> period = findByDate(date);
        return period.map(FiscalPeriod::canPostJournalEntry).orElse(true);
    }

    public void validatePeriodOpenForPosting(LocalDate date) {
        Optional<FiscalPeriod> period = findByDate(date);
        if (period.isPresent() && !period.get().canPostJournalEntry()) {
            throw new IllegalStateException("Cannot post journal entry. Fiscal period " +
                    period.get().getPeriodDisplayName() + " is " + period.get().getStatus().getIndonesianName());
        }
    }

    public long countByStatus(FiscalPeriodStatus status) {
        return fiscalPeriodRepository.countByStatus(status);
    }

    private String getCurrentUsername() {
        return CurrentUser.name();
    }
}
