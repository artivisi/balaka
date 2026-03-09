package com.artivisi.accountingfinance.entity;

import com.artivisi.accountingfinance.security.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "employees")
@Getter
@Setter
@NoArgsConstructor
public class Employee extends TimestampedEntity {

    @NotBlank(message = "NIK karyawan wajib diisi")
    @Size(max = 20, message = "NIK karyawan maksimal 20 karakter")
    @Column(name = "employee_id", nullable = false, unique = true, length = 20)
    private String employeeId;

    @NotBlank(message = "Nama karyawan wajib diisi")
    @Size(max = 255, message = "Nama karyawan maksimal 255 karakter")
    @Column(name = "name", nullable = false)
    private String name;

    @Email(message = "Format email tidak valid")
    @Size(max = 255, message = "Email maksimal 255 karakter")
    @Column(name = "email")
    private String email;

    @Size(max = 50, message = "Nomor telepon maksimal 50 karakter")
    @Column(name = "phone", length = 50)
    private String phone;

    @Column(name = "address", columnDefinition = "TEXT")
    private String address;

    // Link to user account for self-service access
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_user")
    private User user;

    // Tax identification - optional fields, validated only when provided
    // Encrypted at rest (PII protection)
    @Size(max = 20, message = "NPWP maksimal 20 karakter")
    @Pattern(regexp = "^$|^[0-9.\\-]{15,20}$", message = "NPWP harus 15-20 digit")
    @Column(name = "npwp", length = 255)  // Extended for encrypted data
    @Convert(converter = EncryptedStringConverter.class)
    private String npwp;

    // Encrypted at rest (PII protection)
    @Size(max = 16, message = "NIK KTP maksimal 16 karakter")
    @Pattern(regexp = "^$|^\\d{16}$", message = "NIK KTP harus 16 digit angka")
    @Column(name = "nik_ktp", length = 255)  // Extended for encrypted data
    @Convert(converter = EncryptedStringConverter.class)
    private String nikKtp;

    @NotNull(message = "Status PTKP wajib diisi")
    @Enumerated(EnumType.STRING)
    @Column(name = "ptkp_status", nullable = false, length = 10)
    private PtkpStatus ptkpStatus = PtkpStatus.TK_0;

    // Employment details
    @NotNull(message = "Tanggal mulai kerja wajib diisi")
    @Column(name = "hire_date", nullable = false)
    private LocalDate hireDate;

    @Column(name = "resign_date")
    private LocalDate resignDate;

    @NotNull(message = "Tipe kepegawaian wajib diisi")
    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", nullable = false, length = 20)
    private EmploymentType employmentType = EmploymentType.PERMANENT;

    @NotNull(message = "Status kepegawaian wajib diisi")
    @Enumerated(EnumType.STRING)
    @Column(name = "employment_status", nullable = false, length = 20)
    private EmploymentStatus employmentStatus = EmploymentStatus.ACTIVE;

    @Size(max = 100, message = "Jabatan maksimal 100 karakter")
    @Column(name = "job_title", length = 100)
    private String jobTitle;

    @Size(max = 100, message = "Departemen maksimal 100 karakter")
    @Column(name = "department", length = 100)
    private String department;

    // Bank account for salary payment
    @Size(max = 100, message = "Nama bank maksimal 100 karakter")
    @Column(name = "bank_name", length = 100)
    private String bankName;

    // Encrypted at rest (PII protection - financial data)
    @Size(max = 50, message = "Nomor rekening maksimal 50 karakter")
    @Column(name = "bank_account_number", length = 255)  // Extended for encrypted data
    @Convert(converter = EncryptedStringConverter.class)
    private String bankAccountNumber;

    @Size(max = 255, message = "Nama pemilik rekening maksimal 255 karakter")
    @Column(name = "bank_account_name")
    private String bankAccountName;

    // BPJS registration - Encrypted at rest (PII protection)
    @Size(max = 20, message = "Nomor BPJS Kesehatan maksimal 20 karakter")
    @Column(name = "bpjs_kesehatan_number", length = 255)  // Extended for encrypted data
    @Convert(converter = EncryptedStringConverter.class)
    private String bpjsKesehatanNumber;

    // Encrypted at rest (PII protection)
    @Size(max = 20, message = "Nomor BPJS Ketenagakerjaan maksimal 20 karakter")
    @Column(name = "bpjs_ketenagakerjaan_number", length = 255)  // Extended for encrypted data
    @Convert(converter = EncryptedStringConverter.class)
    private String bpjsKetenagakerjaanNumber;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    public boolean isActive() {
        return Boolean.TRUE.equals(active);
    }

    public String getDisplayName() {
        return employeeId + " - " + name;
    }
}
