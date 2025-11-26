-- V003: Application Schema
-- All application tables and functions

-- ============================================
-- Company Configuration
-- ============================================

CREATE TABLE company_config (
    id UUID PRIMARY KEY,
    company_name VARCHAR(255) NOT NULL,
    company_address TEXT,
    company_phone VARCHAR(50),
    company_email VARCHAR(255),
    tax_id VARCHAR(50),
    fiscal_year_start_month INTEGER NOT NULL DEFAULT 1,
    currency_code VARCHAR(10) NOT NULL DEFAULT 'IDR',
    signing_officer_name VARCHAR(255),
    signing_officer_title VARCHAR(100),
    company_logo_path VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    deleted_at TIMESTAMP
);

-- Company Bank Accounts (multiple per company)
CREATE TABLE company_bank_accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bank_name VARCHAR(100) NOT NULL,
    bank_branch VARCHAR(100),
    account_number VARCHAR(50) NOT NULL,
    account_name VARCHAR(255) NOT NULL,
    currency_code VARCHAR(10) NOT NULL DEFAULT 'IDR',
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_company_bank_active ON company_bank_accounts(active);
CREATE INDEX idx_company_bank_default ON company_bank_accounts(is_default);

-- ============================================
-- Chart of Accounts
-- ============================================

CREATE TABLE chart_of_accounts (
    id UUID PRIMARY KEY,
    account_code VARCHAR(20) NOT NULL UNIQUE,
    account_name VARCHAR(255) NOT NULL,
    account_type VARCHAR(20) NOT NULL,
    normal_balance VARCHAR(10) NOT NULL,
    id_parent UUID REFERENCES chart_of_accounts(id),
    level INTEGER NOT NULL DEFAULT 1,
    is_header BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    is_permanent BOOLEAN NOT NULL DEFAULT TRUE,
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    deleted_at TIMESTAMP,

    CONSTRAINT chk_account_type CHECK (account_type IN ('ASSET', 'LIABILITY', 'EQUITY', 'REVENUE', 'EXPENSE')),
    CONSTRAINT chk_normal_balance CHECK (normal_balance IN ('DEBIT', 'CREDIT'))
);

CREATE INDEX idx_coa_account_code ON chart_of_accounts(account_code);
CREATE INDEX idx_coa_account_type ON chart_of_accounts(account_type);
CREATE INDEX idx_coa_parent ON chart_of_accounts(id_parent);
CREATE INDEX idx_coa_active ON chart_of_accounts(active);

-- ============================================
-- Journal Templates
-- ============================================

CREATE TABLE journal_templates (
    id UUID PRIMARY KEY,
    template_name VARCHAR(255) NOT NULL,
    category VARCHAR(20) NOT NULL,
    cash_flow_category VARCHAR(20) NOT NULL,
    template_type VARCHAR(20) NOT NULL DEFAULT 'SIMPLE',
    description TEXT,
    is_favorite BOOLEAN NOT NULL DEFAULT FALSE,
    is_system BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version INTEGER NOT NULL DEFAULT 1,
    usage_count INTEGER NOT NULL DEFAULT 0,
    last_used_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    deleted_at TIMESTAMP,

    CONSTRAINT chk_category CHECK (category IN ('INCOME', 'EXPENSE', 'PAYMENT', 'RECEIPT', 'TRANSFER')),
    CONSTRAINT chk_cash_flow_category CHECK (cash_flow_category IN ('OPERATING', 'INVESTING', 'FINANCING')),
    CONSTRAINT chk_template_type CHECK (template_type IN ('SIMPLE', 'DETAILED'))
);

CREATE INDEX idx_jt_category ON journal_templates(category);
CREATE INDEX idx_jt_active ON journal_templates(active);
CREATE INDEX idx_jt_is_favorite ON journal_templates(is_favorite);

CREATE TABLE journal_template_lines (
    id UUID PRIMARY KEY,
    id_journal_template UUID NOT NULL REFERENCES journal_templates(id) ON DELETE CASCADE,
    id_account UUID NOT NULL REFERENCES chart_of_accounts(id),
    position VARCHAR(10) NOT NULL,
    formula VARCHAR(255) NOT NULL DEFAULT 'amount',
    line_order INTEGER NOT NULL,
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP,

    CONSTRAINT chk_position CHECK (position IN ('DEBIT', 'CREDIT'))
);

CREATE INDEX idx_jtl_template ON journal_template_lines(id_journal_template);
CREATE INDEX idx_jtl_account ON journal_template_lines(id_account);

CREATE TABLE journal_template_tags (
    id UUID PRIMARY KEY,
    id_journal_template UUID NOT NULL REFERENCES journal_templates(id) ON DELETE CASCADE,
    tag VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_template_tag UNIQUE (id_journal_template, tag)
);

