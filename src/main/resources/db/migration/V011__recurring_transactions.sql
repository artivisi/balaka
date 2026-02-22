-- Recurring Transactions: scheduled auto-posting
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
