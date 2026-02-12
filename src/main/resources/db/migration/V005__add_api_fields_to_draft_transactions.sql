-- V005: Add API support to draft_transactions and device authentication
-- Support for AI-assisted transaction posting via REST API with OAuth 2.0 Device Flow

-- ============================================
-- Draft Transaction API Fields
-- ============================================

-- Add 'API' to source enum
ALTER TABLE draft_transactions DROP CONSTRAINT IF EXISTS chk_draft_source;
ALTER TABLE draft_transactions ADD CONSTRAINT chk_draft_source
    CHECK (source IN ('TELEGRAM', 'MANUAL', 'EMAIL', 'API'));

-- Add API source type (e.g., 'claude-code', 'gemini-cli', 'curl', etc.)
ALTER TABLE draft_transactions ADD COLUMN IF NOT EXISTS api_source VARCHAR(50);

-- Add metadata JSONB for items list, category, and other custom fields
ALTER TABLE draft_transactions ADD COLUMN IF NOT EXISTS metadata JSONB;

-- Create index for API source filtering
CREATE INDEX IF NOT EXISTS idx_draft_transactions_api_source
    ON draft_transactions(api_source) WHERE api_source IS NOT NULL;

-- Add comments for documentation
COMMENT ON COLUMN draft_transactions.api_source IS
    'External tool/client that created this draft via API (claude-code, gemini-cli, postman, etc.)';
COMMENT ON COLUMN draft_transactions.metadata IS
    'Additional metadata: items list, category, custom fields, etc. Stored as JSONB for flexibility.';

-- ============================================
-- Device Authentication (OAuth 2.0 Device Flow)
-- ============================================

-- Device authorization codes (temporary, for device flow)
CREATE TABLE device_codes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_code VARCHAR(100) UNIQUE NOT NULL,      -- Long random string for polling
    user_code VARCHAR(10) UNIQUE NOT NULL,         -- Short code for user to enter (e.g., "WDJB-MJHT")
    verification_uri VARCHAR(255) NOT NULL,        -- URL for user to visit
    client_id VARCHAR(50) NOT NULL,                -- Client identifier (e.g., "claude-code")

    -- Authorization status
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING, AUTHORIZED, EXPIRED, DENIED
    id_user UUID REFERENCES users(id),             -- NULL until user authorizes

    -- Timing
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP NOT NULL,                 -- Usually 15 minutes from creation
    authorized_at TIMESTAMP,

    CONSTRAINT chk_device_code_status CHECK (status IN ('PENDING', 'AUTHORIZED', 'EXPIRED', 'DENIED'))
);

CREATE INDEX idx_device_codes_device_code ON device_codes(device_code);
CREATE INDEX idx_device_codes_user_code ON device_codes(user_code);
CREATE INDEX idx_device_codes_status ON device_codes(status);
CREATE INDEX idx_device_codes_expires_at ON device_codes(expires_at);

COMMENT ON TABLE device_codes IS 'Temporary device authorization codes for OAuth 2.0 Device Flow';
COMMENT ON COLUMN device_codes.device_code IS 'Long random string used by device to poll for authorization';
COMMENT ON COLUMN device_codes.user_code IS 'Short code displayed to user (e.g., WDJB-MJHT)';
COMMENT ON COLUMN device_codes.client_id IS 'Client identifier (claude-code, docker-desktop, etc.)';

-- Device tokens (long-lived access tokens)
CREATE TABLE device_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_user UUID NOT NULL REFERENCES users(id),

    -- Token details
    token_hash VARCHAR(255) NOT NULL,              -- BCrypt hash of the token
    device_name VARCHAR(100),                      -- User-friendly name (e.g., "Claude Code on MacBook")
    client_id VARCHAR(50) NOT NULL,                -- Client that requested the token

    -- Scopes and permissions
    scopes VARCHAR(255),                           -- Comma-separated: "drafts:create,drafts:approve"

    -- Usage tracking
    last_used_at TIMESTAMP,
    last_used_ip VARCHAR(45),                      -- IPv4 or IPv6

    -- Lifecycle
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP,                          -- NULL = never expires
    revoked_at TIMESTAMP,
    revoked_by VARCHAR(100),

    -- Audit
    created_by VARCHAR(100)
);

CREATE INDEX idx_device_tokens_user ON device_tokens(id_user);
CREATE INDEX idx_device_tokens_token_hash ON device_tokens(token_hash);
CREATE INDEX idx_device_tokens_expires_at ON device_tokens(expires_at) WHERE expires_at IS NOT NULL;
CREATE INDEX idx_device_tokens_revoked ON device_tokens(revoked_at) WHERE revoked_at IS NULL;

COMMENT ON TABLE device_tokens IS 'Long-lived access tokens for device authentication (OAuth 2.0 Device Flow)';
COMMENT ON COLUMN device_tokens.token_hash IS 'BCrypt hash of the access token (never store plaintext)';
COMMENT ON COLUMN device_tokens.device_name IS 'User-friendly device name for management UI';
COMMENT ON COLUMN device_tokens.scopes IS 'Comma-separated list of granted permissions';
