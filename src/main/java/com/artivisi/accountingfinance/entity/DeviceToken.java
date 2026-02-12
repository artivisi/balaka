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
 * Device access token for API authentication.
 * Long-lived token issued after successful device authorization.
 */
@Entity
@Table(name = "device_tokens")
@Getter
@Setter
@NoArgsConstructor
public class DeviceToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_user", nullable = false)
    private User user;

    @NotBlank(message = "Token hash is required")
    @Size(max = 255)
    @Column(name = "token_hash", nullable = false, length = 255)
    private String tokenHash;

    @Size(max = 100)
    @Column(name = "device_name", length = 100)
    private String deviceName;

    @NotBlank(message = "Client ID is required")
    @Size(max = 50)
    @Column(name = "client_id", nullable = false, length = 50)
    private String clientId;

    @Size(max = 255)
    @Column(name = "scopes", length = 255)
    private String scopes;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Size(max = 45)
    @Column(name = "last_used_ip", length = 45)
    private String lastUsedIp;

    @NotNull
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Size(max = 100)
    @Column(name = "revoked_by", length = 100)
    private String revokedBy;

    @Size(max = 100)
    @Column(name = "created_by", length = 100)
    private String createdBy;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.expiresAt == null) {
            // Default: 30 days
            this.expiresAt = this.createdAt.plusDays(30);
        }
    }

    public boolean isValid() {
        if (revokedAt != null) {
            return false;
        }
        if (expiresAt != null && LocalDateTime.now().isAfter(expiresAt)) {
            return false;
        }
        return true;
    }

    public void revoke(String revokedBy) {
        this.revokedAt = LocalDateTime.now();
        this.revokedBy = revokedBy;
    }

    public void updateLastUsed(String ipAddress) {
        this.lastUsedAt = LocalDateTime.now();
        this.lastUsedIp = ipAddress;
    }

    public boolean hasScope(String scope) {
        if (scopes == null || scopes.isBlank()) {
            return false;
        }
        return scopes.contains(scope);
    }
}
