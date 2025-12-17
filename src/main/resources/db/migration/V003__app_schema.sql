-- V003: Application Schema
-- All application tables and functions

-- ============================================
-- Company Configuration
-- ============================================

CREATE TABLE company_config (
    id UUID PRIMARY KEY,
    row_version BIGINT NOT NULL DEFAULT 0,
    company_name VARCHAR(255) NOT NULL,
    company_address TEXT,
    company_phone VARCHAR(50),
    company_email VARCHAR(255),
    tax_id VARCHAR(50),
    npwp VARCHAR(20),                    -- 16 digits formatted: XX.XXX.XXX.X-XXX.XXX
    nitku VARCHAR(22),                   -- 22 characters (NPWP + 6 digit branch)
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
    row_version BIGINT NOT NULL DEFAULT 0,
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
    row_version BIGINT NOT NULL DEFAULT 0,
    template_name VARCHAR(255) NOT NULL,
    category VARCHAR(20) NOT NULL,
    cash_flow_category VARCHAR(20) NOT NULL,
    template_type VARCHAR(20) NOT NULL DEFAULT 'SIMPLE',
    description TEXT,
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

CREATE TABLE journal_template_lines (
    id UUID PRIMARY KEY,
    id_journal_template UUID NOT NULL REFERENCES journal_templates(id) ON DELETE CASCADE,
    id_account UUID REFERENCES chart_of_accounts(id),
    position VARCHAR(10) NOT NULL,
    formula VARCHAR(255) NOT NULL DEFAULT 'amount',
    line_order INTEGER NOT NULL,
    description TEXT,
    account_hint VARCHAR(100),
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
    -- Tax identification fields (for Coretax integration)
    npwp VARCHAR(20),                    -- 16 digits formatted: XX.XXX.XXX.X-XXX.XXX
    nitku VARCHAR(22),                   -- 22 characters (NPWP + 6 digit branch)
    nik VARCHAR(16),                     -- 16 digit NIK for non-PKP clients
    id_type VARCHAR(10) DEFAULT 'TIN',   -- TIN (NPWP) or NIK
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_client_id_type CHECK (id_type IN ('TIN', 'NIK'))
);

CREATE INDEX idx_clients_active ON clients(active);
CREATE INDEX idx_clients_name ON clients(name);
CREATE INDEX idx_clients_npwp ON clients(npwp);

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
    row_version BIGINT NOT NULL DEFAULT 0,
    transaction_number VARCHAR(50) UNIQUE,  -- Generated when posting, null for drafts
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
-- Transaction Variables (for DETAILED templates)
-- ============================================

CREATE TABLE transaction_variables (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_transaction UUID NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    variable_name VARCHAR(100) NOT NULL,
    variable_value DECIMAL(19, 2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_trx_variable UNIQUE (id_transaction, variable_name)
);

CREATE INDEX idx_tv_transaction ON transaction_variables(id_transaction);

-- ============================================
-- Journal Entries
-- ============================================

CREATE TABLE journal_entries (
    id UUID PRIMARY KEY,
    row_version BIGINT NOT NULL DEFAULT 0,
    journal_number VARCHAR(50),
    posted_at TIMESTAMP,
    voided_at TIMESTAMP,
    void_reason VARCHAR(500),
    id_transaction UUID NOT NULL REFERENCES transactions(id),
    id_account UUID NOT NULL REFERENCES chart_of_accounts(id),
    id_project UUID REFERENCES projects(id),
    debit_amount DECIMAL(19, 2) NOT NULL DEFAULT 0,
    credit_amount DECIMAL(19, 2) NOT NULL DEFAULT 0,
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
    )
);

CREATE INDEX idx_je_number ON journal_entries(journal_number);
CREATE INDEX idx_je_transaction ON journal_entries(id_transaction);
CREATE INDEX idx_je_account ON journal_entries(id_account);
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
-- Amortization Schedules
-- ============================================

CREATE TABLE amortization_schedules (
    id UUID PRIMARY KEY,
    row_version BIGINT NOT NULL DEFAULT 0,
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
    journal_number VARCHAR(20),
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
CREATE INDEX idx_amortization_entries_journal_number ON amortization_entries(journal_number);

-- ============================================
-- Documents (Attachments)
-- ============================================

CREATE TABLE documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_transaction UUID REFERENCES transactions(id) ON DELETE CASCADE,
    id_journal_entry UUID REFERENCES journal_entries(id) ON DELETE CASCADE,
    id_invoice UUID REFERENCES invoices(id) ON DELETE CASCADE,
    filename VARCHAR(255) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    storage_path VARCHAR(500) NOT NULL,
    checksum_sha256 VARCHAR(64) NOT NULL,
    uploaded_by VARCHAR(100),
    uploaded_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP
);

CREATE INDEX idx_documents_transaction ON documents(id_transaction);
CREATE INDEX idx_documents_journal_entry ON documents(id_journal_entry);
CREATE INDEX idx_documents_invoice ON documents(id_invoice);
CREATE INDEX idx_documents_uploaded_at ON documents(uploaded_at);

-- ============================================
-- Telegram Receipt Import (Phase 2.2)
-- ============================================

-- Link Telegram users to app users
CREATE TABLE telegram_user_links (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_user UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    telegram_user_id BIGINT UNIQUE,
    telegram_username VARCHAR(100),
    telegram_first_name VARCHAR(255),
    verification_code VARCHAR(10),
    verification_expires_at TIMESTAMP,
    linked_at TIMESTAMP,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_telegram_links_user ON telegram_user_links(id_user);
CREATE INDEX idx_telegram_links_telegram_id ON telegram_user_links(telegram_user_id);
CREATE INDEX idx_telegram_links_active ON telegram_user_links(is_active);

-- Merchant pattern to template mapping
CREATE TABLE merchant_mappings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_pattern VARCHAR(255) NOT NULL,
    match_type VARCHAR(20) NOT NULL DEFAULT 'CONTAINS',
    id_template UUID NOT NULL REFERENCES journal_templates(id),
    default_description VARCHAR(500),
    match_count INTEGER NOT NULL DEFAULT 0,
    last_used_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by VARCHAR(100),

    CONSTRAINT chk_match_type CHECK (match_type IN ('EXACT', 'CONTAINS', 'REGEX'))
);

CREATE INDEX idx_merchant_mappings_pattern ON merchant_mappings(merchant_pattern);
CREATE INDEX idx_merchant_mappings_template ON merchant_mappings(id_template);

-- Draft transactions from receipt OCR
CREATE TABLE draft_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source VARCHAR(50) NOT NULL,
    source_reference VARCHAR(255),
    telegram_message_id BIGINT,
    telegram_chat_id BIGINT,

    -- Extracted data
    merchant_name VARCHAR(255),
    transaction_date DATE,
    amount DECIMAL(19, 2),
    currency VARCHAR(10) DEFAULT 'IDR',
    raw_ocr_text TEXT,
    receipt_type VARCHAR(50),

    -- Parsed fields confidence (0.0 - 1.0)
    merchant_confidence DECIMAL(3, 2),
    date_confidence DECIMAL(3, 2),
    amount_confidence DECIMAL(3, 2),
    overall_confidence DECIMAL(3, 2),

    -- Suggested mapping
    id_suggested_template UUID REFERENCES journal_templates(id),
    id_merchant_mapping UUID REFERENCES merchant_mappings(id),

    -- Attached document (receipt image)
    id_document UUID REFERENCES documents(id),

    -- Workflow status
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    rejection_reason VARCHAR(500),

    -- Created transaction (after approval)
    id_transaction UUID REFERENCES transactions(id),

    -- Audit
    created_by VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    processed_by VARCHAR(100),
    processed_at TIMESTAMP,

    CONSTRAINT chk_draft_source CHECK (source IN ('TELEGRAM', 'MANUAL', 'EMAIL')),
    CONSTRAINT chk_draft_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'AUTO_APPROVED'))
);

