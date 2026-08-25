-- =============================================
-- V005: Tax Filing Register (issue #35)
--
-- Period-linked tax records: SPT Masa/Tahunan, BPE, STP, teguran, SP2DK, SKPLB.
-- Transaction-linked tax documents (faktur pajak, bukti potong) stay on
-- documents.id_transaction; those have a journal entry to hang on, these do not.
--
-- Ships as its own migration rather than being folded into V003, which was
-- already applied to production. Editing an applied migration fails Flyway's
-- checksum validation on every existing environment and forces the schema to be
-- created by hand there.
-- =============================================

CREATE TABLE tax_filings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tax_type VARCHAR(20) NOT NULL,
    period_year INTEGER NOT NULL,
    period_month INTEGER,
    pembetulan_no INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL,
    filed_date DATE,
    bpe_number VARCHAR(100),
    bpe_date DATE,
    kurang_bayar DECIMAL(15, 2),
    lebih_bayar DECIMAL(15, 2),
    nihil BOOLEAN NOT NULL DEFAULT FALSE,
    billing_code VARCHAR(50),
    ntpn VARCHAR(50),
    paid_date DATE,
    id_payment_transaction UUID REFERENCES transactions(id) ON DELETE SET NULL,
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_tax_filing_type CHECK (tax_type IN ('PPN', 'PPH21', 'PPH23_UNIFIKASI', 'PPH_BADAN')),
    CONSTRAINT chk_tax_filing_status CHECK (status IN ('NOT_FILED', 'FILED', 'LATE', 'CORRECTED')),
    CONSTRAINT chk_tax_filing_year CHECK (period_year BETWEEN 2000 AND 2100),
    CONSTRAINT chk_tax_filing_month CHECK (period_month IS NULL OR period_month BETWEEN 1 AND 12),
    CONSTRAINT chk_tax_filing_pembetulan CHECK (pembetulan_no >= 0),
    CONSTRAINT chk_tax_filing_kurang_bayar CHECK (kurang_bayar IS NULL OR kurang_bayar >= 0),
    CONSTRAINT chk_tax_filing_lebih_bayar CHECK (lebih_bayar IS NULL OR lebih_bayar >= 0),
    -- PPh Badan is filed per year; the masa types always carry a month
    CONSTRAINT chk_tax_filing_period_shape CHECK (
        (tax_type = 'PPH_BADAN' AND period_month IS NULL)
        OR (tax_type <> 'PPH_BADAN' AND period_month IS NOT NULL)
    )
);

-- COALESCE folds the annual NULL month into 0; a plain NULL column would compare
-- distinct and let two PPh Badan rows share a year
CREATE UNIQUE INDEX uk_tax_filing_period
    ON tax_filings(tax_type, period_year, COALESCE(period_month, 0), pembetulan_no);
CREATE INDEX idx_tax_filings_year ON tax_filings(period_year);
CREATE INDEX idx_tax_filings_status ON tax_filings(status);
CREATE INDEX idx_tax_filings_payment ON tax_filings(id_payment_transaction);

-- Coretax paperwork attached to a filing. The file itself lives in `documents`
-- (same encryption, checksum, and 10-year retention as transaction attachments);
-- this table adds the tax metadata and the STP -> teguran -> billing chain.
CREATE TABLE tax_documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_tax_filing UUID NOT NULL REFERENCES tax_filings(id) ON DELETE CASCADE,
    id_document UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    doc_type VARCHAR(20) NOT NULL,
    doc_number VARCHAR(100),
    doc_date DATE,
    due_date DATE,
    amount DECIMAL(15, 2),
    reference_number VARCHAR(100),
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_tax_document_type CHECK (doc_type IN (
        'SPT', 'BPE', 'STP', 'TEGURAN', 'SP2DK', 'SKPLB', 'SKPKPP', 'KODE_BILLING', 'BPN', 'SURAT')),
    CONSTRAINT chk_tax_document_amount CHECK (amount IS NULL OR amount >= 0)
);

CREATE INDEX idx_tax_documents_filing ON tax_documents(id_tax_filing);
CREATE INDEX idx_tax_documents_document ON tax_documents(id_document);
CREATE INDEX idx_tax_documents_type ON tax_documents(doc_type);
CREATE INDEX idx_tax_documents_number ON tax_documents(doc_number);
