-- V004: Transactions

-- Transaction sequence for auto-numbering
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

-- Initialize sequences for 2025
INSERT INTO transaction_sequences (id, sequence_type, prefix, year, last_number) VALUES
('d0000000-0000-0000-0000-000000000001', 'TRANSACTION', 'TRX', 2025, 0),
('d0000000-0000-0000-0000-000000000002', 'JOURNAL', 'JE', 2025, 0);

-- Transactions table
CREATE TABLE transactions (
    id UUID PRIMARY KEY,
    transaction_number VARCHAR(50) NOT NULL UNIQUE,
    transaction_date DATE NOT NULL,
    id_journal_template UUID NOT NULL REFERENCES journal_templates(id),
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

    CONSTRAINT chk_transaction_status CHECK (status IN ('DRAFT', 'POSTED', 'VOID')),
    CONSTRAINT chk_void_reason CHECK (void_reason IS NULL OR void_reason IN ('INPUT_ERROR', 'DUPLICATE', 'CANCELLED', 'OTHER'))
);

CREATE INDEX idx_trx_number ON transactions(transaction_number);
CREATE INDEX idx_trx_date ON transactions(transaction_date);
CREATE INDEX idx_trx_template ON transactions(id_journal_template);
CREATE INDEX idx_trx_status ON transactions(status);

-- Transaction account mappings (for overriding template default accounts)
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
