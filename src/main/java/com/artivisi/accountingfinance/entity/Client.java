package com.artivisi.accountingfinance.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "clients")
@Getter
@Setter
@NoArgsConstructor
public class Client extends TimestampedEntity {

    @NotBlank(message = "Kode klien wajib diisi")
    @Size(max = 50, message = "Kode klien maksimal 50 karakter")
    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @NotBlank(message = "Nama klien wajib diisi")
    @Size(max = 255, message = "Nama klien maksimal 255 karakter")
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

    // Tax identification fields (for Coretax integration)
    @Pattern(regexp = "^$|^\\d{2}\\.\\d{3}\\.\\d{3}\\.\\d-\\d{3}\\.\\d{3}$|^\\d{16}$",
            message = "Format NPWP tidak valid (XX.XXX.XXX.X-XXX.XXX atau 16 digit)")
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

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @JsonIgnore
    @OneToMany(mappedBy = "client")
    private List<Project> projects = new ArrayList<>();

    @JsonIgnore
    @OneToMany(mappedBy = "client")
    private List<Invoice> invoices = new ArrayList<>();

    public boolean isActive() {
        return Boolean.TRUE.equals(active);
    }
}