CREATE INDEX idx_draft_transactions_status ON draft_transactions(status);
CREATE INDEX idx_draft_transactions_user ON draft_transactions(created_by);
CREATE INDEX idx_draft_transactions_date ON draft_transactions(transaction_date);
CREATE INDEX idx_draft_transactions_telegram ON draft_transactions(telegram_chat_id, telegram_message_id);

-- ============================================
-- Tax Transaction Details (for Coretax export)
-- ============================================

CREATE TABLE tax_transaction_details (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_transaction UUID NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,

    -- e-Faktur fields (PPN)
    faktur_number VARCHAR(20),           -- Nomor Faktur Pajak
    faktur_date DATE,
    transaction_code VARCHAR(10),        -- 01, 02, 03, 04, 07, 08
    dpp DECIMAL(19, 2),                  -- Dasar Pengenaan Pajak
    ppn DECIMAL(19, 2),                  -- PPN amount (11%)
    ppnbm DECIMAL(19, 2) DEFAULT 0,      -- PPnBM if applicable

    -- e-Bupot fields (PPh)
    bupot_number VARCHAR(30),            -- Nomor Bukti Potong
    tax_object_code VARCHAR(20),         -- Kode Objek Pajak (e.g., 24-104-01)
    gross_amount DECIMAL(19, 2),         -- Jumlah Bruto
    tax_rate DECIMAL(5, 2),              -- Tarif (e.g., 2.00, 10.00)
    tax_amount DECIMAL(19, 2),           -- PPh dipotong

    -- Counterparty information (copied from client at time of transaction)
    counterparty_npwp VARCHAR(20),       -- NPWP lawan transaksi
    counterparty_nitku VARCHAR(22),
    counterparty_nik VARCHAR(16),
    counterparty_id_type VARCHAR(10),    -- TIN or NIK
    counterparty_name VARCHAR(255),
    counterparty_address TEXT,

    -- Tax type categorization
    tax_type VARCHAR(20),                -- PPN_KELUARAN, PPN_MASUKAN, PPH_23, PPH_42, etc.

    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_tax_type CHECK (tax_type IN ('PPN_KELUARAN', 'PPN_MASUKAN', 'PPH_21', 'PPH_23', 'PPH_42', 'PPH_25', 'PPH_29'))
);

