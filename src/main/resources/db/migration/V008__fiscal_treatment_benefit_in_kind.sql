-- =============================================
-- V008: Deductibility flag, reviewable fiscal adjustment drafts, benefit-in-kind (issue #46)
--
-- 1. chart_of_accounts.fiscal_treatment: DEDUCTIBLE | NON_DEDUCTIBLE | CONDITIONAL, on EXPENSE
--    accounts. NULL = not classified yet: existing accounts stay NULL because no rule can derive
--    their treatment; fiscal adjustment generation reports them instead of assuming one.
-- 2. fiscal_adjustments.status + source: generated corrections start as DRAFT and only CONFIRMED
--    rows reach the fiscal reconciliation. Existing rows were entered by hand and already count.
-- 3. benefit_in_kind: recipient and value per transaction. Rows linked to a payroll detail have
--    entered that employee's PPh 21 base (payroll_details.benefit_in_kind).
-- 4. Seed accounts with codes shared by all seed packs. On a database that already has a chart of
--    accounts the rows are added under the existing parents; a missing parent fails the migration.
--    On an empty chart nothing is inserted and the seed pack supplies the accounts.
-- =============================================

ALTER TABLE chart_of_accounts ADD COLUMN fiscal_treatment VARCHAR(20);
ALTER TABLE chart_of_accounts ADD CONSTRAINT chk_coa_fiscal_treatment
    CHECK (fiscal_treatment IN ('DEDUCTIBLE', 'NON_DEDUCTIBLE', 'CONDITIONAL'));
ALTER TABLE chart_of_accounts ADD CONSTRAINT chk_coa_fiscal_treatment_expense
    CHECK (fiscal_treatment IS NULL OR account_type = 'EXPENSE');

ALTER TABLE fiscal_adjustments ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED';
ALTER TABLE fiscal_adjustments ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'MANUAL';
ALTER TABLE fiscal_adjustments ALTER COLUMN status DROP DEFAULT;
ALTER TABLE fiscal_adjustments ALTER COLUMN source DROP DEFAULT;
ALTER TABLE fiscal_adjustments ADD CONSTRAINT chk_fiscal_adj_status CHECK (status IN ('DRAFT', 'CONFIRMED'));
ALTER TABLE fiscal_adjustments ADD CONSTRAINT chk_fiscal_adj_source CHECK (source IN ('MANUAL', 'GENERATED'));

ALTER TABLE payroll_details ADD COLUMN benefit_in_kind DECIMAL(19, 2) NOT NULL DEFAULT 0;

CREATE TABLE benefit_in_kind (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_transaction UUID NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    id_employee UUID NOT NULL REFERENCES employees(id),
    amount DECIMAL(19, 2) NOT NULL,
    description VARCHAR(255) NOT NULL,
    id_payroll_detail UUID REFERENCES payroll_details(id) ON DELETE SET NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_benefit_in_kind_amount CHECK (amount > 0)
);
CREATE INDEX idx_benefit_in_kind_transaction ON benefit_in_kind(id_transaction);
CREATE INDEX idx_benefit_in_kind_employee ON benefit_in_kind(id_employee);
CREATE INDEX idx_benefit_in_kind_payroll_detail ON benefit_in_kind(id_payroll_detail);

-- System tag attached to every transaction that carries a benefit-in-kind recipient, so the existing
-- tag filter and tag report apply. Resolved by code in BenefitInKindService.
INSERT INTO tag_types (id, code, name, description)
VALUES (gen_random_uuid(), 'NATURA', 'Natura', 'Transaksi natura/kenikmatan karyawan');
INSERT INTO tags (id, id_tag_type, code, name, description)
SELECT gen_random_uuid(), id, 'NATURA', 'Natura/Kenikmatan', 'Transaksi dengan penerima natura'
FROM tag_types WHERE code = 'NATURA';

-- Seed accounts. Later features resolve these accounts by code (V011 wires bonus and THR to 5.8.01 and
-- 5.8.02), so a code already in use is kept only when it is the same account: same type, name
-- (case-insensitive), header flag and normal balance. Any difference fails the migration and lists the
-- codes. An existing leaf (non-header) account that would receive a seed account as child also fails,
-- because it would silently turn into a parent while holding postings.
CREATE TEMPORARY TABLE v008_seed_accounts (
    account_code VARCHAR(20), account_name VARCHAR(255), account_type VARCHAR(20), normal_balance VARCHAR(10),
    parent_code VARCHAR(20), is_header BOOLEAN, is_permanent BOOLEAN, fiscal_treatment VARCHAR(20), sort_order INT
) ON COMMIT DROP;
INSERT INTO v008_seed_accounts VALUES
    ('2.1.25', 'Hutang PPh 26',                 'LIABILITY', 'CREDIT', '2.1', FALSE, TRUE,  NULL,             1),
    ('2.1.40', 'Hutang Dividen',                'LIABILITY', 'CREDIT', '2.1', FALSE, TRUE,  NULL,             2),
    ('3.2.03', 'Dividen',                       'EQUITY',    'DEBIT',  '3.2', FALSE, FALSE, NULL,             3),
    ('5.8',    'Beban Karyawan & Koreksi Fiskal', 'EXPENSE', 'DEBIT',  '5',   TRUE,  FALSE, NULL,             4),
    ('5.8.01', 'Beban Bonus',                   'EXPENSE',   'DEBIT',  '5.8', FALSE, FALSE, 'DEDUCTIBLE',     5),
    ('5.8.02', 'Beban THR',                     'EXPENSE',   'DEBIT',  '5.8', FALSE, FALSE, 'DEDUCTIBLE',     6),
    ('5.8.03', 'Beban Natura/Kenikmatan',       'EXPENSE',   'DEBIT',  '5.8', FALSE, FALSE, 'CONDITIONAL',    7),
    ('5.8.04', 'Beban Tidak Dapat Dikurangkan', 'EXPENSE',   'DEBIT',  '5.8', FALSE, FALSE, 'NON_DEDUCTIBLE', 8);

DO $$
DECLARE
    conflict TEXT;
    seed RECORD;
BEGIN
    SELECT string_agg(c.account_code || ' (' || c.account_name || ', ' || c.account_type || ', '
                      || CASE WHEN c.is_header THEN 'header' ELSE 'leaf' END || ', ' || c.normal_balance
                      || '; expected ' || s.account_name || ', ' || s.account_type || ', '
                      || CASE WHEN s.is_header THEN 'header' ELSE 'leaf' END || ', ' || s.normal_balance || ')',
                      ', ' ORDER BY c.account_code)
      INTO conflict
      FROM chart_of_accounts c
      JOIN v008_seed_accounts s ON s.account_code = c.account_code
     WHERE c.account_type <> s.account_type
        OR lower(btrim(c.account_name)) <> lower(s.account_name)
        OR c.is_header <> s.is_header
        OR c.normal_balance <> s.normal_balance;
    IF conflict IS NOT NULL THEN
        RAISE EXCEPTION 'V008: account codes reserved for seed accounts are used by different accounts: %. Rename or renumber those accounts first.', conflict;
    END IF;

    SELECT string_agg(DISTINCT p.account_code || ' (' || p.account_name || ')', ', ')
      INTO conflict
      FROM v008_seed_accounts s
      JOIN chart_of_accounts p ON p.account_code = s.parent_code
     WHERE p.is_header = FALSE
       AND NOT EXISTS (SELECT 1 FROM chart_of_accounts c WHERE c.account_code = s.account_code);
    IF conflict IS NOT NULL THEN
        RAISE EXCEPTION 'V008: leaf accounts would become parents of seed accounts: %. Renumber those accounts first.', conflict;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM chart_of_accounts) THEN
        RETURN;
    END IF;

    SELECT string_agg(DISTINCT s.parent_code, ', ')
      INTO conflict
      FROM v008_seed_accounts s
     WHERE s.parent_code NOT IN (SELECT account_code FROM chart_of_accounts)
       AND s.parent_code NOT IN (SELECT account_code FROM v008_seed_accounts);
    IF conflict IS NOT NULL THEN
        RAISE EXCEPTION 'V008: parent accounts for the seed accounts are missing: %', conflict;
    END IF;

    -- Parents precede children in sort_order, so 5.8 exists when 5.8.xx looks it up.
    FOR seed IN SELECT * FROM v008_seed_accounts ORDER BY sort_order LOOP
        INSERT INTO chart_of_accounts (id, account_code, account_name, account_type, normal_balance,
                                       id_parent, level, is_header, active, is_permanent, fiscal_treatment)
        SELECT gen_random_uuid(), seed.account_code, seed.account_name, seed.account_type, seed.normal_balance,
               p.id, p.level + 1, seed.is_header, TRUE, seed.is_permanent, seed.fiscal_treatment
          FROM chart_of_accounts p
         WHERE p.account_code = seed.parent_code
        ON CONFLICT (account_code) DO NOTHING;
    END LOOP;
END $$;
