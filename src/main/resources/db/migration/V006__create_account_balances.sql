-- V006: Account Balances (Materialized)

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
