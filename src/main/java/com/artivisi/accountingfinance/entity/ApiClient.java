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
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Service account for the OAuth2 client_credentials grant. Headless callers
 * exchange client_id + client_secret for a short-lived bearer token at
 * POST /api/oauth/token. The secret is stored as a BCrypt hash and shown in
 * plaintext exactly once, at creation.
 */
@Entity
@Table(name = "api_clients")
@Getter
@Setter
@NoArgsConstructor
public class ApiClient {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotBlank(message = "Client ID wajib diisi")
    @Size(max = 50)
    @Column(name = "client_id", nullable = false, unique = true, length = 50)
    private String clientId;

    @NotBlank(message = "Client secret hash wajib diisi")
    @Size(max = 255)
    @Column(name = "client_secret_hash", nullable = false, length = 255)
    private String clientSecretHash;

    @NotBlank(message = "Nama klien wajib diisi")
    @Size(max = 100, message = "Nama klien maksimal 100 karakter")
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @NotBlank(message = "Scope wajib diisi")
    @Size(max = 255)
    @Column(name = "scopes", nullable = false, length = 255)
    private String scopes;

    /**
     * Acting user for audit: tokens issued to this client authenticate as this
     * user, so postings carry a meaningful created_by.
     */
    @NotNull(message = "User layanan wajib diisi")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_user", nullable = false)
    private User user;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Size(max = 100)
    @Column(name = "created_by", length = 100)
    private String createdBy;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
