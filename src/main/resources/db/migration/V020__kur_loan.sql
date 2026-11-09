-- =============================================
-- V009: KUR loan master, repayment schedule, payments, and reserve tracker (FAS-23)
--
-- The KUR facility is financed by a bank. Until the signed agreement and the
-- lender's repayment schedule are attached and reviewed by the Finance
-- Controller, every row created here is provenance 'FOUNDER_ESTIMATE' and the
-- schedule rows are source kind 'ESTIMATE'. The schema never upgrades a loan to
-- 'VERIFIED' by itself.
--
-- Money columns are DECIMAL(19,2): no binary floating point.
-- Posted/payment rows are never deleted; corrections use the reversal columns.
-- Import idempotency is enforced by source-reference uniqueness constraints.
-- =============================================

CREATE TABLE kur_loans (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lender VARCHAR(100) NOT NULL,
    product VARCHAR(50) NOT NULL DEFAULT 'KUR',
    -- Protected lender reference. Stored for reconciliation; never shown in full
    -- by the dashboard read layer.
    -- AES-GCM ciphertext expands the value; 255 leaves room for a 100-char reference.
    loan_reference VARCHAR(255),
    borrower_name VARCHAR(200) NOT NULL,
    borrower_legal_owner VARCHAR(200),
    principal_original DECIMAL(19, 2) NOT NULL,
    disbursement_date DATE,
    term_months INTEGER NOT NULL,
    installment_contractual DECIMAL(19, 2),
    installment_frequency VARCHAR(20) NOT NULL DEFAULT 'MONTHLY',
    installment_count INTEGER NOT NULL,
    first_due_date DATE,
    monthly_due_day INTEGER,
    maturity_date DATE,
    fees DECIMAL(19, 2),
    agreement_document_id UUID,
    schedule_document_id UUID,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    provenance VARCHAR(30) NOT NULL DEFAULT 'FOUNDER_ESTIMATE',
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    CONSTRAINT chk_kur_loan_principal CHECK (principal_original > 0),
    CONSTRAINT chk_kur_loan_term CHECK (term_months > 0),
    CONSTRAINT chk_kur_loan_count CHECK (installment_count > 0),
    CONSTRAINT chk_kur_loan_fees CHECK (fees IS NULL OR fees >= 0),
    CONSTRAINT chk_kur_loan_due_day CHECK (monthly_due_day IS NULL OR monthly_due_day BETWEEN 1 AND 31),
    CONSTRAINT chk_kur_loan_status CHECK (status IN ('ACTIVE', 'CLOSED', 'DEFAULTED')),
    CONSTRAINT chk_kur_loan_provenance CHECK (provenance IN ('FOUNDER_ESTIMATE', 'VERIFIED', 'BANK_CONFIRMED'))
);

CREATE INDEX idx_kur_loans_status ON kur_loans(status);
CREATE INDEX idx_kur_loans_lender ON kur_loans(lender);

CREATE TABLE kur_installments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    loan_id UUID NOT NULL REFERENCES kur_loans(id),
    installment_number INTEGER NOT NULL,
    due_date DATE NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    principal DECIMAL(19, 2),
    interest DECIMAL(19, 2),
    fee DECIMAL(19, 2),
    paid_amount DECIMAL(19, 2) NOT NULL DEFAULT 0,
    paid_date DATE,
    status VARCHAR(20) NOT NULL DEFAULT 'UNPAID',
    bank_ref VARCHAR(100),
    evidence_ref VARCHAR(200),
    source_kind VARCHAR(30) NOT NULL DEFAULT 'ESTIMATE',
    source_ref VARCHAR(200) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_kur_installment_number CHECK (installment_number > 0),
    CONSTRAINT chk_kur_installment_amount CHECK (amount >= 0),
    CONSTRAINT chk_kur_installment_paid CHECK (paid_amount >= 0),
    CONSTRAINT chk_kur_installment_components CHECK (
        (principal IS NULL OR principal >= 0)
        AND (interest IS NULL OR interest >= 0)
        AND (fee IS NULL OR fee >= 0)
    ),
    CONSTRAINT chk_kur_installment_status CHECK (status IN ('UNPAID', 'PARTIAL', 'PAID', 'OVERPAID')),
    CONSTRAINT chk_kur_installment_source CHECK (source_kind IN ('ESTIMATE', 'LENDER_SCHEDULE'))
);

-- One schedule row per number, and one row per source reference: a re-import of
-- the same lender row can never duplicate an installment.
CREATE UNIQUE INDEX uk_kur_installment_number ON kur_installments(loan_id, installment_number);
CREATE UNIQUE INDEX uk_kur_installment_source ON kur_installments(loan_id, source_kind, source_ref);
CREATE INDEX idx_kur_installments_due ON kur_installments(loan_id, due_date);
CREATE INDEX idx_kur_installments_status ON kur_installments(loan_id, status);

CREATE TABLE kur_payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    installment_id UUID NOT NULL REFERENCES kur_installments(id),
    payment_date DATE NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    bank_ref VARCHAR(100),
    -- Evidence is mandatory: an installment can only be marked paid with a
    -- reconcilable reference (transfer proof, bank statement line, receipt).
    evidence_ref VARCHAR(200) NOT NULL,
    source_ref VARCHAR(200) NOT NULL,
    reversed_at TIMESTAMP,
    reversal_reason VARCHAR(300),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_kur_payment_amount CHECK (amount > 0),
    CONSTRAINT chk_kur_payment_evidence CHECK (length(trim(evidence_ref)) > 0),
    CONSTRAINT chk_kur_payment_source CHECK (length(trim(source_ref)) > 0),
    CONSTRAINT chk_kur_payment_reversal CHECK (
        (reversed_at IS NULL AND reversal_reason IS NULL)
        OR (reversed_at IS NOT NULL AND reversal_reason IS NOT NULL)
    )
);

-- Idempotent payment import: the same source reference on the same installment
-- is applied once. Re-sending it returns the existing row.
CREATE UNIQUE INDEX uk_kur_payment_source ON kur_payments(installment_id, source_ref);
CREATE INDEX idx_kur_payments_date ON kur_payments(payment_date);

CREATE TABLE kur_reserve_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    loan_id UUID NOT NULL REFERENCES kur_loans(id),
    entry_date DATE NOT NULL,
    kind VARCHAR(30) NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    description VARCHAR(300),
    -- An earmark is internal only; it never moves cash and never marks an
    -- installment paid. Cash held in a separate reserve account is tracked with
    -- its own kind so it is not counted twice.
    source_ref VARCHAR(200) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_kur_reserve_amount CHECK (amount > 0),
    CONSTRAINT chk_kur_reserve_kind CHECK (kind IN (
        'EARMARK', 'RELEASE', 'SEPARATE_ACCOUNT', 'SEPARATE_ACCOUNT_RELEASE')),
    CONSTRAINT chk_kur_reserve_source CHECK (length(trim(source_ref)) > 0)
);

CREATE UNIQUE INDEX uk_kur_reserve_source ON kur_reserve_entries(loan_id, source_ref);
CREATE INDEX idx_kur_reserve_date ON kur_reserve_entries(loan_id, entry_date);