CREATE INDEX idx_jtt_template ON journal_template_tags(id_journal_template);
CREATE INDEX idx_jtt_tag ON journal_template_tags(tag);

CREATE TABLE user_template_preferences (
    id UUID PRIMARY KEY,
    id_user UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    id_journal_template UUID NOT NULL REFERENCES journal_templates(id) ON DELETE CASCADE,
    is_favorite BOOLEAN NOT NULL DEFAULT FALSE,
    last_used_at TIMESTAMP,
    use_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_user_template UNIQUE (id_user, id_journal_template)
);

CREATE INDEX idx_utp_user ON user_template_preferences(id_user);
CREATE INDEX idx_utp_template ON user_template_preferences(id_journal_template);
CREATE INDEX idx_utp_favorite ON user_template_preferences(id_user, is_favorite);
CREATE INDEX idx_utp_last_used ON user_template_preferences(id_user, last_used_at DESC);

-- ============================================
-- Transaction Sequences
-- ============================================

CREATE TABLE transaction_sequences (
    id UUID PRIMARY KEY,
    sequence_type VARCHAR(50) NOT NULL,
    prefix VARCHAR(20) NOT NULL,
    year INTEGER NOT NULL,
    last_number INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_sequence_type_year UNIQUE (sequence_type, year)
);

CREATE INDEX idx_ts_sequence_type ON transaction_sequences(sequence_type, year);

-- ============================================
-- Clients
-- ============================================

