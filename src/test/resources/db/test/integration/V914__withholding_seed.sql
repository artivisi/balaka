-- Withholding seed for the integration DB: V009 skips deadline and templates because V800 runs after it.
INSERT INTO tax_deadlines (id, name, deadline_type, due_day, use_last_day_of_month, description, reminder_days_before, active) VALUES
('f0000000-0000-0000-0000-000000000009', 'Setor PPh 26', 'PPH_26_PAYMENT', 15, FALSE,
 'Pembayaran PPh Pasal 26 atas penghasilan penerima luar negeri. Jatuh tempo tanggal 15 bulan berikutnya.', 7, TRUE);

INSERT INTO journal_templates (id, template_name, category, cash_flow_category, template_type, description, is_system, active) VALUES
('e0000000-0000-0000-0000-000000000043', 'Pembayaran dengan Pemotongan PPh', 'PAYMENT', 'OPERATING', 'DETAILED',
 'Pembayaran ke penerima dengan pemotongan PPh 21/23/26. Variabel: settledAmount, netAmount, taxAmount', FALSE, TRUE),
('e0000000-0000-0000-0000-000000000044', 'Setor PPh Dipotong', 'PAYMENT', 'OPERATING', 'SIMPLE',
 'Penyetoran PPh 21/23/26 hasil pemotongan ke kas negara', FALSE, TRUE);

INSERT INTO journal_template_lines (id, id_journal_template, id_account, position, formula, line_order, description, account_hint) VALUES
(gen_random_uuid(), 'e0000000-0000-0000-0000-000000000043', NULL, 'DEBIT', 'settledAmount', 1, 'Beban atau hutang usaha', 'BEBAN'),
(gen_random_uuid(), 'e0000000-0000-0000-0000-000000000043', NULL, 'CREDIT', 'netAmount', 2, 'Pembayaran dari bank', 'BANK'),
(gen_random_uuid(), 'e0000000-0000-0000-0000-000000000043', NULL, 'CREDIT', 'taxAmount', 3, 'Hutang PPh', 'HUTANG_PPH'),
(gen_random_uuid(), 'e0000000-0000-0000-0000-000000000044', NULL, 'DEBIT', 'amount', 1, 'Hutang PPh', 'HUTANG_PPH'),
(gen_random_uuid(), 'e0000000-0000-0000-0000-000000000044', NULL, 'CREDIT', 'amount', 2, 'Pembayaran dari bank', 'BANK');

-- V903 added a second account with this name; the withholding flow requires exactly one.
UPDATE chart_of_accounts SET account_name = 'Hutang PPh 23 (uji formula)' WHERE account_code = '2.1.05';
