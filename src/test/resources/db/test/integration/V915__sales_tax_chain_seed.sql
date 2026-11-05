-- Sales tax chain seed for the integration DB: V010 skips account and template because V800 runs after it.
INSERT INTO chart_of_accounts (id, account_code, account_name, account_type, normal_balance, id_parent, level, is_header, active, is_permanent)
SELECT gen_random_uuid(), '1.1.26', 'Kredit Pajak PPh 23', 'ASSET', 'DEBIT', p.id, p.level + 1, FALSE, TRUE, TRUE
FROM chart_of_accounts p WHERE p.account_code = '1.1';

INSERT INTO journal_templates (id, template_name, category, cash_flow_category, template_type, description, is_system, active) VALUES
('e0000000-0000-0000-0000-000000000045', 'Pemotongan PPh 23 oleh Pelanggan', 'RECEIPT', 'OPERATING', 'SIMPLE',
 'Kredit pajak atas PPh 23 yang dipotong pelanggan dari piutang invoice', FALSE, TRUE);

INSERT INTO journal_template_lines (id, id_journal_template, id_account, position, formula, line_order, description, account_hint) VALUES
(gen_random_uuid(), 'e0000000-0000-0000-0000-000000000045', NULL, 'DEBIT', 'amount', 1, 'Kredit Pajak PPh 23', 'KREDIT_PPH23'),
(gen_random_uuid(), 'e0000000-0000-0000-0000-000000000045', NULL, 'CREDIT', 'amount', 2, 'Piutang usaha', 'PIUTANG');
