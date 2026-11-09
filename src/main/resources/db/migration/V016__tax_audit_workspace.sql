-- =============================================
-- V016: Tax audit workspace (issue #51)
--
-- 1. company_config.evidence_required_from: posting a transaction dated on or after this date is refused while an
--    evidence rule (#48) reports a missing type. NULL = not enforced. Existing rows stay NULL.
-- 2. Historical document archive: archive_import_batches, archive_documents. The file lives in documents; the
--    archive row holds year, type, counterparty, amount, source, download date, the extracted text and an optional
--    link to a transaction or one of its journal lines. search_vector is generated for full-text search.
-- 3. Inquiry case record (SP2DK, pemeriksaan): tax_inquiry_cases with items, entries and disclosures. Items, entries
--    and disclosures are append-only: a trigger refuses UPDATE and DELETE. Corrections are new entries.
-- =============================================

ALTER TABLE company_config ADD COLUMN evidence_required_from DATE;

-- ---------- document archive ----------

CREATE TABLE archive_import_batches (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    original_filename VARCHAR(255) NOT NULL,
    document_count    INTEGER      NOT NULL CHECK (document_count > 0),
    imported_by       VARCHAR(100) NOT NULL,
    imported_at       TIMESTAMP    NOT NULL
);

CREATE TABLE archive_documents (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_document       UUID          NOT NULL UNIQUE REFERENCES documents(id),
    id_import_batch   UUID          REFERENCES archive_import_batches(id),
    doc_year          INTEGER       NOT NULL CHECK (doc_year BETWEEN 1983 AND 2100),
    doc_type          VARCHAR(30)   NOT NULL CHECK (doc_type IN ('FAKTUR_PAJAK', 'BUPOT', 'SPT', 'BPE', 'BANK_STATEMENT',
                                        'INVOICE', 'RECEIPT', 'CONTRACT', 'TAX_LETTER', 'OTHER')),
    source            VARCHAR(20)   NOT NULL CHECK (source IN ('TAX_PORTAL', 'BANK', 'OTHER')),
    downloaded_on     DATE          NOT NULL,
    doc_number        VARCHAR(100),
    counterparty      VARCHAR(255),
    amount            NUMERIC(19, 2) CHECK (amount >= 0),
    description       TEXT,
    id_transaction    UUID          REFERENCES transactions(id),
    id_journal_entry  UUID          REFERENCES journal_entries(id),
    extracted_text    TEXT,
    text_status       VARCHAR(20)   NOT NULL CHECK (text_status IN ('EXTRACTED', 'NO_TEXT', 'NOT_SUPPORTED')),
    created_by        VARCHAR(100)  NOT NULL,
    created_at        TIMESTAMP     NOT NULL,
    updated_at        TIMESTAMP     NOT NULL,
    search_vector     TSVECTOR GENERATED ALWAYS AS (
        setweight(to_tsvector('simple'::regconfig, coalesce(doc_number, '') || ' ' || coalesce(counterparty, '')), 'A')
        || setweight(to_tsvector('simple'::regconfig, coalesce(description, '')), 'B')
        || setweight(to_tsvector('simple'::regconfig, coalesce(extracted_text, '')), 'C')
    ) STORED,
    CONSTRAINT chk_archive_line_needs_transaction CHECK (id_journal_entry IS NULL OR id_transaction IS NOT NULL)
);

CREATE INDEX idx_archive_documents_search ON archive_documents USING GIN (search_vector);
CREATE INDEX idx_archive_documents_year_type ON archive_documents (doc_year, doc_type);
CREATE INDEX idx_archive_documents_transaction ON archive_documents (id_transaction);

-- ---------- inquiry cases ----------

CREATE TABLE tax_inquiry_cases (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    inquiry_type        VARCHAR(20)  NOT NULL CHECK (inquiry_type IN ('SP2DK', 'PEMERIKSAAN', 'OTHER')),
    tax_year            INTEGER      NOT NULL,
    letter_number       VARCHAR(100) NOT NULL,
    letter_date         DATE         NOT NULL,
    received_date       DATE         NOT NULL,
    tax_office          VARCHAR(200) NOT NULL,
    subject             TEXT         NOT NULL,
    deadline            DATE         NOT NULL,
    status              VARCHAR(20)  NOT NULL CHECK (status IN ('OPEN', 'RESPONDED', 'CLOSED')),
    decision            TEXT,
    closed_on           DATE,
    id_letter_document  UUID         NOT NULL REFERENCES documents(id),
    id_tax_filing       UUID         REFERENCES tax_filings(id),
    id_tax_document     UUID         REFERENCES tax_documents(id),
    created_by          VARCHAR(100) NOT NULL,
    created_at          TIMESTAMP    NOT NULL,
    updated_at          TIMESTAMP    NOT NULL,
    CONSTRAINT uk_tax_inquiry_letter UNIQUE (inquiry_type, letter_number),
    CONSTRAINT chk_tax_inquiry_closed CHECK ((status = 'CLOSED') = (decision IS NOT NULL AND closed_on IS NOT NULL))
);

CREATE INDEX idx_tax_inquiry_cases_year ON tax_inquiry_cases (tax_year);

CREATE TABLE tax_inquiry_items (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_case         UUID         NOT NULL REFERENCES tax_inquiry_cases(id),
    item_no         INTEGER      NOT NULL CHECK (item_no > 0),
    description     TEXT         NOT NULL CHECK (LENGTH(TRIM(description)) > 0),
    self_test_line  VARCHAR(40),
    period_month    INTEGER CHECK (period_month BETWEEN 0 AND 12),
    created_by      VARCHAR(100) NOT NULL,
    created_at      TIMESTAMP    NOT NULL,
    CONSTRAINT uk_tax_inquiry_item UNIQUE (id_case, item_no),
    CONSTRAINT chk_tax_inquiry_item_line CHECK ((self_test_line IS NULL) = (period_month IS NULL))
);

CREATE TABLE tax_inquiry_entries (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_case       UUID         NOT NULL REFERENCES tax_inquiry_cases(id),
    entry_type    VARCHAR(20)  NOT NULL CHECK (entry_type IN ('REPLY', 'MEETING', 'NOTE', 'DEADLINE_CHANGE',
                                   'STATUS_CHANGE', 'DECISION', 'DISCLOSURE', 'CORRECTION')),
    entry_date    DATE         NOT NULL,
    content       TEXT         NOT NULL CHECK (LENGTH(TRIM(content)) > 0),
    id_item       UUID         REFERENCES tax_inquiry_items(id),
    id_corrects   UUID         REFERENCES tax_inquiry_entries(id),
    id_document   UUID         REFERENCES documents(id),
    new_status    VARCHAR(20),
    new_deadline  DATE,
    created_by    VARCHAR(100) NOT NULL,
    created_at    TIMESTAMP    NOT NULL,
    CONSTRAINT chk_tax_inquiry_correction CHECK ((entry_type = 'CORRECTION') = (id_corrects IS NOT NULL)),
    CONSTRAINT chk_tax_inquiry_status CHECK ((entry_type = 'STATUS_CHANGE') = (new_status IS NOT NULL)),
    CONSTRAINT chk_tax_inquiry_deadline CHECK ((entry_type = 'DEADLINE_CHANGE') = (new_deadline IS NOT NULL))
);

CREATE INDEX idx_tax_inquiry_entries_case ON tax_inquiry_entries (id_case, created_at);

CREATE TABLE tax_inquiry_disclosures (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_case              UUID          NOT NULL REFERENCES tax_inquiry_cases(id),
    id_entry             UUID          NOT NULL REFERENCES tax_inquiry_entries(id),
    id_item              UUID          NOT NULL REFERENCES tax_inquiry_items(id),
    id_document          UUID          NOT NULL REFERENCES documents(id),
    id_archive_document  UUID          REFERENCES archive_documents(id),
    id_transaction       UUID          REFERENCES transactions(id),
    disclosed_on         DATE          NOT NULL,
    filename             VARCHAR(255)  NOT NULL,
    checksum_sha256      VARCHAR(64)   NOT NULL,
    file_size            BIGINT        NOT NULL,
    created_by           VARCHAR(100)  NOT NULL,
    created_at           TIMESTAMP     NOT NULL,
    CONSTRAINT uk_tax_inquiry_disclosure UNIQUE (id_entry, id_document)
);

CREATE INDEX idx_tax_inquiry_disclosures_case ON tax_inquiry_disclosures (id_case);
CREATE INDEX idx_tax_inquiry_disclosures_document ON tax_inquiry_disclosures (id_document);

CREATE FUNCTION tax_inquiry_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Catatan pemeriksaan pajak tidak dapat diubah atau dihapus (%): buat entri koreksi', TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_tax_inquiry_items_append_only BEFORE UPDATE OR DELETE ON tax_inquiry_items
    FOR EACH ROW EXECUTE FUNCTION tax_inquiry_append_only();
CREATE TRIGGER trg_tax_inquiry_entries_append_only BEFORE UPDATE OR DELETE ON tax_inquiry_entries
    FOR EACH ROW EXECUTE FUNCTION tax_inquiry_append_only();
CREATE TRIGGER trg_tax_inquiry_disclosures_append_only BEFORE UPDATE OR DELETE ON tax_inquiry_disclosures
    FOR EACH ROW EXECUTE FUNCTION tax_inquiry_append_only();
