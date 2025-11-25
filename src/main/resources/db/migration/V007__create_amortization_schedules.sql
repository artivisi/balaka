-- V007: Amortization Schedules

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

CREATE INDEX idx_amort_schedules_status ON amortization_schedules(status);
CREATE INDEX idx_amort_schedules_type ON amortization_schedules(schedule_type);
CREATE INDEX idx_amort_schedules_code ON amortization_schedules(code);
CREATE INDEX idx_amort_entries_schedule ON amortization_entries(id_schedule);
CREATE INDEX idx_amort_entries_status ON amortization_entries(status);
CREATE INDEX idx_amort_entries_period_end ON amortization_entries(period_end);
