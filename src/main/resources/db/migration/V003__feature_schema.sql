-- V003: Feature-Specific Schema
-- Tables: device auth, bank reconciliation, analysis reports,
-- transaction tags, smart alerts, payment tracking, recurring transactions

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

-- ============================================
-- Bank Statement Parser Configs
-- ============================================

CREATE TABLE bank_statement_parser_configs (
    id UUID PRIMARY KEY,
    row_version BIGINT NOT NULL DEFAULT 0,
    bank_type VARCHAR(20) NOT NULL,
    config_name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    date_column INTEGER NOT NULL,
    description_column INTEGER NOT NULL,
    debit_column INTEGER,
    credit_column INTEGER,
    balance_column INTEGER,
    date_format VARCHAR(50) NOT NULL,
    delimiter VARCHAR(5) NOT NULL DEFAULT ',',
    skip_header_rows INTEGER NOT NULL DEFAULT 1,
    encoding VARCHAR(20) DEFAULT 'UTF-8',
    decimal_separator VARCHAR(5) DEFAULT '.',
    thousand_separator VARCHAR(5) DEFAULT ',',
    is_system BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    deleted_at TIMESTAMP,

    CONSTRAINT chk_parser_bank_type CHECK (bank_type IN ('BCA', 'MANDIRI', 'BNI', 'BSI', 'CIMB', 'CUSTOM'))
);

CREATE INDEX idx_parser_config_bank_type ON bank_statement_parser_configs(bank_type);
CREATE INDEX idx_parser_config_active ON bank_statement_parser_configs(active);

-- ============================================
-- Bank Statements
-- ============================================

CREATE TABLE bank_statements (
    id UUID PRIMARY KEY,
    row_version BIGINT NOT NULL DEFAULT 0,
    id_bank_account UUID NOT NULL REFERENCES company_bank_accounts(id),
    id_parser_config UUID NOT NULL REFERENCES bank_statement_parser_configs(id),
    statement_period_start DATE NOT NULL,
    statement_period_end DATE NOT NULL,
    opening_balance DECIMAL(19, 2),
    closing_balance DECIMAL(19, 2),
    original_filename VARCHAR(500) NOT NULL,
    total_items INTEGER,
    total_debit DECIMAL(19, 2),
    total_credit DECIMAL(19, 2),
    imported_at TIMESTAMP,
    imported_by VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    deleted_at TIMESTAMP
);

CREATE INDEX idx_bs_bank_account ON bank_statements(id_bank_account);
CREATE INDEX idx_bs_period ON bank_statements(statement_period_start, statement_period_end);

-- ============================================
-- Bank Statement Items
-- ============================================

CREATE TABLE bank_statement_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_bank_statement UUID NOT NULL REFERENCES bank_statements(id) ON DELETE CASCADE,
    line_number INTEGER NOT NULL,
    transaction_date DATE NOT NULL,
    description TEXT,
    debit_amount DECIMAL(19, 2),
    credit_amount DECIMAL(19, 2),
    balance DECIMAL(19, 2),
    raw_line TEXT,
    match_status VARCHAR(20) NOT NULL DEFAULT 'UNMATCHED',
    match_type VARCHAR(20),
    id_matched_transaction UUID REFERENCES transactions(id),
    matched_at TIMESTAMP,
    matched_by VARCHAR(100),

    CONSTRAINT chk_bsi_match_status CHECK (match_status IN ('UNMATCHED', 'MATCHED', 'BANK_ONLY', 'BOOK_ONLY')),
    CONSTRAINT chk_bsi_match_type CHECK (match_type IS NULL OR match_type IN ('EXACT', 'FUZZY_DATE', 'KEYWORD', 'MANUAL'))
);

CREATE INDEX idx_bsi_statement ON bank_statement_items(id_bank_statement);
CREATE INDEX idx_bsi_match_status ON bank_statement_items(match_status);
CREATE INDEX idx_bsi_transaction_date ON bank_statement_items(transaction_date);
CREATE INDEX idx_bsi_matched_txn ON bank_statement_items(id_matched_transaction);

-- ============================================
-- Bank Reconciliations
-- ============================================