CREATE INDEX idx_tax_details_transaction ON tax_transaction_details(id_transaction);
CREATE INDEX idx_tax_details_faktur ON tax_transaction_details(faktur_number);
CREATE INDEX idx_tax_details_bupot ON tax_transaction_details(bupot_number);
CREATE INDEX idx_tax_details_type ON tax_transaction_details(tax_type);
CREATE INDEX idx_tax_details_faktur_date ON tax_transaction_details(faktur_date);

-- ============================================
-- Fiscal Periods (Phase 2.7)
-- ============================================

CREATE TABLE fiscal_periods (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    year INTEGER NOT NULL,
    month INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    month_closed_at TIMESTAMP,
    month_closed_by VARCHAR(100),
    tax_filed_at TIMESTAMP,
    tax_filed_by VARCHAR(100),
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_fiscal_period_year_month UNIQUE (year, month),
    CONSTRAINT chk_fiscal_year CHECK (year BETWEEN 2000 AND 2100),
    CONSTRAINT chk_fiscal_month CHECK (month BETWEEN 1 AND 12),
    CONSTRAINT chk_fiscal_status CHECK (status IN ('OPEN', 'MONTH_CLOSED', 'TAX_FILED'))
);

CREATE INDEX idx_fiscal_periods_year ON fiscal_periods(year);
CREATE INDEX idx_fiscal_periods_status ON fiscal_periods(status);
CREATE INDEX idx_fiscal_periods_year_month ON fiscal_periods(year, month);

-- ============================================
-- Tax Deadlines (Phase 2.8)
-- ============================================

