-- Issue #47: bonus and THR in the payroll run, unposted-run alert.
--
-- 1. payroll_details.bonus / thr: amounts counted in the run. gross_salary keeps meaning cash gross,
--    so it now equals base_salary + bonus + thr. Existing rows have no such income.
-- 2. payroll_additions: per-employee input that survives recalculation, which replaces the details.
-- 3. Post Gaji Bulanan gets lines for bonus, thr and pph21Overpaid. Each is zero, and skipped at posting,
--    unless the run carries it. pph21Overpaid is a debit on the template's PPh 21 account: December
--    reconciliation can return PPh 21 to employees, and a journal line cannot hold a negative amount.
-- 4. Alert rule PAYROLL_UNPOSTED: threshold is days after the period end.

ALTER TABLE payroll_details ADD COLUMN bonus DECIMAL(19, 2) NOT NULL DEFAULT 0;
ALTER TABLE payroll_details ADD COLUMN thr DECIMAL(19, 2) NOT NULL DEFAULT 0;

CREATE TABLE payroll_additions (
    id UUID PRIMARY KEY,
    id_payroll_run UUID NOT NULL REFERENCES payroll_runs(id) ON DELETE CASCADE,
    id_employee UUID NOT NULL REFERENCES employees(id),
    addition_type VARCHAR(10) NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_payroll_addition_type CHECK (addition_type IN ('BONUS', 'THR')),
    CONSTRAINT chk_payroll_addition_amount CHECK (amount > 0)
);
CREATE INDEX idx_payroll_additions_run ON payroll_additions(id_payroll_run);
CREATE INDEX idx_payroll_additions_employee ON payroll_additions(id_employee);

DO $$
DECLARE
    payroll_template UUID;
    bonus_account UUID;
    thr_account UUID;
    pph21_account UUID;
    next_order INTEGER;
BEGIN
    SELECT id INTO payroll_template FROM journal_templates
     WHERE template_name = 'Post Gaji Bulanan' AND is_current_version = TRUE AND deleted_at IS NULL;
    -- An empty install gets the lines from its seed pack
    IF payroll_template IS NOT NULL THEN
        SELECT id INTO bonus_account FROM chart_of_accounts WHERE account_code = '5.8.01';
        SELECT id INTO thr_account FROM chart_of_accounts WHERE account_code = '5.8.02';
        IF bonus_account IS NULL OR thr_account IS NULL THEN
            RAISE EXCEPTION 'V011: accounts 5.8.01 and 5.8.02 are required by Post Gaji Bulanan; create them first';
        END IF;
        SELECT id_account INTO pph21_account FROM journal_template_lines
         WHERE id_journal_template = payroll_template AND position = 'CREDIT' AND formula = 'pph21' AND deleted_at IS NULL;
        IF pph21_account IS NULL THEN
            RAISE EXCEPTION 'V011: Post Gaji Bulanan has no credit line with formula pph21 and a fixed account; fix the template first';
        END IF;
        SELECT MAX(line_order) + 1 INTO next_order FROM journal_template_lines
         WHERE id_journal_template = payroll_template;
        INSERT INTO journal_template_lines (id, id_journal_template, id_account, position, formula, line_order, description)
        VALUES (gen_random_uuid(), payroll_template, bonus_account, 'DEBIT', 'bonus', next_order, 'Beban bonus karyawan'),
               (gen_random_uuid(), payroll_template, thr_account, 'DEBIT', 'thr', next_order + 1, 'Beban THR karyawan'),
               (gen_random_uuid(), payroll_template, pph21_account, 'DEBIT', 'pph21Overpaid', next_order + 2, 'Kelebihan potong PPh 21 dikembalikan ke karyawan');
        UPDATE journal_templates
           SET description = description || ' Variabel tambahan: bonus thr pph21Overpaid'
         WHERE id = payroll_template;
    END IF;
END $$;

INSERT INTO alert_rules (id, row_version, alert_type, threshold, enabled, description)
VALUES (gen_random_uuid(), 0, 'PAYROLL_UNPOSTED', 0.00, TRUE,
        'Peringatan jika payroll berstatus disetujui belum diposting X hari setelah akhir periode');
