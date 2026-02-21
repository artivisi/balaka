package com.artivisi.accountingfinance.repository;

import com.artivisi.accountingfinance.entity.AlertEvent;
import com.artivisi.accountingfinance.enums.AlertSeverity;
import com.artivisi.accountingfinance.enums.AlertType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface AlertEventRepository extends JpaRepository<AlertEvent, UUID> {

    @Query("SELECT e FROM AlertEvent e JOIN FETCH e.alertRule WHERE e.acknowledgedAt IS NULL ORDER BY e.triggeredAt DESC")
    List<AlertEvent> findByAcknowledgedAtIsNullOrderByTriggeredAtDesc();

    long countByAcknowledgedAtIsNull();

    @Query("SELECT CASE WHEN COUNT(e) > 0 THEN true ELSE false END FROM AlertEvent e " +
            "WHERE e.alertRule.id = :ruleId AND e.acknowledgedAt IS NULL AND e.triggeredAt > :since")
    boolean existsRecentUnacknowledged(@Param("ruleId") UUID ruleId, @Param("since") LocalDateTime since);

    @Query("SELECT e FROM AlertEvent e JOIN FETCH e.alertRule r WHERE " +
            "(:alertType IS NULL OR r.alertType = :alertType) AND " +
            "(:severity IS NULL OR e.severity = :severity) AND " +
            "(:acknowledged IS NULL OR (:acknowledged = true AND e.acknowledgedAt IS NOT NULL) OR (:acknowledged = false AND e.acknowledgedAt IS NULL)) " +
            "ORDER BY e.triggeredAt DESC")
    Page<AlertEvent> findByFilters(
            @Param("alertType") AlertType alertType,
            @Param("severity") AlertSeverity severity,
            @Param("acknowledged") Boolean acknowledged,
            Pageable pageable);
}
