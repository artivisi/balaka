-- V018: explicit cash/bank flag and its provenance
--
-- Cash reporting must never infer cash from an account-code prefix at read time.
-- The selector this replaces (active, non-header ASSET, account_code LIKE '1.1.0%')
-- also matched receivables, prepaid expenses and input VAT, so a receivable
-- overstated cash in the cash flow statement, the dashboard and SPT 8A.
--
-- The backfill below keeps that old predicate on purpose: every account an existing
-- database already counted as cash stays cash after the migration, so no report
-- changes silently. Deciding which of those accounts are genuinely not cash is the
-- Finance Controller's job, through the chart-of-accounts form, and the provenance
-- column records that a human made the call.

ALTER TABLE chart_of_accounts
    ADD COLUMN is_cash_bank BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE chart_of_accounts
    ADD COLUMN cash_bank_source VARCHAR(20);

COMMENT ON COLUMN chart_of_accounts.is_cash_bank IS
    'True only for accounts holding cash on hand or bank balances. Receivables, prepaid expenses and input VAT must stay false.';

COMMENT ON COLUMN chart_of_accounts.cash_bank_source IS
    'Provenance of is_cash_bank: EXPLICIT, BANK_LINK or MIGRATED. NULL when is_cash_bank is false.';

-- One-off data migration for rows that already exist. Seed packs declare the flag
-- in their own chart_of_accounts CSV; the application never infers it again.
UPDATE chart_of_accounts
SET is_cash_bank = TRUE
WHERE active = TRUE
  AND is_header = FALSE
  AND account_type = 'ASSET'
  AND (account_code LIKE '1.1.0%'
       OR EXISTS (
           SELECT 1
           FROM company_bank_accounts b
           WHERE b.id_account = chart_of_accounts.id
       ));

-- Everything flagged above is durable: unlinking a bank account must not clear it.
UPDATE chart_of_accounts
SET cash_bank_source = 'MIGRATED'
WHERE is_cash_bank = TRUE;

ALTER TABLE chart_of_accounts
    ADD CONSTRAINT chk_coa_cash_bank_asset
    CHECK (is_cash_bank = FALSE OR (account_type = 'ASSET' AND is_header = FALSE));

ALTER TABLE chart_of_accounts
    ADD CONSTRAINT chk_coa_cash_bank_source
    CHECK (cash_bank_source IS NULL
           OR (is_cash_bank = TRUE
               AND cash_bank_source IN ('EXPLICIT', 'BANK_LINK', 'MIGRATED')));

CREATE INDEX idx_coa_cash_bank ON chart_of_accounts(is_cash_bank)
    WHERE is_cash_bank = TRUE;

CREATE INDEX idx_coa_cash_bank_source ON chart_of_accounts(cash_bank_source);