CREATE TABLE tax_deadlines (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    deadline_type VARCHAR(30) NOT NULL UNIQUE,
    due_day INTEGER NOT NULL,
    use_last_day_of_month BOOLEAN NOT NULL DEFAULT FALSE,
    description TEXT,
    reminder_days_before INTEGER NOT NULL DEFAULT 7,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_deadline_type CHECK (deadline_type IN (
        'PPH_21_PAYMENT', 'PPH_23_PAYMENT', 'PPH_42_PAYMENT', 'PPH_25_PAYMENT', 'PPN_PAYMENT',
        'SPT_PPH_21', 'SPT_PPH_23', 'SPT_PPN'
    )),
    CONSTRAINT chk_due_day CHECK (due_day BETWEEN 1 AND 31),
    CONSTRAINT chk_reminder_days CHECK (reminder_days_before BETWEEN 0 AND 30)
);

CREATE INDEX idx_tax_deadlines_type ON tax_deadlines(deadline_type);
CREATE INDEX idx_tax_deadlines_active ON tax_deadlines(active);

CREATE TABLE tax_deadline_completions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_tax_deadline UUID NOT NULL REFERENCES tax_deadlines(id) ON DELETE CASCADE,
    year INTEGER NOT NULL,
    month INTEGER NOT NULL,
    completed_date DATE NOT NULL,
    reference_number VARCHAR(100),
    notes TEXT,
    completed_by VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_tax_deadline_period UNIQUE (id_tax_deadline, year, month),
    CONSTRAINT chk_completion_year CHECK (year BETWEEN 2000 AND 2100),
    CONSTRAINT chk_completion_month CHECK (month BETWEEN 1 AND 12)
);

CREATE INDEX idx_tax_completions_deadline ON tax_deadline_completions(id_tax_deadline);
CREATE INDEX idx_tax_completions_period ON tax_deadline_completions(year, month);

-- ============================================
-- Employees (Phase 3.1)
-- ============================================

CREATE TABLE employees (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    phone VARCHAR(50),
    address TEXT,

    -- Link to user account for self-service access
    id_user UUID REFERENCES users(id),

    -- Tax identification
    npwp VARCHAR(20),                        -- 15-16 digits formatted: XX.XXX.XXX.X-XXX.XXX
    nik_ktp VARCHAR(16),                     -- 16 digit NIK KTP

    -- PTKP status for PPh 21 calculation
    ptkp_status VARCHAR(10) NOT NULL DEFAULT 'TK_0',

    -- Employment details
    hire_date DATE NOT NULL,
    resign_date DATE,
    employment_type VARCHAR(20) NOT NULL DEFAULT 'PERMANENT',
    employment_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    job_title VARCHAR(100),
    department VARCHAR(100),

    -- Bank account for salary payment
    bank_name VARCHAR(100),
    bank_account_number VARCHAR(50),
    bank_account_name VARCHAR(255),

    -- BPJS registration
    bpjs_kesehatan_number VARCHAR(20),
    bpjs_ketenagakerjaan_number VARCHAR(20),

    notes TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_ptkp_status CHECK (ptkp_status IN ('TK_0', 'TK_1', 'TK_2', 'TK_3', 'K_0', 'K_1', 'K_2', 'K_3', 'K_I_0', 'K_I_1', 'K_I_2', 'K_I_3')),
    CONSTRAINT chk_employment_type CHECK (employment_type IN ('PERMANENT', 'CONTRACT', 'PROBATION', 'FREELANCE')),
    CONSTRAINT chk_employment_status CHECK (employment_status IN ('ACTIVE', 'RESIGNED', 'TERMINATED', 'RETIRED'))
);

CREATE INDEX idx_employees_employee_id ON employees(employee_id);
CREATE INDEX idx_employees_name ON employees(name);
CREATE INDEX idx_employees_active ON employees(active);
CREATE INDEX idx_employees_status ON employees(employment_status);
CREATE INDEX idx_employees_npwp ON employees(npwp);
CREATE INDEX idx_employees_user ON employees(id_user);

-- ============================================
-- Salary Components (Phase 3.2)
-- ============================================

CREATE TABLE salary_components (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    component_type VARCHAR(30) NOT NULL,
    is_percentage BOOLEAN NOT NULL DEFAULT FALSE,
    default_rate DECIMAL(10, 4),          -- e.g., 0.0400 for 4%
    default_amount DECIMAL(15, 2),
    is_system BOOLEAN NOT NULL DEFAULT FALSE,
    display_order INTEGER NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    is_taxable BOOLEAN NOT NULL DEFAULT TRUE,
    bpjs_category VARCHAR(50),            -- For BPJS reporting: KESEHATAN, JHT, JKK, JKM, JP
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_component_type CHECK (component_type IN ('EARNING', 'DEDUCTION', 'COMPANY_CONTRIBUTION'))
);

