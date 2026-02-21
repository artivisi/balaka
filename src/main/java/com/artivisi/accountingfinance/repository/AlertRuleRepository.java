package com.artivisi.accountingfinance.repository;

import com.artivisi.accountingfinance.entity.AlertRule;
import com.artivisi.accountingfinance.enums.AlertType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlertRuleRepository extends JpaRepository<AlertRule, UUID> {

    List<AlertRule> findByEnabledTrue();

    List<AlertRule> findAllByOrderByAlertTypeAsc();

    Optional<AlertRule> findByAlertType(AlertType alertType);
}
