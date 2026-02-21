package com.artivisi.accountingfinance.entity;

import com.artivisi.accountingfinance.enums.AlertType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "alert_rules")
@Getter
@Setter
@SQLRestriction("deleted_at IS NULL")
public class AlertRule extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false, unique = true, length = 30)
    private AlertType alertType;

    @Column(name = "threshold", nullable = false, precision = 19, scale = 2)
    private BigDecimal threshold;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "last_triggered_at")
    private LocalDateTime lastTriggeredAt;
}