CREATE TABLE clients (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    contact_person VARCHAR(255),
    email VARCHAR(255),
    phone VARCHAR(50),
    address TEXT,
    notes TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_clients_active ON clients(active);
CREATE INDEX idx_clients_name ON clients(name);

-- ============================================
-- Projects
-- ============================================

CREATE TABLE projects (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    id_client UUID REFERENCES clients(id),
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    contract_value DECIMAL(19, 2),
    budget_amount DECIMAL(19, 2),
    start_date DATE,
    end_date DATE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_projects_client ON projects(id_client);
CREATE INDEX idx_projects_status ON projects(status);

-- ============================================
-- Project Milestones
-- ============================================

CREATE TABLE project_milestones (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_project UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    sequence INT NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    weight_percent INT NOT NULL DEFAULT 0,
    target_date DATE,
    actual_date DATE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(id_project, sequence)
);

CREATE INDEX idx_milestones_project ON project_milestones(id_project);
CREATE INDEX idx_milestones_status ON project_milestones(status);

-- ============================================
-- Transactions
-- ============================================

CREATE TABLE transactions (
    id UUID PRIMARY KEY,
    transaction_number VARCHAR(50) NOT NULL UNIQUE,
    transaction_date DATE NOT NULL,
    id_journal_template UUID NOT NULL REFERENCES journal_templates(id),
    id_project UUID REFERENCES projects(id),
    amount DECIMAL(19, 2) NOT NULL,
    description VARCHAR(500) NOT NULL,
    reference_number VARCHAR(100),
    notes TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    void_reason VARCHAR(50),
    void_notes TEXT,
    voided_at TIMESTAMP,
    voided_by VARCHAR(100),
    posted_at TIMESTAMP,
    posted_by VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    deleted_at TIMESTAMP,

    CONSTRAINT chk_transaction_status CHECK (status IN ('DRAFT', 'POSTED', 'VOID')),
    CONSTRAINT chk_void_reason CHECK (void_reason IS NULL OR void_reason IN ('INPUT_ERROR', 'DUPLICATE', 'CANCELLED', 'OTHER'))
);

CREATE INDEX idx_trx_number ON transactions(transaction_number);
CREATE INDEX idx_trx_date ON transactions(transaction_date);
CREATE INDEX idx_trx_template ON transactions(id_journal_template);
CREATE INDEX idx_trx_status ON transactions(status);
CREATE INDEX idx_transactions_project ON transactions(id_project);

-- ============================================
-- Project Payment Terms
-- ============================================

CREATE TABLE project_payment_terms (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_project UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    sequence INT NOT NULL,
    name VARCHAR(255) NOT NULL,
    percentage DECIMAL(5, 2),
    amount DECIMAL(19, 2),
    due_trigger VARCHAR(20) NOT NULL,
    id_milestone UUID REFERENCES project_milestones(id),
    due_date DATE,
    id_template UUID REFERENCES journal_templates(id),
    auto_post BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(id_project, sequence)
);

CREATE INDEX idx_payment_terms_project ON project_payment_terms(id_project);
CREATE INDEX idx_payment_terms_milestone ON project_payment_terms(id_milestone);
CREATE INDEX idx_payment_terms_template ON project_payment_terms(id_template);

-- ============================================
-- Transaction Account Mappings
-- ============================================

CREATE TABLE transaction_account_mappings (
    id UUID PRIMARY KEY,
    id_transaction UUID NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    id_template_line UUID NOT NULL REFERENCES journal_template_lines(id),
    id_account UUID NOT NULL REFERENCES chart_of_accounts(id),
    amount DECIMAL(19, 2),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_trx_mapping UNIQUE (id_transaction, id_template_line)
);

CREATE INDEX idx_tam_transaction ON transaction_account_mappings(id_transaction);
CREATE INDEX idx_tam_template_line ON transaction_account_mappings(id_template_line);

-- ============================================
-- Journal Entries
-- ============================================

CREATE TABLE journal_entries (
    id UUID PRIMARY KEY,
    journal_number VARCHAR(50) NOT NULL,
    journal_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    posted_at TIMESTAMP,
    voided_at TIMESTAMP,
    void_reason VARCHAR(500),
    id_transaction UUID REFERENCES transactions(id),
    id_account UUID NOT NULL REFERENCES chart_of_accounts(id),
    id_project UUID REFERENCES projects(id),
    description VARCHAR(500) NOT NULL,
    debit_amount DECIMAL(19, 2) NOT NULL DEFAULT 0,
    credit_amount DECIMAL(19, 2) NOT NULL DEFAULT 0,
    reference_number VARCHAR(100),
    is_reversal BOOLEAN NOT NULL DEFAULT FALSE,
    id_reversed_entry UUID REFERENCES journal_entries(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    deleted_at TIMESTAMP,

    CONSTRAINT chk_debit_or_credit CHECK (
        (debit_amount > 0 AND credit_amount = 0) OR
        (debit_amount = 0 AND credit_amount > 0)
    ),
    CONSTRAINT chk_status CHECK (status IN ('DRAFT', 'POSTED', 'VOID'))
);

CREATE INDEX idx_je_number ON journal_entries(journal_number);
CREATE INDEX idx_je_date ON journal_entries(journal_date);
CREATE INDEX idx_je_status ON journal_entries(status);
CREATE INDEX idx_je_transaction ON journal_entries(id_transaction);
CREATE INDEX idx_je_account ON journal_entries(id_account);
CREATE INDEX idx_je_account_date ON journal_entries(id_account, journal_date);
CREATE INDEX idx_journal_entries_project ON journal_entries(id_project);

-- ============================================
-- Invoices
-- ============================================

CREATE TABLE invoices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_number VARCHAR(50) NOT NULL UNIQUE,
    id_client UUID NOT NULL REFERENCES clients(id),
    id_project UUID REFERENCES projects(id),
    id_payment_term UUID REFERENCES project_payment_terms(id),
    invoice_date DATE NOT NULL,
    due_date DATE NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    sent_at TIMESTAMP,
    paid_at TIMESTAMP,
    id_journal_entry UUID,
    id_transaction UUID REFERENCES transactions(id),
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_invoices_client ON invoices(id_client);
CREATE INDEX idx_invoices_project ON invoices(id_project);
CREATE INDEX idx_invoices_status ON invoices(status);
CREATE INDEX idx_invoices_due_date ON invoices(due_date);

-- ============================================
-- Account Balances (Materialized)
-- ============================================

CREATE TABLE account_balances (
    id UUID PRIMARY KEY,
    id_account UUID NOT NULL REFERENCES chart_of_accounts(id),
    period_year INTEGER NOT NULL,
    period_month INTEGER NOT NULL,
    opening_balance DECIMAL(19, 2) NOT NULL DEFAULT 0,
    debit_total DECIMAL(19, 2) NOT NULL DEFAULT 0,
    credit_total DECIMAL(19, 2) NOT NULL DEFAULT 0,
    closing_balance DECIMAL(19, 2) NOT NULL DEFAULT 0,
    entry_count INTEGER NOT NULL DEFAULT 0,
    last_calculated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_account_period UNIQUE (id_account, period_year, period_month)
);

CREATE INDEX idx_ab_account ON account_balances(id_account);
CREATE INDEX idx_ab_period ON account_balances(period_year, period_month);
CREATE INDEX idx_ab_account_period ON account_balances(id_account, period_year, period_month);

-- Function to update account balance
CREATE OR REPLACE FUNCTION update_account_balance(
    p_account_id UUID,
    p_year INTEGER,
    p_month INTEGER
) RETURNS VOID AS $$
DECLARE
    v_opening DECIMAL(19, 2);
    v_debit DECIMAL(19, 2);
    v_credit DECIMAL(19, 2);
    v_closing DECIMAL(19, 2);
    v_count INTEGER;
    v_normal_balance VARCHAR(10);
BEGIN
    -- Get normal balance for the account
    SELECT normal_balance INTO v_normal_balance
    FROM chart_of_accounts WHERE id = p_account_id;

    -- Calculate opening balance from previous period
    SELECT COALESCE(closing_balance, 0) INTO v_opening
    FROM account_balances
    WHERE id_account = p_account_id
      AND (period_year < p_year OR (period_year = p_year AND period_month < p_month))
    ORDER BY period_year DESC, period_month DESC
    LIMIT 1;

    IF v_opening IS NULL THEN
        v_opening := 0;
    END IF;

    -- Calculate totals for the period
    SELECT
        COALESCE(SUM(debit_amount), 0),
        COALESCE(SUM(credit_amount), 0),
        COUNT(*)
    INTO v_debit, v_credit, v_count
    FROM journal_entries je
    JOIN transactions t ON je.id_transaction = t.id
    WHERE je.id_account = p_account_id
      AND EXTRACT(YEAR FROM je.journal_date) = p_year
      AND EXTRACT(MONTH FROM je.journal_date) = p_month
      AND t.status = 'POSTED';

    -- Calculate closing balance based on normal balance
    IF v_normal_balance = 'DEBIT' THEN
        v_closing := v_opening + v_debit - v_credit;
    ELSE
        v_closing := v_opening - v_debit + v_credit;
    END IF;

    -- Upsert the balance record
    INSERT INTO account_balances (
        id, id_account, period_year, period_month,
        opening_balance, debit_total, credit_total, closing_balance,
        entry_count, last_calculated_at
    ) VALUES (
        gen_random_uuid(), p_account_id, p_year, p_month,
        v_opening, v_debit, v_credit, v_closing,
        v_count, NOW()
    )
    ON CONFLICT (id_account, period_year, period_month)
    DO UPDATE SET
        opening_balance = v_opening,
        debit_total = v_debit,
        credit_total = v_credit,
        closing_balance = v_closing,
        entry_count = v_count,
        last_calculated_at = NOW(),
        updated_at = NOW();
END;
$$ LANGUAGE plpgsql;

-- ============================================
-- Amortization Schedules
-- ============================================

CREATE TABLE amortization_schedules (
    id UUID PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    schedule_type VARCHAR(50) NOT NULL,

    id_source_account UUID NOT NULL REFERENCES chart_of_accounts(id),
    id_target_account UUID NOT NULL REFERENCES chart_of_accounts(id),

    total_amount DECIMAL(19, 2) NOT NULL,
    period_amount DECIMAL(19, 2) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    frequency VARCHAR(20) NOT NULL DEFAULT 'MONTHLY',
    total_periods INT NOT NULL,

    completed_periods INT NOT NULL DEFAULT 0,
    amortized_amount DECIMAL(19, 2) NOT NULL DEFAULT 0,
    remaining_amount DECIMAL(19, 2) NOT NULL,

    auto_post BOOLEAN NOT NULL DEFAULT FALSE,
    post_day INT DEFAULT 1,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    deleted_at TIMESTAMP,

    CONSTRAINT chk_schedule_type CHECK (schedule_type IN ('PREPAID_EXPENSE', 'UNEARNED_REVENUE', 'INTANGIBLE_ASSET', 'ACCRUED_REVENUE')),
    CONSTRAINT chk_schedule_frequency CHECK (frequency IN ('MONTHLY', 'QUARTERLY')),
    CONSTRAINT chk_schedule_status CHECK (status IN ('ACTIVE', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT chk_post_day CHECK (post_day BETWEEN 1 AND 28)
);

CREATE INDEX idx_amort_schedules_status ON amortization_schedules(status);
CREATE INDEX idx_amort_schedules_type ON amortization_schedules(schedule_type);
CREATE INDEX idx_amort_schedules_code ON amortization_schedules(code);

CREATE TABLE amortization_entries (
    id UUID PRIMARY KEY,
    id_schedule UUID NOT NULL REFERENCES amortization_schedules(id),
    period_number INT NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,

    id_journal_entry UUID,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',

    generated_at TIMESTAMP,
    posted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_entry_status CHECK (status IN ('PENDING', 'POSTED', 'SKIPPED')),
    CONSTRAINT uk_schedule_period UNIQUE (id_schedule, period_number)
);

CREATE INDEX idx_amort_entries_schedule ON amortization_entries(id_schedule);
CREATE INDEX idx_amort_entries_status ON amortization_entries(status);
CREATE INDEX idx_amort_entries_period_end ON amortization_entries(period_end);
