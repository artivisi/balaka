package com.artivisi.accountingfinance.entity;

import com.artivisi.accountingfinance.enums.AccountType;
import com.artivisi.accountingfinance.enums.CloseCheckRole;
import com.artivisi.accountingfinance.enums.SelfTestRole;
import com.artivisi.accountingfinance.enums.FiscalTreatment;
import com.artivisi.accountingfinance.enums.NormalBalance;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "chart_of_accounts")
@Getter
@Setter
@NoArgsConstructor
@SQLRestriction("deleted_at IS NULL")
@com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class ChartOfAccount extends BaseEntity {

    @NotBlank(message = "Kode akun harus diisi")
    @Size(max = 20, message = "Kode akun maksimal 20 karakter")
    @Column(name = "account_code", nullable = false, unique = true, length = 20)
    private String accountCode;

    @NotBlank(message = "Nama akun harus diisi")
    @Size(max = 255, message = "Nama akun maksimal 255 karakter")
    @Column(name = "account_name", nullable = false, length = 255)
    private String accountName;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 20)
    private AccountType accountType;

    @Enumerated(EnumType.STRING)
    @Column(name = "normal_balance", nullable = false, length = 10)
    private NormalBalance normalBalance;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_parent")
    private ChartOfAccount parent;

    @JsonIgnore
    @OneToMany(mappedBy = "parent", fetch = FetchType.LAZY)
    @OrderBy("accountCode ASC")
    private List<ChartOfAccount> children = new ArrayList<>();

    @Min(value = 1, message = "Level minimal 1")
    @Column(name = "level", nullable = false)
    private Integer level = 1;

    @Column(name = "is_header", nullable = false)
    private Boolean isHeader = false;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @Column(name = "is_permanent", nullable = false)
    private Boolean permanent = true;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /** Only on EXPENSE accounts. Null means the account has not been classified yet. */
    @Enumerated(EnumType.STRING)
    @Column(name = "fiscal_treatment", length = 20)
    private FiscalTreatment fiscalTreatment;

    /** TRUE: a payment on this account normally needs withholding. Null = not classified. */
    @Column(name = "service_expense")
    private Boolean serviceExpense;

    /** Role in the period close checks. Only on non-header accounts of the types the role allows. */
    @Enumerated(EnumType.STRING)
    @Column(name = "close_check_role", length = 30)
    private CloseCheckRole closeCheckRole;

    /** Role in the tax-office self-test. Only on non-header accounts of the type the role allows. */
    @Enumerated(EnumType.STRING)
    @Column(name = "self_test_role", length = 30)
    private SelfTestRole selfTestRole;
}
