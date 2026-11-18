-- V018: TOTP multi-factor authentication
--
-- The secret is stored through EncryptedStringConverter (AES-256-GCM with
-- app.encryption.key), so the column holds ciphertext with an ENC: prefix and
-- has to be wide enough for it. Recovery codes are stored as SHA-256 hashes and
-- are single use: used_at marks the moment a code was accepted.

ALTER TABLE users ADD COLUMN mfa_secret VARCHAR(512);
ALTER TABLE users ADD COLUMN mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE;
-- The RFC 6238 time step of the last accepted code, so a code cannot be
-- replayed inside its 30 second window or in a later one.
ALTER TABLE users ADD COLUMN mfa_last_used_step BIGINT;

CREATE TABLE user_mfa_recovery_codes (
    id UUID PRIMARY KEY,
    id_user UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    code_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    used_at TIMESTAMP
);

CREATE INDEX idx_user_mfa_recovery_codes_user ON user_mfa_recovery_codes (id_user);