CREATE INDEX idx_salary_components_code ON salary_components(code);
CREATE INDEX idx_salary_components_type ON salary_components(component_type);
CREATE INDEX idx_salary_components_active ON salary_components(active);
CREATE INDEX idx_salary_components_order ON salary_components(display_order);

-- Employee salary component assignments
CREATE TABLE employee_salary_components (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id UUID NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    salary_component_id UUID NOT NULL REFERENCES salary_components(id) ON DELETE CASCADE,
    rate DECIMAL(10, 4),                  -- Override rate for this employee
    amount DECIMAL(15, 2),                -- Override amount for this employee
    effective_date DATE NOT NULL,
    end_date DATE,
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_employee_component UNIQUE (employee_id, salary_component_id)
);

CREATE INDEX idx_esc_employee ON employee_salary_components(employee_id);
CREATE INDEX idx_esc_component ON employee_salary_components(salary_component_id);
CREATE INDEX idx_esc_effective_date ON employee_salary_components(effective_date);
CREATE INDEX idx_esc_end_date ON employee_salary_components(end_date);

-- ============================================
-- Payroll Processing (Phase 3.5)
-- ============================================

CREATE TABLE payroll_runs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payroll_period VARCHAR(7) NOT NULL,    -- Format: YYYY-MM (e.g., 2025-01)
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',

    -- Summary totals
    total_gross DECIMAL(19, 2) DEFAULT 0,
    total_deductions DECIMAL(19, 2) DEFAULT 0,
    total_net_pay DECIMAL(19, 2) DEFAULT 0,
    total_company_bpjs DECIMAL(19, 2) DEFAULT 0,
    total_pph21 DECIMAL(19, 2) DEFAULT 0,
    employee_count INTEGER DEFAULT 0,

    notes TEXT,

    -- Reference to transaction when posted
    id_transaction UUID REFERENCES transactions(id),
    posted_at TIMESTAMP,
    cancelled_at TIMESTAMP,
    cancel_reason VARCHAR(500),

    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_payroll_status CHECK (status IN ('DRAFT', 'CALCULATED', 'APPROVED', 'POSTED', 'CANCELLED')),
    CONSTRAINT uk_payroll_period UNIQUE (payroll_period)
);

CREATE INDEX idx_payroll_runs_period ON payroll_runs(payroll_period);
CREATE INDEX idx_payroll_runs_status ON payroll_runs(status);
CREATE INDEX idx_payroll_runs_transaction ON payroll_runs(id_transaction);

CREATE TABLE payroll_details (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_payroll_run UUID NOT NULL REFERENCES payroll_runs(id) ON DELETE CASCADE,
    id_employee UUID NOT NULL REFERENCES employees(id),

    -- Salary
    base_salary DECIMAL(19, 2) NOT NULL DEFAULT 0,
    gross_salary DECIMAL(19, 2) NOT NULL DEFAULT 0,

    -- BPJS Kesehatan
    bpjs_kes_company DECIMAL(15, 2) DEFAULT 0,
    bpjs_kes_employee DECIMAL(15, 2) DEFAULT 0,

    -- BPJS Ketenagakerjaan
    bpjs_jkk DECIMAL(15, 2) DEFAULT 0,
    bpjs_jkm DECIMAL(15, 2) DEFAULT 0,
    bpjs_jht_company DECIMAL(15, 2) DEFAULT 0,
    bpjs_jht_employee DECIMAL(15, 2) DEFAULT 0,
    bpjs_jp_company DECIMAL(15, 2) DEFAULT 0,
    bpjs_jp_employee DECIMAL(15, 2) DEFAULT 0,

    -- PPh 21
    pph21 DECIMAL(15, 2) DEFAULT 0,

    -- Totals
    total_deductions DECIMAL(19, 2) DEFAULT 0,
    net_pay DECIMAL(19, 2) DEFAULT 0,

    -- JKK Risk class used
    jkk_risk_class INTEGER DEFAULT 1,

    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_payroll_employee UNIQUE (id_payroll_run, id_employee)
);

