-- V008: Provenance for the explicit cash/bank flag (FAS-87)
--
-- FAS-77 introduced chart_of_accounts.is_cash_bank. That flag is authoritative,
-- but runtime maintenance needs to know WHY an account is flagged so an unlink
-- from company_bank_accounts does not clear an explicit Finance Controller
-- decision, while an account that was only cash because of a bank link is not
-- left stranded after the link is removed.
--
--   EXPLICIT  - set by the Finance Controller through the chart-of-accounts form
--   BANK_LINK - set automatically when a company bank account points at the GL account
--   MIGRATED  - backfilled by V007 or imported from a legacy/canonical seed

ALTER TABLE chart_of_accounts
    ADD COLUMN cash_bank_source VARCHAR(20);

COMMENT ON COLUMN chart_of_accounts.cash_bank_source IS
    'Provenance of is_cash_bank: EXPLICIT, BANK_LINK or MIGRATED (FAS-87). NULL when is_cash_bank is false.';

-- Every account flagged at migration time is durable: V007 backfilled canonical
-- codes and accounts already linked from company_bank_accounts. Unlinking a bank
-- account must not remove those flags.
UPDATE chart_of_accounts
SET cash_bank_source = 'MIGRATED'
WHERE is_cash_bank = TRUE
  AND cash_bank_source IS NULL;

ALTER TABLE chart_of_accounts
    ADD CONSTRAINT chk_coa_cash_bank_source
    CHECK (cash_bank_source IS NULL
           OR (is_cash_bank = TRUE
               AND cash_bank_source IN ('EXPLICIT', 'BANK_LINK', 'MIGRATED')));

CREATE INDEX idx_coa_cash_bank_source ON chart_of_accounts(cash_bank_source);
