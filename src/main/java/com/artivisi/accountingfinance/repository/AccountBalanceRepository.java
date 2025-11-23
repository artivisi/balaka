package com.artivisi.accountingfinance.repository;

import com.artivisi.accountingfinance.entity.AccountBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountBalanceRepository extends JpaRepository<AccountBalance, UUID> {

    Optional<AccountBalance> findByAccountIdAndPeriodYearAndPeriodMonth(
            UUID accountId, Integer periodYear, Integer periodMonth);

    List<AccountBalance> findByPeriodYearAndPeriodMonth(Integer periodYear, Integer periodMonth);

    List<AccountBalance> findByAccountIdOrderByPeriodYearDescPeriodMonthDesc(UUID accountId);

    @Query("SELECT ab FROM AccountBalance ab WHERE ab.account.id = :accountId AND " +
           "(ab.periodYear < :year OR (ab.periodYear = :year AND ab.periodMonth < :month)) " +
           "ORDER BY ab.periodYear DESC, ab.periodMonth DESC LIMIT 1")
    Optional<AccountBalance> findPreviousPeriodBalance(
            @Param("accountId") UUID accountId,
            @Param("year") Integer year,
            @Param("month") Integer month);

    @Query("SELECT ab FROM AccountBalance ab WHERE ab.account.id = :accountId AND " +
           "(ab.periodYear > :year OR (ab.periodYear = :year AND ab.periodMonth >= :month)) " +
           "ORDER BY ab.periodYear ASC, ab.periodMonth ASC")
    List<AccountBalance> findBalancesFromPeriod(
            @Param("accountId") UUID accountId,
            @Param("year") Integer year,
            @Param("month") Integer month);

    void deleteByAccountIdAndPeriodYearAndPeriodMonth(UUID accountId, Integer periodYear, Integer periodMonth);
}
