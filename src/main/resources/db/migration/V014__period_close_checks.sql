-- =============================================
-- V014: Period close checks (issue #50)
--
-- 1. chart_of_accounts.close_check_role: TAX_CREDIT (asset), CLEARING and SHAREHOLDER_CURRENT (asset or
--    liability), non-header accounts only. NULL = no role.
-- 2. 1.1.26 "Kredit Pajak PPh 23" (the account the seed packs and V010 use) becomes TAX_CREDIT.
-- 3. 2.1.50 "Rekening Koran Pemegang Saham/Direksi" (SHAREHOLDER_CURRENT) is inserted under 2.1 on a database
--    with a chart of accounts; an empty chart gets it from the seed pack. 2.1.50 used by another account fails
--    the migration: rename or move that account first.
-- 4. Alert rule LIABILITY_UNMOVED: threshold is the number of months without a posted line. The close checklist
--    reads the same threshold.
-- 5. close-checks:read for device-flow tokens holding analysis:read (same rule as V013).
-- =============================================

ALTER TABLE chart_of_accounts ADD COLUMN close_check_role VARCHAR(30);
ALTER TABLE chart_of_accounts ADD CONSTRAINT chk_coa_close_check_role CHECK (
    close_check_role IS NULL
    OR (is_header = FALSE AND (
        (close_check_role = 'TAX_CREDIT' AND account_type = 'ASSET')
        OR (close_check_role IN ('CLEARING', 'SHAREHOLDER_CURRENT') AND account_type IN ('ASSET', 'LIABILITY'))
    ))
);

UPDATE chart_of_accounts
   SET close_check_role = 'TAX_CREDIT'
 WHERE account_code = '1.1.26'
   AND account_name = 'Kredit Pajak PPh 23'
   AND account_type = 'ASSET'
   AND is_header = FALSE;

DO $$
DECLARE
    taken TEXT;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM chart_of_accounts) THEN
        RETURN;
    END IF;

    SELECT account_name INTO taken FROM chart_of_accounts WHERE account_code = '2.1.50';
    IF taken IS NOT NULL AND taken <> 'Rekening Koran Pemegang Saham/Direksi' THEN
        RAISE EXCEPTION 'V014: account code 2.1.50 is used by "%"; it is reserved for Rekening Koran Pemegang Saham/Direksi', taken;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM chart_of_accounts WHERE account_code = '2.1' AND account_type = 'LIABILITY') THEN
        RAISE EXCEPTION 'V014: parent account 2.1 (LIABILITY) for 2.1.50 is missing';
    END IF;

    INSERT INTO chart_of_accounts (id, account_code, account_name, account_type, normal_balance,
                                   id_parent, level, is_header, active, is_permanent, close_check_role)
    SELECT gen_random_uuid(), '2.1.50', 'Rekening Koran Pemegang Saham/Direksi', 'LIABILITY', 'CREDIT',
           p.id, p.level + 1, FALSE, TRUE, TRUE, 'SHAREHOLDER_CURRENT'
      FROM chart_of_accounts p
     WHERE p.account_code = '2.1'
    ON CONFLICT (account_code) DO UPDATE SET close_check_role = 'SHAREHOLDER_CURRENT';
END $$;

INSERT INTO alert_rules (id, row_version, alert_type, threshold, enabled, description)
VALUES (gen_random_uuid(), 0, 'LIABILITY_UNMOVED', 6.00, TRUE,
        'Peringatan jika akun liabilitas bersaldo tidak bergerak selama X bulan');

UPDATE device_tokens t
   SET scopes = t.scopes || ',close-checks:read'
 WHERE t.scopes IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM api_clients c WHERE c.client_id = t.client_id)
   AND (',' || t.scopes || ',') LIKE '%,analysis:read,%'
   AND (',' || t.scopes || ',') NOT LIKE '%,close-checks:read,%';