CREATE TABLE bank_reconciliations (
    id UUID PRIMARY KEY,
    row_version BIGINT NOT NULL DEFAULT 0,
    id_bank_account UUID NOT NULL REFERENCES company_bank_accounts(id),
    id_bank_statement UUID NOT NULL REFERENCES bank_statements(id),
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    book_balance DECIMAL(19, 2),
    bank_balance DECIMAL(19, 2),
    total_statement_items INTEGER,
    matched_count INTEGER,
    unmatched_bank_count INTEGER,
    unmatched_book_count INTEGER,
    completed_at TIMESTAMP,
    completed_by VARCHAR(100),
    notes VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    deleted_at TIMESTAMP,

    CONSTRAINT chk_recon_status CHECK (status IN ('DRAFT', 'IN_PROGRESS', 'COMPLETED'))
);

CREATE INDEX idx_recon_bank_account ON bank_reconciliations(id_bank_account);
CREATE INDEX idx_recon_statement ON bank_reconciliations(id_bank_statement);
CREATE INDEX idx_recon_status ON bank_reconciliations(status);
CREATE INDEX idx_recon_period ON bank_reconciliations(period_start, period_end);

-- ============================================
-- Reconciliation Items
-- ============================================

CREATE TABLE reconciliation_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_reconciliation UUID NOT NULL REFERENCES bank_reconciliations(id) ON DELETE CASCADE,
    id_statement_item UUID REFERENCES bank_statement_items(id),
    id_transaction UUID REFERENCES transactions(id),
    match_status VARCHAR(20) NOT NULL,
    match_type VARCHAR(20),
    match_confidence DECIMAL(5, 2),
    notes VARCHAR(500),

    CONSTRAINT chk_ri_match_status CHECK (match_status IN ('UNMATCHED', 'MATCHED', 'BANK_ONLY', 'BOOK_ONLY')),
    CONSTRAINT chk_ri_match_type CHECK (match_type IS NULL OR match_type IN ('EXACT', 'FUZZY_DATE', 'KEYWORD', 'MANUAL'))
);

CREATE INDEX idx_ri_reconciliation ON reconciliation_items(id_reconciliation);
CREATE INDEX idx_ri_statement_item ON reconciliation_items(id_statement_item);
CREATE INDEX idx_ri_transaction ON reconciliation_items(id_transaction);
CREATE INDEX idx_ri_match_status ON reconciliation_items(match_status);

-- ============================================
-- Analysis Reports
-- ============================================

CREATE TABLE analysis_reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    row_version BIGINT NOT NULL DEFAULT 0,
    title VARCHAR(255) NOT NULL,
    report_type VARCHAR(50) NOT NULL,
    industry VARCHAR(50),
    period_start DATE,
    period_end DATE,
    ai_source VARCHAR(50),
    ai_model VARCHAR(100),
    executive_summary TEXT,
    metrics JSONB,
    findings JSONB,
    recommendations JSONB,
    risks JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    deleted_at TIMESTAMP
);

-- ============================================
-- Transaction Tags
-- ============================================

CREATE TABLE tag_types (
    id UUID PRIMARY KEY,
    row_version BIGINT NOT NULL DEFAULT 0,
    code VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    deleted_at TIMESTAMP
);

CREATE TABLE tags (
    id UUID PRIMARY KEY,
    row_version BIGINT NOT NULL DEFAULT 0,
    id_tag_type UUID NOT NULL REFERENCES tag_types(id),
    code VARCHAR(20) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    deleted_at TIMESTAMP,
    CONSTRAINT uq_tag_code_per_type UNIQUE (id_tag_type, code)
);

CREATE TABLE transaction_tags (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_transaction UUID NOT NULL REFERENCES transactions(id),
    id_tag UUID NOT NULL REFERENCES tags(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_transaction_tag UNIQUE (id_transaction, id_tag)
);

CREATE INDEX idx_tags_tag_type ON tags(id_tag_type);
CREATE INDEX idx_transaction_tags_transaction ON transaction_tags(id_transaction);
CREATE INDEX idx_transaction_tags_tag ON transaction_tags(id_tag);

-- ============================================
-- Smart Alerts
-- ============================================

CREATE TABLE alert_rules (
    id UUID PRIMARY KEY,
    row_version BIGINT NOT NULL DEFAULT 0,
    alert_type VARCHAR(30) NOT NULL UNIQUE,
    threshold DECIMAL(19, 2) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    description VARCHAR(500),
    last_triggered_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    deleted_at TIMESTAMP
);

CREATE TABLE alert_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_alert_rule UUID NOT NULL REFERENCES alert_rules(id),
    triggered_at TIMESTAMP NOT NULL DEFAULT NOW(),
    severity VARCHAR(10) NOT NULL,
    message TEXT NOT NULL,
    details TEXT,
    acknowledged_at TIMESTAMP,
    acknowledged_by VARCHAR(100)
);