CREATE INDEX idx_payroll_details_run ON payroll_details(id_payroll_run);
CREATE INDEX idx_payroll_details_employee ON payroll_details(id_employee);

-- ============================================
-- Asset Categories (Phase 4)
-- ============================================

CREATE TABLE asset_categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    depreciation_method VARCHAR(20) NOT NULL DEFAULT 'STRAIGHT_LINE',
    useful_life_months INTEGER NOT NULL DEFAULT 48,
    depreciation_rate DECIMAL(5, 2),
    id_asset_account UUID NOT NULL REFERENCES chart_of_accounts(id),
    id_accumulated_depreciation_account UUID NOT NULL REFERENCES chart_of_accounts(id),
    id_depreciation_expense_account UUID NOT NULL REFERENCES chart_of_accounts(id),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_depreciation_method CHECK (depreciation_method IN ('STRAIGHT_LINE', 'DECLINING_BALANCE'))
);

CREATE INDEX idx_asset_categories_code ON asset_categories(code);
CREATE INDEX idx_asset_categories_active ON asset_categories(active);

-- ============================================
-- Fixed Assets (Phase 4)
-- ============================================

CREATE TABLE fixed_assets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    asset_code VARCHAR(30) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(500),
    id_category UUID NOT NULL REFERENCES asset_categories(id),

    -- Purchase information
    purchase_date DATE NOT NULL,
    purchase_cost DECIMAL(19, 2) NOT NULL,
    supplier VARCHAR(100),
    invoice_number VARCHAR(100),

    -- Depreciation settings
    depreciation_method VARCHAR(20) NOT NULL DEFAULT 'STRAIGHT_LINE',
    useful_life_months INTEGER NOT NULL DEFAULT 48,
    residual_value DECIMAL(19, 2) NOT NULL DEFAULT 0,
    depreciation_rate DECIMAL(5, 2),
    depreciation_start_date DATE NOT NULL,

    -- Current values
    accumulated_depreciation DECIMAL(19, 2) NOT NULL DEFAULT 0,
    book_value DECIMAL(19, 2) NOT NULL,
    last_depreciation_date DATE,
    depreciation_periods_completed INTEGER NOT NULL DEFAULT 0,

    -- Status
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    -- Disposal information
    disposal_date DATE,
    disposal_type VARCHAR(20),
    disposal_proceeds DECIMAL(19, 2),
    gain_loss_on_disposal DECIMAL(19, 2),
    disposal_notes VARCHAR(500),

    -- Account references
    id_asset_account UUID NOT NULL REFERENCES chart_of_accounts(id),
    id_accumulated_depreciation_account UUID NOT NULL REFERENCES chart_of_accounts(id),
    id_depreciation_expense_account UUID NOT NULL REFERENCES chart_of_accounts(id),

    -- Transaction references
    id_purchase_transaction UUID REFERENCES transactions(id),
    id_disposal_transaction UUID REFERENCES transactions(id),

    -- Additional information
    location VARCHAR(100),
    serial_number VARCHAR(100),
    notes TEXT,

    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_asset_depreciation_method CHECK (depreciation_method IN ('STRAIGHT_LINE', 'DECLINING_BALANCE')),
    CONSTRAINT chk_asset_status CHECK (status IN ('ACTIVE', 'FULLY_DEPRECIATED', 'DISPOSED')),
    CONSTRAINT chk_disposal_type CHECK (disposal_type IS NULL OR disposal_type IN ('SOLD', 'WRITTEN_OFF', 'TRANSFERRED'))
);

CREATE INDEX idx_fixed_assets_code ON fixed_assets(asset_code);
CREATE INDEX idx_fixed_assets_category ON fixed_assets(id_category);
CREATE INDEX idx_fixed_assets_status ON fixed_assets(status);
CREATE INDEX idx_fixed_assets_purchase_date ON fixed_assets(purchase_date);

