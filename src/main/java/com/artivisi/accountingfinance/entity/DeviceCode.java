package com.artivisi.accountingfinance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Device authorization code for OAuth 2.0 Device Flow.
 * Temporary entity used during the device authorization process.
 */
@Entity
@Table(name = "device_codes")
@Getter
@Setter
@NoArgsConstructor
public class DeviceCode {

    public enum Status {
        PENDING,    // Waiting for user authorization
        AUTHORIZED, // User authorized the device
        EXPIRED,    // Code expired before authorization
        DENIED      // User denied authorization
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotBlank(message = "Device code is required")
    @Size(max = 100)
    @Column(name = "device_code", nullable = false, unique = true, length = 100)
    private String deviceCode;

    @NotBlank(message = "User code is required")
    @Size(max = 10)
    @Column(name = "user_code", nullable = false, unique = true, length = 10)
    private String userCode;

    @NotBlank(message = "Verification URI is required")
    @Size(max = 255)
    @Column(name = "verification_uri", nullable = false, length = 255)
    private String verificationUri;

    @NotBlank(message = "Client ID is required")
    @Size(max = 50)
    @Column(name = "client_id", nullable = false, length = 50)
    private String clientId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_user")
    private User user;

    @NotNull
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @NotNull
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "authorized_at")
    private LocalDateTime authorizedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.expiresAt == null) {
            // Default: 15 minutes
            this.expiresAt = this.createdAt.plusMinutes(15);
        }
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean isPending() {
        return status == Status.PENDING && !isExpired();
    }

    public void authorize(User user) {
        this.user = user;
        this.status = Status.AUTHORIZED;
        this.authorizedAt = LocalDateTime.now();
    }

    public void deny() {
        this.status = Status.DENIED;
    }

    public void expire() {
        if (this.status == Status.PENDING) {
            this.status = Status.EXPIRED;
        }
    }
}