CREATE INDEX idx_alert_events_rule ON alert_events(id_alert_rule);
CREATE INDEX idx_alert_events_triggered ON alert_events(triggered_at DESC);
CREATE INDEX idx_alert_events_unacknowledged ON alert_events(acknowledged_at) WHERE acknowledged_at IS NULL;

-- ============================================
-- Invoice Payments
-- ============================================

CREATE TABLE invoice_payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_invoice UUID NOT NULL REFERENCES invoices(id),
    payment_date DATE NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    payment_method VARCHAR(20) NOT NULL,
    reference_number VARCHAR(100),
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_invoice_payments_invoice ON invoice_payments(id_invoice);
CREATE INDEX idx_invoice_payments_date ON invoice_payments(payment_date);

-- ============================================
-- Bill Payments
-- ============================================

CREATE TABLE bill_payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_bill UUID NOT NULL REFERENCES bills(id),
    payment_date DATE NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    payment_method VARCHAR(20) NOT NULL,
    reference_number VARCHAR(100),
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_bill_payments_bill ON bill_payments(id_bill);
CREATE INDEX idx_bill_payments_date ON bill_payments(payment_date);

-- ============================================
-- Recurring Transactions
-- ============================================

CREATE TABLE recurring_transactions (
    id UUID PRIMARY KEY,
    row_version BIGINT NOT NULL DEFAULT 0,
    name VARCHAR(255) NOT NULL,
    id_journal_template UUID NOT NULL REFERENCES journal_templates(id),
    amount DECIMAL(15, 2) NOT NULL,
    description VARCHAR(500) NOT NULL,
    frequency VARCHAR(20) NOT NULL,
    day_of_month INTEGER,
    day_of_week INTEGER,
    start_date DATE NOT NULL,
    end_date DATE,
    next_run_date DATE,
    last_run_date DATE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    skip_weekends BOOLEAN NOT NULL DEFAULT FALSE,
    auto_post BOOLEAN NOT NULL DEFAULT TRUE,
    total_runs INTEGER NOT NULL DEFAULT 0,
    max_occurrences INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    deleted_at TIMESTAMP,
    CONSTRAINT chk_recurring_day_of_month CHECK (day_of_month IS NULL OR (day_of_month >= 1 AND day_of_month <= 28)),
    CONSTRAINT chk_recurring_day_of_week CHECK (day_of_week IS NULL OR (day_of_week >= 1 AND day_of_week <= 7))
);

CREATE TABLE recurring_transaction_account_mappings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_recurring_transaction UUID NOT NULL REFERENCES recurring_transactions(id) ON DELETE CASCADE,
    id_template_line UUID NOT NULL REFERENCES journal_template_lines(id),
    id_account UUID NOT NULL REFERENCES chart_of_accounts(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE recurring_transaction_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_recurring_transaction UUID NOT NULL REFERENCES recurring_transactions(id),
    scheduled_date DATE NOT NULL,
    executed_at TIMESTAMP,
    id_transaction UUID REFERENCES transactions(id),
    status VARCHAR(20) NOT NULL,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_recurring_next_run ON recurring_transactions(next_run_date, status) WHERE deleted_at IS NULL;
CREATE INDEX idx_recurring_status ON recurring_transactions(status) WHERE deleted_at IS NULL;
CREATE INDEX idx_recurring_logs_recurring ON recurring_transaction_logs(id_recurring_transaction);
CREATE INDEX idx_recurring_logs_scheduled ON recurring_transaction_logs(scheduled_date DESC);
CREATE INDEX idx_recurring_account_mappings_recurring ON recurring_transaction_account_mappings(id_recurring_transaction);

-- ============================================
-- Fiscal Adjustments (Koreksi Fiskal)
-- ============================================

CREATE TABLE fiscal_adjustments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    year INTEGER NOT NULL,
    description VARCHAR(500) NOT NULL,
    adjustment_category VARCHAR(20) NOT NULL,
    adjustment_direction VARCHAR(20) NOT NULL,
    amount DECIMAL(15, 2) NOT NULL,
    account_code VARCHAR(20),
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_fiscal_adj_category CHECK (adjustment_category IN ('PERMANENT', 'TEMPORARY')),
    CONSTRAINT chk_fiscal_adj_direction CHECK (adjustment_direction IN ('POSITIVE', 'NEGATIVE')),
    CONSTRAINT chk_fiscal_adj_amount CHECK (amount > 0)
);

CREATE INDEX idx_fiscal_adjustments_year ON fiscal_adjustments(year);
