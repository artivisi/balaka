-- V011 runs before V800, so the integration payroll template needs the same lines here.
INSERT INTO journal_template_lines (id, id_journal_template, id_account, position, formula, line_order, description)
SELECT gen_random_uuid(), t.id, a.id, 'DEBIT', l.formula, l.line_order, l.description
FROM journal_templates t
CROSS JOIN (VALUES ('5.8.01', 'bonus', 100, 'Beban bonus karyawan'), ('5.8.02', 'thr', 101, 'Beban THR karyawan'))
    AS l(account_code, formula, line_order, description)
JOIN chart_of_accounts a ON a.account_code = l.account_code
WHERE t.template_name = 'Post Gaji Bulanan' AND t.is_current_version = TRUE;

INSERT INTO journal_template_lines (id, id_journal_template, id_account, position, formula, line_order, description)
SELECT gen_random_uuid(), p.id_journal_template, p.id_account, 'DEBIT', 'pph21Overpaid', 102, 'Kelebihan potong PPh 21 dikembalikan ke karyawan'
FROM journal_template_lines p
JOIN journal_templates t ON t.id = p.id_journal_template
WHERE t.template_name = 'Post Gaji Bulanan' AND t.is_current_version = TRUE
  AND p.position = 'CREDIT' AND p.formula = 'pph21';
