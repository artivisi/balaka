-- =============================================
-- V013: Evidence completeness, contract register, bank statement archive (issue #48)
--
-- 1. documents.evidence_type: what an attached file proves. Existing rows stay NULL (unclassified) and satisfy
--    no requirement until someone classifies them.
-- 2. evidence_requirements: required evidence by account (and its sub-accounts) or by template family.
--    No rows are seeded: which evidence a business needs is its own decision.
-- 3. contracts: register linked to a client or a vendor; invoices and transactions may reference one.
-- 4. bank_statement_archives: one ACTIVE statement file per bank account per month, with a retention date.
--    A replaced file stays as SUPERSEDED; rows are never removed before retention_until.
-- 5. Alert rule BANK_STATEMENT_MISSING: threshold is days after the month end.
-- 6. Scope evidence:read for existing device-flow tokens that hold analysis:read (same rule as V006).
-- =============================================

ALTER TABLE documents ADD COLUMN evidence_type VARCHAR(20);
ALTER TABLE documents ADD CONSTRAINT chk_document_evidence_type
    CHECK (evidence_type IS NULL OR evidence_type IN ('CONTRACT', 'INVOICE', 'PROOF_OF_PAYMENT', 'BUPOT', 'OTHER'));

CREATE TABLE evidence_requirements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_account UUID REFERENCES chart_of_accounts(id) ON DELETE CASCADE,
    id_journal_template UUID REFERENCES journal_templates(id) ON DELETE CASCADE,
    evidence_type VARCHAR(20) NOT NULL,
    min_amount DECIMAL(19, 2),
    notes VARCHAR(255),
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_evidence_requirement_target CHECK ((id_account IS NULL) <> (id_journal_template IS NULL)),
    CONSTRAINT chk_evidence_requirement_type
        CHECK (evidence_type IN ('CONTRACT', 'INVOICE', 'PROOF_OF_PAYMENT', 'BUPOT')),
    CONSTRAINT chk_evidence_requirement_min_amount CHECK (min_amount IS NULL OR min_amount > 0)
);
CREATE UNIQUE INDEX uk_evidence_requirement_account ON evidence_requirements(id_account, evidence_type)
    WHERE id_account IS NOT NULL;
CREATE UNIQUE INDEX uk_evidence_requirement_template ON evidence_requirements(id_journal_template, evidence_type)
    WHERE id_journal_template IS NOT NULL;

CREATE TABLE contracts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    contract_number VARCHAR(100) NOT NULL UNIQUE,
    title VARCHAR(255) NOT NULL,
    id_client UUID REFERENCES clients(id),
    id_vendor UUID REFERENCES vendors(id),
    start_date DATE NOT NULL,
    end_date DATE,
    contract_value DECIMAL(19, 2),
    id_document UUID REFERENCES documents(id),
    notes TEXT,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_contract_counterparty CHECK ((id_client IS NULL) <> (id_vendor IS NULL)),
    CONSTRAINT chk_contract_dates CHECK (end_date IS NULL OR end_date >= start_date),
    CONSTRAINT chk_contract_value CHECK (contract_value IS NULL OR contract_value >= 0)
);
CREATE INDEX idx_contracts_client ON contracts(id_client);
CREATE INDEX idx_contracts_vendor ON contracts(id_vendor);

ALTER TABLE invoices ADD COLUMN id_contract UUID REFERENCES contracts(id);
CREATE INDEX idx_invoices_contract ON invoices(id_contract);
ALTER TABLE transactions ADD COLUMN id_contract UUID REFERENCES contracts(id);
CREATE INDEX idx_transactions_contract ON transactions(id_contract);

CREATE TABLE bank_statement_archives (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_bank_account UUID NOT NULL REFERENCES company_bank_accounts(id),
    period_month DATE NOT NULL,
    id_document UUID NOT NULL REFERENCES documents(id),
    retention_until DATE NOT NULL,
    status VARCHAR(12) NOT NULL,
    superseded_at TIMESTAMP,
    uploaded_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_statement_archive_month CHECK (period_month = date_trunc('month', period_month)::date),
    CONSTRAINT chk_statement_archive_status CHECK (status IN ('ACTIVE', 'SUPERSEDED')),
    CONSTRAINT chk_statement_archive_superseded CHECK ((status = 'SUPERSEDED') = (superseded_at IS NOT NULL)),
    CONSTRAINT chk_statement_archive_retention CHECK (retention_until > period_month)
);
CREATE UNIQUE INDEX uk_statement_archive_active ON bank_statement_archives(id_bank_account, period_month)
    WHERE status = 'ACTIVE';
CREATE INDEX idx_statement_archive_document ON bank_statement_archives(id_document);

INSERT INTO alert_rules (id, row_version, alert_type, threshold, enabled, description)
VALUES (gen_random_uuid(), 0, 'BANK_STATEMENT_MISSING', 10.00, TRUE,
        'Peringatan jika rekening koran bulanan belum diarsipkan X hari setelah akhir bulan');

UPDATE device_tokens t
   SET scopes = t.scopes || ',evidence:read'
 WHERE t.scopes IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM api_clients c WHERE c.client_id = t.client_id)
   AND (',' || t.scopes || ',') LIKE '%,analysis:read,%'
   AND (',' || t.scopes || ',') NOT LIKE '%,evidence:read,%';