-- ============================================
-- Depreciation Entries (Phase 4)
-- ============================================

CREATE TABLE depreciation_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_fixed_asset UUID NOT NULL REFERENCES fixed_assets(id) ON DELETE CASCADE,
    period_number INTEGER NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    depreciation_amount DECIMAL(19, 2) NOT NULL,
    accumulated_depreciation DECIMAL(19, 2) NOT NULL,
    book_value DECIMAL(19, 2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    id_transaction UUID REFERENCES transactions(id),
    generated_at TIMESTAMP,
    posted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_depreciation_entry_status CHECK (status IN ('PENDING', 'POSTED', 'SKIPPED')),
    CONSTRAINT uk_asset_period UNIQUE (id_fixed_asset, period_number)
);

CREATE INDEX idx_depreciation_entries_asset ON depreciation_entries(id_fixed_asset);
CREATE INDEX idx_depreciation_entries_status ON depreciation_entries(status);
CREATE INDEX idx_depreciation_entries_period_end ON depreciation_entries(period_end);

-- ============================================
-- Product Categories (Phase 5 - Inventory)
-- ============================================

CREATE TABLE product_categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    id_parent UUID REFERENCES product_categories(id),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_product_categories_code ON product_categories(code);
CREATE INDEX idx_product_categories_parent ON product_categories(id_parent);
CREATE INDEX idx_product_categories_active ON product_categories(active);

-- ============================================
-- Products (Phase 5 - Inventory)
-- ============================================

CREATE TABLE products (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(500),
    unit VARCHAR(20) NOT NULL,
    id_category UUID REFERENCES product_categories(id),
    costing_method VARCHAR(20) NOT NULL DEFAULT 'WEIGHTED_AVERAGE',
    track_inventory BOOLEAN NOT NULL DEFAULT TRUE,
    minimum_stock DECIMAL(15, 4) NOT NULL DEFAULT 0,
    selling_price DECIMAL(19, 2),
    id_inventory_account UUID REFERENCES chart_of_accounts(id),
    id_cogs_account UUID REFERENCES chart_of_accounts(id),
    id_sales_account UUID REFERENCES chart_of_accounts(id),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_costing_method CHECK (costing_method IN ('FIFO', 'WEIGHTED_AVERAGE'))
);

CREATE INDEX idx_products_code ON products(code);
CREATE INDEX idx_products_name ON products(name);
CREATE INDEX idx_products_category ON products(id_category);
CREATE INDEX idx_products_active ON products(active);

-- ============================================
-- Inventory Balances (Phase 5 - Inventory)
-- ============================================

