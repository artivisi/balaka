-- V007: Explicit cash/bank account universe (FAS-77)
--
-- Cash reporting must never infer cash from an account-code prefix. The previous
-- selector (account_code LIKE '1.1.0%') also matched receivables, prepaid expenses
-- and input VAT, so a nonzero receivable would overstate cash. Cash/bank membership
-- is now an explicit attribute of the account.

ALTER TABLE chart_of_accounts
    ADD COLUMN is_cash_bank BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN chart_of_accounts.is_cash_bank IS
    'True only for accounts holding cash on hand or bank balances. Receivables, prepaid expenses and input VAT must stay false (FAS-77).';

-- Deterministic backfill for the canonical Balaka chart of accounts:
--   1.1.01 Kas, 1.1.02 Bank BCA, 1.1.03 Bank BNI.
-- This is a closed code list, not a prefix rule. Any other cash/bank account must
-- be marked explicitly (the FASTERNET COA code mapping is owned by the Finance
-- Controller, contract open item 1).
UPDATE chart_of_accounts
SET is_cash_bank = TRUE
WHERE account_code IN ('1.1.01', '1.1.02', '1.1.03')
  AND account_type = 'ASSET'
  AND is_header = FALSE;

-- Bank accounts linked in company_bank_accounts are cash by definition.
UPDATE chart_of_accounts c
SET is_cash_bank = TRUE
WHERE c.account_type = 'ASSET'
  AND c.is_header = FALSE
  AND EXISTS (
      SELECT 1
      FROM company_bank_accounts b
      WHERE b.id_account = c.id
  );

ALTER TABLE chart_of_accounts
    ADD CONSTRAINT chk_coa_cash_bank_asset
    CHECK (is_cash_bank = FALSE OR (account_type = 'ASSET' AND is_header = FALSE));

CREATE INDEX idx_coa_cash_bank ON chart_of_accounts(is_cash_bank)
    WHERE is_cash_bank = TRUE;
