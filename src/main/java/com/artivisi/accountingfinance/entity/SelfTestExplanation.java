package com.artivisi.accountingfinance.entity;

import com.artivisi.accountingfinance.enums.SelfTestLine;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** User's explanation of one self-test line of one year. periodMonth 0 is the whole-year line, 1-12 a masa row. */
@Entity
@Table(name = "self_test_explanations")
@Getter
@Setter
public class SelfTestExplanation extends TimestampedEntity {

    @Column(name = "report_year", nullable = false)
    private Integer reportYear;

    @Enumerated(EnumType.STRING)
    @Column(name = "line_code", nullable = false, length = 40)
    private SelfTestLine line;

    @Column(name = "period_month", nullable = false)
    private Integer periodMonth;

    @Column(name = "explanation", nullable = false, columnDefinition = "TEXT")
    private String explanation;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy;
}