CREATE TABLE inventory_balances (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_product UUID NOT NULL UNIQUE REFERENCES products(id),
    quantity DECIMAL(15, 4) NOT NULL DEFAULT 0,
    total_cost DECIMAL(19, 2) NOT NULL DEFAULT 0,
    average_cost DECIMAL(19, 4) NOT NULL DEFAULT 0,
    last_transaction_date TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_inventory_balances_product ON inventory_balances(id_product);

-- ============================================
-- Inventory Transactions (Phase 5 - Inventory)
-- ============================================

CREATE TABLE inventory_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_product UUID NOT NULL REFERENCES products(id),
    transaction_type VARCHAR(20) NOT NULL,
    transaction_date DATE NOT NULL,
    quantity DECIMAL(15, 4) NOT NULL,
    unit_cost DECIMAL(19, 4) NOT NULL,
    total_cost DECIMAL(19, 2) NOT NULL,
    unit_price DECIMAL(19, 4),
    reference_number VARCHAR(100),
    notes VARCHAR(500),
    id_transaction UUID REFERENCES transactions(id),
    balance_after DECIMAL(15, 4) NOT NULL,
    total_cost_after DECIMAL(19, 2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by VARCHAR(100),

    CONSTRAINT chk_inv_transaction_type CHECK (transaction_type IN (
        'PURCHASE', 'SALE', 'ADJUSTMENT_IN', 'ADJUSTMENT_OUT',
        'PRODUCTION_IN', 'PRODUCTION_OUT', 'TRANSFER_IN', 'TRANSFER_OUT'
    ))
);

CREATE INDEX idx_inv_transactions_product ON inventory_transactions(id_product);
CREATE INDEX idx_inv_transactions_type ON inventory_transactions(transaction_type);
CREATE INDEX idx_inv_transactions_date ON inventory_transactions(transaction_date);
CREATE INDEX idx_inv_transactions_reference ON inventory_transactions(reference_number);

-- ============================================
-- Inventory FIFO Layers (Phase 5 - Inventory)
-- ============================================

CREATE TABLE inventory_fifo_layers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_product UUID NOT NULL REFERENCES products(id),
    id_inventory_transaction UUID NOT NULL REFERENCES inventory_transactions(id),
    layer_date DATE NOT NULL,
    original_quantity DECIMAL(15, 4) NOT NULL,
    remaining_quantity DECIMAL(15, 4) NOT NULL,
    unit_cost DECIMAL(19, 4) NOT NULL,
    fully_consumed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_fifo_layers_product ON inventory_fifo_layers(id_product);
CREATE INDEX idx_fifo_layers_date ON inventory_fifo_layers(layer_date);
CREATE INDEX idx_fifo_layers_consumed ON inventory_fifo_layers(fully_consumed);

-- ============================================
-- Bill of Materials (Phase 5.4 - Production)
-- ============================================

CREATE TABLE bill_of_materials (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_product UUID NOT NULL REFERENCES products(id),
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(500),
    output_quantity DECIMAL(15, 4) NOT NULL DEFAULT 1,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_bom_product ON bill_of_materials(id_product);
CREATE INDEX idx_bom_code ON bill_of_materials(code);
CREATE INDEX idx_bom_active ON bill_of_materials(active);

-- ============================================
-- Bill of Materials Lines (Phase 5.4 - Production)
-- ============================================

CREATE TABLE bill_of_material_lines (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_bill_of_material UUID NOT NULL REFERENCES bill_of_materials(id) ON DELETE CASCADE,
    id_component UUID NOT NULL REFERENCES products(id),
    quantity DECIMAL(15, 4) NOT NULL,
    notes VARCHAR(255),
    line_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_bom_lines_bom ON bill_of_material_lines(id_bill_of_material);
CREATE INDEX idx_bom_lines_component ON bill_of_material_lines(id_component);

-- ============================================
-- Production Orders (Phase 5.4 - Production)
-- ============================================

CREATE TABLE production_orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_number VARCHAR(50) NOT NULL UNIQUE,
    id_bill_of_material UUID NOT NULL REFERENCES bill_of_materials(id),
    quantity DECIMAL(15, 4) NOT NULL,
    order_date DATE NOT NULL,
    planned_completion_date DATE,
    actual_completion_date DATE,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    notes VARCHAR(500),
    total_component_cost DECIMAL(19, 2) NOT NULL DEFAULT 0,
    unit_cost DECIMAL(19, 4) NOT NULL DEFAULT 0,
    id_transaction UUID REFERENCES transactions(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by VARCHAR(100),
    completed_by VARCHAR(100),

    CONSTRAINT chk_production_order_status CHECK (status IN (
        'DRAFT', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'
    ))
);

CREATE INDEX idx_production_orders_number ON production_orders(order_number);
CREATE INDEX idx_production_orders_bom ON production_orders(id_bill_of_material);
CREATE INDEX idx_production_orders_status ON production_orders(status);
CREATE INDEX idx_production_orders_date ON production_orders(order_date);

-- ============================================
-- Security Audit Logs (Phase 6 - Security Hardening)
-- ============================================

CREATE TABLE security_audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type VARCHAR(50) NOT NULL,
    username VARCHAR(100),
    ip_address VARCHAR(50),
    user_agent VARCHAR(500),
    details TEXT,
    success BOOLEAN NOT NULL DEFAULT TRUE,
    timestamp TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_security_audit_event_type ON security_audit_logs(event_type);
CREATE INDEX idx_security_audit_username ON security_audit_logs(username);
CREATE INDEX idx_security_audit_timestamp ON security_audit_logs(timestamp);
