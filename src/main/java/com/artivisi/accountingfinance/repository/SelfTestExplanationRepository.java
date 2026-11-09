package com.artivisi.accountingfinance.repository;

import com.artivisi.accountingfinance.entity.SelfTestExplanation;
import com.artivisi.accountingfinance.enums.SelfTestLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SelfTestExplanationRepository extends JpaRepository<SelfTestExplanation, UUID> {

    List<SelfTestExplanation> findByReportYear(Integer reportYear);

    Optional<SelfTestExplanation> findByReportYearAndLineAndPeriodMonth(Integer reportYear, SelfTestLine line, Integer periodMonth);
}
