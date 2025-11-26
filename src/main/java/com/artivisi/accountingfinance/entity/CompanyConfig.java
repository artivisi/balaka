package com.artivisi.accountingfinance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "company_config")
@Getter
@Setter
@NoArgsConstructor
public class CompanyConfig extends BaseEntity {

    @NotBlank(message = "Company name is required")
    @Size(max = 255, message = "Company name must not exceed 255 characters")
    @Column(name = "company_name", nullable = false, length = 255)
    private String companyName;

    @Column(name = "company_address", columnDefinition = "TEXT")
    private String companyAddress;

    @Size(max = 50, message = "Company phone must not exceed 50 characters")
    @Column(name = "company_phone", length = 50)
    private String companyPhone;

    @Size(max = 255, message = "Company email must not exceed 255 characters")
    @Column(name = "company_email", length = 255)
    private String companyEmail;

    @Size(max = 50, message = "Tax ID must not exceed 50 characters")
    @Column(name = "tax_id", length = 50)
    private String taxId;

    @Min(value = 1, message = "Fiscal year start month must be between 1 and 12")
    @Max(value = 12, message = "Fiscal year start month must be between 1 and 12")
    @Column(name = "fiscal_year_start_month", nullable = false)
    private Integer fiscalYearStartMonth = 1;

    @Size(max = 10, message = "Currency code must not exceed 10 characters")
    @Column(name = "currency_code", nullable = false, length = 10)
    private String currencyCode = "IDR";

    @Size(max = 255, message = "Signing officer name must not exceed 255 characters")
    @Column(name = "signing_officer_name", length = 255)
    private String signingOfficerName;

    @Size(max = 100, message = "Signing officer title must not exceed 100 characters")
    @Column(name = "signing_officer_title", length = 100)
    private String signingOfficerTitle;

    @Size(max = 500, message = "Company logo path must not exceed 500 characters")
    @Column(name = "company_logo_path", length = 500)
    private String companyLogoPath;
}
