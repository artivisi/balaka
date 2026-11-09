package com.artivisi.accountingfinance.repository;

import com.artivisi.accountingfinance.entity.TaxInquiryCase;
import com.artivisi.accountingfinance.enums.TaxInquiryType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaxInquiryCaseRepository extends JpaRepository<TaxInquiryCase, UUID> {

    @Query("SELECT c FROM TaxInquiryCase c JOIN FETCH c.letterDocument LEFT JOIN FETCH c.taxFiling LEFT JOIN FETCH c.taxDocument "
            + "WHERE c.id = :id")
    Optional<TaxInquiryCase> findWithLinks(@Param("id") UUID id);

    @Query("SELECT c FROM TaxInquiryCase c WHERE (:year IS NULL OR c.taxYear = :year) ORDER BY c.receivedDate DESC, c.letterNumber")
    List<TaxInquiryCase> findByYear(@Param("year") Integer year);

    @Query("SELECT c FROM TaxInquiryCase c WHERE c.taxFiling.id IN :filingIds ORDER BY c.receivedDate DESC")
    List<TaxInquiryCase> findByTaxFilingIds(@Param("filingIds") Collection<UUID> filingIds);

    boolean existsByInquiryTypeAndLetterNumber(TaxInquiryType inquiryType, String letterNumber);
}
