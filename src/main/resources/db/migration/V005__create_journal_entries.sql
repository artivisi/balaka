-- V005: Journal Entries

CREATE TABLE journal_entries (
    id UUID PRIMARY KEY,
    journal_number VARCHAR(50) NOT NULL UNIQUE,
    journal_date DATE NOT NULL,
    id_transaction UUID REFERENCES transactions(id),
    id_account UUID NOT NULL REFERENCES chart_of_accounts(id),
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

    CONSTRAINT chk_debit_or_credit CHECK (
        (debit_amount > 0 AND credit_amount = 0) OR
        (debit_amount = 0 AND credit_amount > 0)
    )
);

CREATE INDEX idx_je_number ON journal_entries(journal_number);
CREATE INDEX idx_je_date ON journal_entries(journal_date);
CREATE INDEX idx_je_transaction ON journal_entries(id_transaction);
CREATE INDEX idx_je_account ON journal_entries(id_account);
CREATE INDEX idx_je_account_date ON journal_entries(id_account, journal_date);
