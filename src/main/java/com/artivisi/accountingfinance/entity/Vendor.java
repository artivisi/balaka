package com.artivisi.accountingfinance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "vendors")
@Getter
@Setter
@NoArgsConstructor
public class Vendor {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotBlank(message = "Kode vendor wajib diisi")
    @Size(max = 50, message = "Kode vendor maksimal 50 karakter")
    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @NotBlank(message = "Nama vendor wajib diisi")
    @Size(max = 255, message = "Nama vendor maksimal 255 karakter")
    @Column(name = "name", nullable = false)
    private String name;

    @Size(max = 255, message = "Nama kontak maksimal 255 karakter")
    @Column(name = "contact_person")
    private String contactPerson;

    @Email(message = "Format email tidak valid")
    @Size(max = 255, message = "Email maksimal 255 karakter")
    @Column(name = "email")
    private String email;

    @Size(max = 50, message = "Nomor telepon maksimal 50 karakter")
    @Column(name = "phone", length = 50)
    private String phone;

    @Column(name = "address", columnDefinition = "TEXT")
    private String address;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Size(max = 20, message = "NPWP maksimal 20 karakter")
    @Column(name = "npwp", length = 20)
    private String npwp;

    @Size(max = 22, message = "NITKU maksimal 22 karakter")
    @Column(name = "nitku", length = 22)
    private String nitku;

    @Size(max = 16, message = "NIK maksimal 16 karakter")
    @Column(name = "nik", length = 16)
    private String nik;

    @Size(max = 10, message = "Tipe ID maksimal 10 karakter")
    @Column(name = "id_type", length = 10)
    private String idType = "TIN";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_default_expense_account")
    private ChartOfAccount defaultExpenseAccount;

    @Column(name = "payment_term_days")
    private Integer paymentTermDays;

    @Size(max = 100, message = "Nama bank maksimal 100 karakter")
    @Column(name = "bank_name", length = 100)
    private String bankName;

    @Size(max = 50, message = "Nomor rekening maksimal 50 karakter")
    @Column(name = "bank_account_number", length = 50)
    private String bankAccountNumber;

    @Size(max = 255, message = "Nama pemilik rekening maksimal 255 karakter")
    @Column(name = "bank_account_name")
    private String bankAccountName;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isActive() {
        return Boolean.TRUE.equals(active);
    }
}
