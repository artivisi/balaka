-- V004: Application Seed Data
-- Transaction sequences, Chart of Accounts, Journal Templates

-- ============================================
-- Transaction Sequences - Initialize for 2025
-- ============================================

INSERT INTO transaction_sequences (id, sequence_type, prefix, year, last_number) VALUES
('d0000000-0000-0000-0000-000000000001', 'TRANSACTION', 'TRX', 2025, 0),
('d0000000-0000-0000-0000-000000000002', 'JOURNAL', 'JE', 2025, 0);

-- ============================================
-- Chart of Accounts - IT Services
-- ============================================

-- ASET (1.x.xx) - Permanent accounts
INSERT INTO chart_of_accounts (id, account_code, account_name, account_type, normal_balance, level, is_header, active, is_permanent) VALUES
('10000000-0000-0000-0000-000000000001', '1', 'ASET', 'ASSET', 'DEBIT', 1, TRUE, TRUE, TRUE);

INSERT INTO chart_of_accounts (id, account_code, account_name, account_type, normal_balance, id_parent, level, is_header, active, is_permanent) VALUES
('10000000-0000-0000-0000-000000000011', '1.1', 'Aset Lancar', 'ASSET', 'DEBIT', '10000000-0000-0000-0000-000000000001', 2, TRUE, TRUE, TRUE),
('10000000-0000-0000-0000-000000000012', '1.2', 'Aset Tetap', 'ASSET', 'DEBIT', '10000000-0000-0000-0000-000000000001', 2, TRUE, TRUE, TRUE),
('10000000-0000-0000-0000-000000000013', '1.3', 'Aset Tak Berwujud', 'ASSET', 'DEBIT', '10000000-0000-0000-0000-000000000001', 2, TRUE, TRUE, TRUE);

INSERT INTO chart_of_accounts (id, account_code, account_name, account_type, normal_balance, id_parent, level, is_header, active, is_permanent) VALUES
('10000000-0000-0000-0000-000000000101', '1.1.01', 'Kas', 'ASSET', 'DEBIT', '10000000-0000-0000-0000-000000000011', 3, FALSE, TRUE, TRUE),
('10000000-0000-0000-0000-000000000102', '1.1.02', 'Bank BCA', 'ASSET', 'DEBIT', '10000000-0000-0000-0000-000000000011', 3, FALSE, TRUE, TRUE),
('10000000-0000-0000-0000-000000000103', '1.1.03', 'Bank BNI', 'ASSET', 'DEBIT', '10000000-0000-0000-0000-000000000011', 3, FALSE, TRUE, TRUE),
('10000000-0000-0000-0000-000000000104', '1.1.04', 'Piutang Usaha', 'ASSET', 'DEBIT', '10000000-0000-0000-0000-000000000011', 3, FALSE, TRUE, TRUE),
('10000000-0000-0000-0000-000000000105', '1.1.05', 'Asuransi Dibayar Dimuka', 'ASSET', 'DEBIT', '10000000-0000-0000-0000-000000000011', 3, FALSE, TRUE, TRUE),
('10000000-0000-0000-0000-000000000106', '1.1.06', 'Sewa Dibayar Dimuka', 'ASSET', 'DEBIT', '10000000-0000-0000-0000-000000000011', 3, FALSE, TRUE, TRUE),
('10000000-0000-0000-0000-000000000107', '1.1.07', 'Langganan Dibayar Dimuka', 'ASSET', 'DEBIT', '10000000-0000-0000-0000-000000000011', 3, FALSE, TRUE, TRUE),
('10000000-0000-0000-0000-000000000108', '1.1.08', 'Piutang Pendapatan', 'ASSET', 'DEBIT', '10000000-0000-0000-0000-000000000011', 3, FALSE, TRUE, TRUE),
('10000000-0000-0000-0000-000000000125', '1.1.25', 'PPN Masukan', 'ASSET', 'DEBIT', '10000000-0000-0000-0000-000000000011', 3, FALSE, TRUE, TRUE),
('10000000-0000-0000-0000-000000000121', '1.2.01', 'Peralatan Komputer', 'ASSET', 'DEBIT', '10000000-0000-0000-0000-000000000012', 3, FALSE, TRUE, TRUE),
('10000000-0000-0000-0000-000000000122', '1.2.02', 'Akum. Penyusutan Peralatan', 'ASSET', 'CREDIT', '10000000-0000-0000-0000-000000000012', 3, FALSE, TRUE, TRUE),
('10000000-0000-0000-0000-000000000131', '1.3.01', 'Website & Software', 'ASSET', 'DEBIT', '10000000-0000-0000-0000-000000000013', 3, FALSE, TRUE, TRUE),
('10000000-0000-0000-0000-000000000132', '1.3.02', 'Akum. Amortisasi Aset Tak Berwujud', 'ASSET', 'CREDIT', '10000000-0000-0000-0000-000000000013', 3, FALSE, TRUE, TRUE);

-- LIABILITAS (2.x.xx) - Permanent accounts
INSERT INTO chart_of_accounts (id, account_code, account_name, account_type, normal_balance, level, is_header, active, is_permanent) VALUES
('20000000-0000-0000-0000-000000000001', '2', 'LIABILITAS', 'LIABILITY', 'CREDIT', 1, TRUE, TRUE, TRUE);

INSERT INTO chart_of_accounts (id, account_code, account_name, account_type, normal_balance, id_parent, level, is_header, active, is_permanent) VALUES
('20000000-0000-0000-0000-000000000011', '2.1', 'Liabilitas Jangka Pendek', 'LIABILITY', 'CREDIT', '20000000-0000-0000-0000-000000000001', 2, TRUE, TRUE, TRUE);

INSERT INTO chart_of_accounts (id, account_code, account_name, account_type, normal_balance, id_parent, level, is_header, active, is_permanent) VALUES
('20000000-0000-0000-0000-000000000101', '2.1.01', 'Hutang Usaha', 'LIABILITY', 'CREDIT', '20000000-0000-0000-0000-000000000011', 3, FALSE, TRUE, TRUE),
('20000000-0000-0000-0000-000000000102', '2.1.02', 'Hutang Pajak', 'LIABILITY', 'CREDIT', '20000000-0000-0000-0000-000000000011', 3, FALSE, TRUE, TRUE),
('20000000-0000-0000-0000-000000000103', '2.1.03', 'Hutang PPN', 'LIABILITY', 'CREDIT', '20000000-0000-0000-0000-000000000011', 3, FALSE, TRUE, TRUE),
('20000000-0000-0000-0000-000000000104', '2.1.04', 'Pendapatan Diterima Dimuka', 'LIABILITY', 'CREDIT', '20000000-0000-0000-0000-000000000011', 3, FALSE, TRUE, TRUE),
('20000000-0000-0000-0000-000000000107', '2.1.07', 'Hutang Gaji', 'LIABILITY', 'CREDIT', '20000000-0000-0000-0000-000000000011', 3, FALSE, TRUE, TRUE),
('20000000-0000-0000-0000-000000000108', '2.1.08', 'Hutang BPJS', 'LIABILITY', 'CREDIT', '20000000-0000-0000-0000-000000000011', 3, FALSE, TRUE, TRUE),
('20000000-0000-0000-0000-000000000120', '2.1.20', 'Hutang PPh 21', 'LIABILITY', 'CREDIT', '20000000-0000-0000-0000-000000000011', 3, FALSE, TRUE, TRUE),
('20000000-0000-0000-0000-000000000121', '2.1.21', 'Hutang PPh 23', 'LIABILITY', 'CREDIT', '20000000-0000-0000-0000-000000000011', 3, FALSE, TRUE, TRUE),
('20000000-0000-0000-0000-000000000122', '2.1.22', 'Hutang PPh 4(2)', 'LIABILITY', 'CREDIT', '20000000-0000-0000-0000-000000000011', 3, FALSE, TRUE, TRUE),
('20000000-0000-0000-0000-000000000123', '2.1.23', 'Hutang PPh 25', 'LIABILITY', 'CREDIT', '20000000-0000-0000-0000-000000000011', 3, FALSE, TRUE, TRUE),
('20000000-0000-0000-0000-000000000124', '2.1.24', 'Hutang PPh 29', 'LIABILITY', 'CREDIT', '20000000-0000-0000-0000-000000000011', 3, FALSE, TRUE, TRUE);

-- EKUITAS (3.x.xx) - Permanent accounts
INSERT INTO chart_of_accounts (id, account_code, account_name, account_type, normal_balance, level, is_header, active, is_permanent) VALUES
('30000000-0000-0000-0000-000000000001', '3', 'EKUITAS', 'EQUITY', 'CREDIT', 1, TRUE, TRUE, TRUE);

INSERT INTO chart_of_accounts (id, account_code, account_name, account_type, normal_balance, id_parent, level, is_header, active, is_permanent) VALUES
('30000000-0000-0000-0000-000000000011', '3.1', 'Modal', 'EQUITY', 'CREDIT', '30000000-0000-0000-0000-000000000001', 2, TRUE, TRUE, TRUE),
('30000000-0000-0000-0000-000000000012', '3.2', 'Laba', 'EQUITY', 'CREDIT', '30000000-0000-0000-0000-000000000001', 2, TRUE, TRUE, TRUE);

INSERT INTO chart_of_accounts (id, account_code, account_name, account_type, normal_balance, id_parent, level, is_header, active, is_permanent) VALUES
('30000000-0000-0000-0000-000000000101', '3.1.01', 'Modal Disetor', 'EQUITY', 'CREDIT', '30000000-0000-0000-0000-000000000011', 3, FALSE, TRUE, TRUE),
('30000000-0000-0000-0000-000000000121', '3.2.01', 'Laba Ditahan', 'EQUITY', 'CREDIT', '30000000-0000-0000-0000-000000000012', 3, FALSE, TRUE, TRUE),
('30000000-0000-0000-0000-000000000122', '3.2.02', 'Laba Berjalan', 'EQUITY', 'CREDIT', '30000000-0000-0000-0000-000000000012', 3, FALSE, TRUE, TRUE);

-- PENDAPATAN (4.x.xx) - Temporary accounts
INSERT INTO chart_of_accounts (id, account_code, account_name, account_type, normal_balance, level, is_header, active, is_permanent) VALUES
('40000000-0000-0000-0000-000000000001', '4', 'PENDAPATAN', 'REVENUE', 'CREDIT', 1, TRUE, TRUE, FALSE);

INSERT INTO chart_of_accounts (id, account_code, account_name, account_type, normal_balance, id_parent, level, is_header, active, is_permanent) VALUES
('40000000-0000-0000-0000-000000000011', '4.1', 'Pendapatan Usaha', 'REVENUE', 'CREDIT', '40000000-0000-0000-0000-000000000001', 2, TRUE, TRUE, FALSE),
('40000000-0000-0000-0000-000000000012', '4.2', 'Pendapatan Lain-lain', 'REVENUE', 'CREDIT', '40000000-0000-0000-0000-000000000001', 2, TRUE, TRUE, FALSE);

INSERT INTO chart_of_accounts (id, account_code, account_name, account_type, normal_balance, id_parent, level, is_header, active, is_permanent) VALUES
('40000000-0000-0000-0000-000000000101', '4.1.01', 'Pendapatan Jasa Konsultasi', 'REVENUE', 'CREDIT', '40000000-0000-0000-0000-000000000011', 3, FALSE, TRUE, FALSE),
('40000000-0000-0000-0000-000000000102', '4.1.02', 'Pendapatan Jasa Development', 'REVENUE', 'CREDIT', '40000000-0000-0000-0000-000000000011', 3, FALSE, TRUE, FALSE),
('40000000-0000-0000-0000-000000000103', '4.1.03', 'Pendapatan Jasa Training', 'REVENUE', 'CREDIT', '40000000-0000-0000-0000-000000000011', 3, FALSE, TRUE, FALSE),
('40000000-0000-0000-0000-000000000121', '4.2.01', 'Pendapatan Bunga', 'REVENUE', 'CREDIT', '40000000-0000-0000-0000-000000000012', 3, FALSE, TRUE, FALSE);

-- BEBAN (5.x.xx) - Temporary accounts
INSERT INTO chart_of_accounts (id, account_code, account_name, account_type, normal_balance, level, is_header, active, is_permanent) VALUES
('50000000-0000-0000-0000-000000000001', '5', 'BEBAN', 'EXPENSE', 'DEBIT', 1, TRUE, TRUE, FALSE);

INSERT INTO chart_of_accounts (id, account_code, account_name, account_type, normal_balance, id_parent, level, is_header, active, is_permanent) VALUES
('50000000-0000-0000-0000-000000000011', '5.1', 'Beban Operasional', 'EXPENSE', 'DEBIT', '50000000-0000-0000-0000-000000000001', 2, TRUE, TRUE, FALSE),
('50000000-0000-0000-0000-000000000012', '5.2', 'Beban Lain-lain', 'EXPENSE', 'DEBIT', '50000000-0000-0000-0000-000000000001', 2, TRUE, TRUE, FALSE);

INSERT INTO chart_of_accounts (id, account_code, account_name, account_type, normal_balance, id_parent, level, is_header, active, is_permanent) VALUES
('50000000-0000-0000-0000-000000000101', '5.1.01', 'Beban Gaji', 'EXPENSE', 'DEBIT', '50000000-0000-0000-0000-000000000011', 3, FALSE, TRUE, FALSE),
('50000000-0000-0000-0000-000000000102', '5.1.02', 'Beban Server & Cloud', 'EXPENSE', 'DEBIT', '50000000-0000-0000-0000-000000000011', 3, FALSE, TRUE, FALSE),
('50000000-0000-0000-0000-000000000103', '5.1.03', 'Beban Software & Lisensi', 'EXPENSE', 'DEBIT', '50000000-0000-0000-0000-000000000011', 3, FALSE, TRUE, FALSE),
('50000000-0000-0000-0000-000000000104', '5.1.04', 'Beban Internet & Telekomunikasi', 'EXPENSE', 'DEBIT', '50000000-0000-0000-0000-000000000011', 3, FALSE, TRUE, FALSE),
('50000000-0000-0000-0000-000000000105', '5.1.05', 'Beban Administrasi & Umum', 'EXPENSE', 'DEBIT', '50000000-0000-0000-0000-000000000011', 3, FALSE, TRUE, FALSE),
('50000000-0000-0000-0000-000000000106', '5.1.06', 'Beban Sewa Kantor', 'EXPENSE', 'DEBIT', '50000000-0000-0000-0000-000000000011', 3, FALSE, FALSE, FALSE),
('50000000-0000-0000-0000-000000000107', '5.1.07', 'Beban Penyusutan', 'EXPENSE', 'DEBIT', '50000000-0000-0000-0000-000000000011', 3, FALSE, TRUE, FALSE),
('50000000-0000-0000-0000-000000000108', '5.1.08', 'Beban Asuransi', 'EXPENSE', 'DEBIT', '50000000-0000-0000-0000-000000000011', 3, FALSE, TRUE, FALSE),
('50000000-0000-0000-0000-000000000109', '5.1.09', 'Beban Amortisasi', 'EXPENSE', 'DEBIT', '50000000-0000-0000-0000-000000000011', 3, FALSE, TRUE, FALSE),
('50000000-0000-0000-0000-000000000111', '5.1.11', 'Beban BPJS', 'EXPENSE', 'DEBIT', '50000000-0000-0000-0000-000000000011', 3, FALSE, TRUE, FALSE),
('50000000-0000-0000-0000-000000000121', '5.2.01', 'Beban Bank', 'EXPENSE', 'DEBIT', '50000000-0000-0000-0000-000000000012', 3, FALSE, TRUE, FALSE);

-- ============================================
-- Journal Templates - IT Services
-- ============================================

-- Template: Pendapatan Jasa Konsultasi (user template - industry specific)
INSERT INTO journal_templates (id, template_name, category, cash_flow_category, template_type, description, is_system, active) VALUES
('e0000000-0000-0000-0000-000000000001', 'Pendapatan Jasa Konsultasi', 'INCOME', 'OPERATING', 'SIMPLE', 'Template untuk mencatat pendapatan dari jasa konsultasi IT', FALSE, TRUE);

INSERT INTO journal_template_lines (id, id_journal_template, id_account, position, formula, line_order) VALUES
('e1000000-0000-0000-0000-000000000001', 'e0000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000102', 'DEBIT', 'amount', 1),
('e1000000-0000-0000-0000-000000000002', 'e0000000-0000-0000-0000-000000000001', '40000000-0000-0000-0000-000000000101', 'CREDIT', 'amount', 2);

-- Template: Pendapatan Jasa Development (user template - industry specific)
INSERT INTO journal_templates (id, template_name, category, cash_flow_category, template_type, description, is_system, active) VALUES
('e0000000-0000-0000-0000-000000000002', 'Pendapatan Jasa Development', 'INCOME', 'OPERATING', 'SIMPLE', 'Template untuk mencatat pendapatan dari jasa development', FALSE, TRUE);

INSERT INTO journal_template_lines (id, id_journal_template, id_account, position, formula, line_order) VALUES
('e1000000-0000-0000-0000-000000000003', 'e0000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000102', 'DEBIT', 'amount', 1),
('e1000000-0000-0000-0000-000000000004', 'e0000000-0000-0000-0000-000000000002', '40000000-0000-0000-0000-000000000102', 'CREDIT', 'amount', 2);

-- Template: Pendapatan Jasa Training (user template - industry specific)
INSERT INTO journal_templates (id, template_name, category, cash_flow_category, template_type, description, is_system, active) VALUES
('e0000000-0000-0000-0000-000000000003', 'Pendapatan Jasa Training', 'INCOME', 'OPERATING', 'SIMPLE', 'Template untuk mencatat pendapatan dari jasa training', FALSE, TRUE);

INSERT INTO journal_template_lines (id, id_journal_template, id_account, position, formula, line_order) VALUES
('e1000000-0000-0000-0000-000000000005', 'e0000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000102', 'DEBIT', 'amount', 1),
('e1000000-0000-0000-0000-000000000006', 'e0000000-0000-0000-0000-000000000003', '40000000-0000-0000-0000-000000000103', 'CREDIT', 'amount', 2);

-- Template: Bayar Beban Gaji (user template - general purpose)
INSERT INTO journal_templates (id, template_name, category, cash_flow_category, template_type, description, is_system, active) VALUES
('e0000000-0000-0000-0000-000000000004', 'Bayar Beban Gaji', 'EXPENSE', 'OPERATING', 'SIMPLE', 'Template untuk mencatat pembayaran gaji karyawan', FALSE, TRUE);

INSERT INTO journal_template_lines (id, id_journal_template, id_account, position, formula, line_order) VALUES
('e1000000-0000-0000-0000-000000000007', 'e0000000-0000-0000-0000-000000000004', '50000000-0000-0000-0000-000000000101', 'DEBIT', 'amount', 1),
('e1000000-0000-0000-0000-000000000008', 'e0000000-0000-0000-0000-000000000004', '10000000-0000-0000-0000-000000000102', 'CREDIT', 'amount', 2);

-- Template: Bayar Beban Server & Cloud (user template - industry specific)
INSERT INTO journal_templates (id, template_name, category, cash_flow_category, template_type, description, is_system, active) VALUES
('e0000000-0000-0000-0000-000000000005', 'Bayar Beban Server & Cloud', 'EXPENSE', 'OPERATING', 'SIMPLE', 'Template untuk mencatat pembayaran server dan cloud', FALSE, TRUE);

INSERT INTO journal_template_lines (id, id_journal_template, id_account, position, formula, line_order) VALUES
('e1000000-0000-0000-0000-000000000009', 'e0000000-0000-0000-0000-000000000005', '50000000-0000-0000-0000-000000000102', 'DEBIT', 'amount', 1),
('e1000000-0000-0000-0000-000000000010', 'e0000000-0000-0000-0000-000000000005', '10000000-0000-0000-0000-000000000102', 'CREDIT', 'amount', 2);

-- Template: Bayar Beban Software & Lisensi (user template - industry specific)
INSERT INTO journal_templates (id, template_name, category, cash_flow_category, template_type, description, is_system, active) VALUES
('e0000000-0000-0000-0000-000000000006', 'Bayar Beban Software & Lisensi', 'EXPENSE', 'OPERATING', 'SIMPLE', 'Template untuk mencatat pembayaran software dan lisensi', FALSE, TRUE);

INSERT INTO journal_template_lines (id, id_journal_template, id_account, position, formula, line_order) VALUES
('e1000000-0000-0000-0000-000000000011', 'e0000000-0000-0000-0000-000000000006', '50000000-0000-0000-0000-000000000103', 'DEBIT', 'amount', 1),
('e1000000-0000-0000-0000-000000000012', 'e0000000-0000-0000-0000-000000000006', '10000000-0000-0000-0000-000000000102', 'CREDIT', 'amount', 2);

-- Template: Bayar Beban Internet (user template - general purpose)
INSERT INTO journal_templates (id, template_name, category, cash_flow_category, template_type, description, is_system, active) VALUES
('e0000000-0000-0000-0000-000000000007', 'Bayar Beban Internet & Telekomunikasi', 'EXPENSE', 'OPERATING', 'SIMPLE', 'Template untuk mencatat pembayaran internet dan telekomunikasi', FALSE, TRUE);

INSERT INTO journal_template_lines (id, id_journal_template, id_account, position, formula, line_order) VALUES
('e1000000-0000-0000-0000-000000000013', 'e0000000-0000-0000-0000-000000000007', '50000000-0000-0000-0000-000000000104', 'DEBIT', 'amount', 1),
('e1000000-0000-0000-0000-000000000014', 'e0000000-0000-0000-0000-000000000007', '10000000-0000-0000-0000-000000000102', 'CREDIT', 'amount', 2);

-- Template: Bayar Beban Administrasi (user template - general purpose)
INSERT INTO journal_templates (id, template_name, category, cash_flow_category, template_type, description, is_system, active) VALUES
('e0000000-0000-0000-0000-000000000008', 'Bayar Beban Administrasi & Umum', 'EXPENSE', 'OPERATING', 'SIMPLE', 'Template untuk mencatat pembayaran beban administrasi dan umum', FALSE, TRUE);

INSERT INTO journal_template_lines (id, id_journal_template, id_account, position, formula, line_order) VALUES
('e1000000-0000-0000-0000-000000000015', 'e0000000-0000-0000-0000-000000000008', '50000000-0000-0000-0000-000000000105', 'DEBIT', 'amount', 1),
('e1000000-0000-0000-0000-000000000016', 'e0000000-0000-0000-0000-000000000008', '10000000-0000-0000-0000-000000000102', 'CREDIT', 'amount', 2);

-- Template: Transfer Antar Bank (user template - general purpose)
INSERT INTO journal_templates (id, template_name, category, cash_flow_category, template_type, description, is_system, active) VALUES
('e0000000-0000-0000-0000-000000000009', 'Transfer Antar Bank', 'TRANSFER', 'OPERATING', 'SIMPLE', 'Template untuk mencatat transfer antar rekening bank', FALSE, TRUE);

INSERT INTO journal_template_lines (id, id_journal_template, id_account, position, formula, line_order) VALUES
('e1000000-0000-0000-0000-000000000017', 'e0000000-0000-0000-0000-000000000009', '10000000-0000-0000-0000-000000000102', 'DEBIT', 'amount', 1),
('e1000000-0000-0000-0000-000000000018', 'e0000000-0000-0000-0000-000000000009', '10000000-0000-0000-0000-000000000103', 'CREDIT', 'amount', 2);

-- Template: Terima Pelunasan Piutang (user template - general purpose)
INSERT INTO journal_templates (id, template_name, category, cash_flow_category, template_type, description, is_system, active) VALUES
('e0000000-0000-0000-0000-000000000010', 'Terima Pelunasan Piutang', 'RECEIPT', 'OPERATING', 'SIMPLE', 'Template untuk mencatat penerimaan pelunasan piutang', FALSE, TRUE);

INSERT INTO journal_template_lines (id, id_journal_template, id_account, position, formula, line_order) VALUES
('e1000000-0000-0000-0000-000000000019', 'e0000000-0000-0000-0000-000000000010', '10000000-0000-0000-0000-000000000102', 'DEBIT', 'amount', 1),
('e1000000-0000-0000-0000-000000000020', 'e0000000-0000-0000-0000-000000000010', '10000000-0000-0000-0000-000000000104', 'CREDIT', 'amount', 2);

-- Template: Bayar Hutang Usaha (user template - general purpose)
INSERT INTO journal_templates (id, template_name, category, cash_flow_category, template_type, description, is_system, active) VALUES
('e0000000-0000-0000-0000-000000000011', 'Bayar Hutang Usaha', 'PAYMENT', 'OPERATING', 'SIMPLE', 'Template untuk mencatat pembayaran hutang usaha', FALSE, TRUE);

INSERT INTO journal_template_lines (id, id_journal_template, id_account, position, formula, line_order) VALUES
('e1000000-0000-0000-0000-000000000021', 'e0000000-0000-0000-0000-000000000011', '20000000-0000-0000-0000-000000000101', 'DEBIT', 'amount', 1),
('e1000000-0000-0000-0000-000000000022', 'e0000000-0000-0000-0000-000000000011', '10000000-0000-0000-0000-000000000102', 'CREDIT', 'amount', 2);

-- Template: Setoran Modal (user template - general purpose)
INSERT INTO journal_templates (id, template_name, category, cash_flow_category, template_type, description, is_system, active) VALUES
('e0000000-0000-0000-0000-000000000012', 'Setoran Modal', 'RECEIPT', 'FINANCING', 'SIMPLE', 'Template untuk mencatat setoran modal dari pemilik', FALSE, TRUE);

INSERT INTO journal_template_lines (id, id_journal_template, id_account, position, formula, line_order) VALUES
('e1000000-0000-0000-0000-000000000023', 'e0000000-0000-0000-0000-000000000012', '10000000-0000-0000-0000-000000000102', 'DEBIT', 'amount', 1),
('e1000000-0000-0000-0000-000000000024', 'e0000000-0000-0000-0000-000000000012', '30000000-0000-0000-0000-000000000101', 'CREDIT', 'amount', 2);

-- Template: Pengakuan Pendapatan Proyek (user template - industry specific)
INSERT INTO journal_templates (id, template_name, category, cash_flow_category, template_type, description, is_system, active) VALUES
('e0000000-0000-0000-0000-000000000013', 'Pengakuan Pendapatan Proyek', 'INCOME', 'OPERATING', 'SIMPLE', 'Template untuk mengakui pendapatan dari pembayaran dimuka saat milestone selesai', FALSE, TRUE);

INSERT INTO journal_template_lines (id, id_journal_template, id_account, position, formula, line_order) VALUES
('e1000000-0000-0000-0000-000000000025', 'e0000000-0000-0000-0000-000000000013', '20000000-0000-0000-0000-000000000104', 'DEBIT', 'amount', 1),
('e1000000-0000-0000-0000-000000000026', 'e0000000-0000-0000-0000-000000000013', '40000000-0000-0000-0000-000000000102', 'CREDIT', 'amount', 2);

-- Template: Post Gaji Bulanan (Payroll Posting)
-- System template for posting payroll to journal entries
-- Uses payroll-specific formula variables: grossSalary, companyBpjs, netPay, totalBpjs, pph21
INSERT INTO journal_templates (id, template_name, category, cash_flow_category, template_type, description, is_system, active) VALUES
('e0000000-0000-0000-0000-000000000014', 'Post Gaji Bulanan', 'EXPENSE', 'OPERATING', 'SIMPLE', 'Template sistem untuk posting payroll bulanan. Menggunakan variabel: grossSalary, companyBpjs, netPay, totalBpjs, pph21', TRUE, TRUE);

INSERT INTO journal_template_lines (id, id_journal_template, id_account, position, formula, line_order, description) VALUES
-- Debit: Beban Gaji (gross salary expense)
('e1000000-0000-0000-0000-000000000027', 'e0000000-0000-0000-0000-000000000014', '50000000-0000-0000-0000-000000000101', 'DEBIT', 'grossSalary', 1, 'Beban gaji karyawan'),
-- Debit: Beban BPJS (company BPJS contribution)
('e1000000-0000-0000-0000-000000000028', 'e0000000-0000-0000-0000-000000000014', '50000000-0000-0000-0000-000000000111', 'DEBIT', 'companyBpjs', 2, 'Beban BPJS perusahaan'),
-- Credit: Hutang Gaji (net pay to employees)
('e1000000-0000-0000-0000-000000000029', 'e0000000-0000-0000-0000-000000000014', '20000000-0000-0000-0000-000000000107', 'CREDIT', 'netPay', 3, 'Hutang gaji karyawan'),
-- Credit: Hutang BPJS (company + employee BPJS)
('e1000000-0000-0000-0000-000000000030', 'e0000000-0000-0000-0000-000000000014', '20000000-0000-0000-0000-000000000108', 'CREDIT', 'totalBpjs', 4, 'Hutang BPJS'),
-- Credit: Hutang PPh 21 (tax withheld)
('e1000000-0000-0000-0000-000000000031', 'e0000000-0000-0000-0000-000000000014', '20000000-0000-0000-0000-000000000120', 'CREDIT', 'pph21', 5, 'Hutang PPh 21');

-- ============================================
-- Tax Deadlines - Indonesian Tax Calendar
-- ============================================
-- Reference: PMK No. 242/PMK.03/2014

INSERT INTO tax_deadlines (id, name, deadline_type, due_day, use_last_day_of_month, description, reminder_days_before, active) VALUES
-- Tax Payments (Setor)
('f0000000-0000-0000-0000-000000000001', 'Setor PPh 21', 'PPH_21_PAYMENT', 10, FALSE, 'Pembayaran PPh Pasal 21 atas gaji karyawan. Jatuh tempo tanggal 10 bulan berikutnya.', 7, TRUE),
('f0000000-0000-0000-0000-000000000002', 'Setor PPh 23', 'PPH_23_PAYMENT', 10, FALSE, 'Pembayaran PPh Pasal 23 atas jasa, dividen, bunga, royalti. Jatuh tempo tanggal 10 bulan berikutnya.', 7, TRUE),
('f0000000-0000-0000-0000-000000000003', 'Setor PPh 4(2)', 'PPH_42_PAYMENT', 10, FALSE, 'Pembayaran PPh Pasal 4 ayat 2 (final) atas sewa tanah/bangunan, dll. Jatuh tempo tanggal 10 bulan berikutnya.', 7, TRUE),
('f0000000-0000-0000-0000-000000000004', 'Setor PPh 25', 'PPH_25_PAYMENT', 15, FALSE, 'Pembayaran angsuran PPh Pasal 25. Jatuh tempo tanggal 15 bulan berikutnya.', 7, TRUE),
('f0000000-0000-0000-0000-000000000005', 'Setor PPN', 'PPN_PAYMENT', 31, TRUE, 'Pembayaran PPN terutang. Jatuh tempo akhir bulan berikutnya.', 7, TRUE),

-- Tax Reporting (Lapor SPT)
('f0000000-0000-0000-0000-000000000006', 'Lapor SPT PPh 21', 'SPT_PPH_21', 20, FALSE, 'Pelaporan SPT Masa PPh 21. Jatuh tempo tanggal 20 bulan berikutnya.', 7, TRUE),
('f0000000-0000-0000-0000-000000000007', 'Lapor SPT PPh 23', 'SPT_PPH_23', 20, FALSE, 'Pelaporan SPT Masa PPh 23 (e-Bupot Unifikasi). Jatuh tempo tanggal 20 bulan berikutnya.', 7, TRUE),
('f0000000-0000-0000-0000-000000000008', 'Lapor SPT PPN', 'SPT_PPN', 31, TRUE, 'Pelaporan SPT Masa PPN (e-Faktur). Jatuh tempo akhir bulan berikutnya.', 7, TRUE);

-- ============================================
-- Salary Components - Indonesian Payroll
-- ============================================
-- BPJS rates based on 2024 regulations:
-- - BPJS Kesehatan: 5% (4% company, 1% employee) of salary
-- - BPJS JHT: 5.7% (3.7% company, 2% employee) of salary
-- - BPJS JKK: 0.24% - 1.74% (company only) based on risk level
-- - BPJS JKM: 0.3% (company only) of salary
-- - BPJS JP: 3% (2% company, 1% employee) of salary, max ceiling 10.042.300

-- EARNINGS (Pendapatan)
INSERT INTO salary_components (id, code, name, description, component_type, is_percentage, default_rate, default_amount, is_system, display_order, is_taxable, bpjs_category, active) VALUES
('c0000000-0000-0000-0000-000000000001', 'GAPOK', 'Gaji Pokok', 'Gaji pokok bulanan karyawan', 'EARNING', FALSE, NULL, NULL, TRUE, 10, TRUE, NULL, TRUE),
('c0000000-0000-0000-0000-000000000002', 'TJ-TRANS', 'Tunjangan Transportasi', 'Tunjangan transportasi bulanan', 'EARNING', FALSE, NULL, 500000, FALSE, 20, TRUE, NULL, TRUE),
('c0000000-0000-0000-0000-000000000003', 'TJ-MAKAN', 'Tunjangan Makan', 'Tunjangan makan bulanan', 'EARNING', FALSE, NULL, 500000, FALSE, 30, TRUE, NULL, TRUE),
('c0000000-0000-0000-0000-000000000004', 'TJ-POSISI', 'Tunjangan Jabatan', 'Tunjangan berdasarkan posisi/jabatan', 'EARNING', FALSE, NULL, NULL, FALSE, 40, TRUE, NULL, TRUE),
('c0000000-0000-0000-0000-000000000005', 'LEMBUR', 'Uang Lembur', 'Pembayaran lembur sesuai perhitungan', 'EARNING', FALSE, NULL, NULL, FALSE, 50, TRUE, NULL, TRUE),
('c0000000-0000-0000-0000-000000000006', 'BONUS', 'Bonus', 'Bonus kinerja atau prestasi', 'EARNING', FALSE, NULL, NULL, FALSE, 60, TRUE, NULL, TRUE),
('c0000000-0000-0000-0000-000000000007', 'THR', 'Tunjangan Hari Raya', 'THR sesuai ketentuan pemerintah', 'EARNING', FALSE, NULL, NULL, FALSE, 70, TRUE, NULL, TRUE);

-- COMPANY CONTRIBUTIONS (Kontribusi Perusahaan)
-- default_rate stores percentage as entered (e.g., 4.0 for 4%), calculation divides by 100 in Java
INSERT INTO salary_components (id, code, name, description, component_type, is_percentage, default_rate, default_amount, is_system, display_order, is_taxable, bpjs_category, active) VALUES
('c0000000-0000-0000-0000-000000000011', 'BPJS-KES-P', 'BPJS Kesehatan (Perusahaan)', 'Iuran BPJS Kesehatan ditanggung perusahaan (4%)', 'COMPANY_CONTRIBUTION', TRUE, 4.0, NULL, TRUE, 110, FALSE, 'KESEHATAN', TRUE),
('c0000000-0000-0000-0000-000000000012', 'BPJS-JHT-P', 'BPJS JHT (Perusahaan)', 'Iuran BPJS Jaminan Hari Tua ditanggung perusahaan (3.7%)', 'COMPANY_CONTRIBUTION', TRUE, 3.7, NULL, TRUE, 120, FALSE, 'JHT', TRUE),
('c0000000-0000-0000-0000-000000000013', 'BPJS-JKK', 'BPJS JKK', 'Iuran BPJS Jaminan Kecelakaan Kerja (0.24% - risk level I)', 'COMPANY_CONTRIBUTION', TRUE, 0.24, NULL, TRUE, 130, FALSE, 'JKK', TRUE),
('c0000000-0000-0000-0000-000000000014', 'BPJS-JKM', 'BPJS JKM', 'Iuran BPJS Jaminan Kematian (0.3%)', 'COMPANY_CONTRIBUTION', TRUE, 0.3, NULL, TRUE, 140, FALSE, 'JKM', TRUE),
('c0000000-0000-0000-0000-000000000015', 'BPJS-JP-P', 'BPJS JP (Perusahaan)', 'Iuran BPJS Jaminan Pensiun ditanggung perusahaan (2%)', 'COMPANY_CONTRIBUTION', TRUE, 2.0, NULL, TRUE, 150, FALSE, 'JP', TRUE);

-- DEDUCTIONS (Potongan)
-- default_rate stores percentage as entered (e.g., 1.0 for 1%), calculation divides by 100 in Java
INSERT INTO salary_components (id, code, name, description, component_type, is_percentage, default_rate, default_amount, is_system, display_order, is_taxable, bpjs_category, active) VALUES
('c0000000-0000-0000-0000-000000000021', 'BPJS-KES-K', 'BPJS Kesehatan (Karyawan)', 'Iuran BPJS Kesehatan ditanggung karyawan (1%)', 'DEDUCTION', TRUE, 1.0, NULL, TRUE, 210, FALSE, 'KESEHATAN', TRUE),
('c0000000-0000-0000-0000-000000000022', 'BPJS-JHT-K', 'BPJS JHT (Karyawan)', 'Iuran BPJS Jaminan Hari Tua ditanggung karyawan (2%)', 'DEDUCTION', TRUE, 2.0, NULL, TRUE, 220, FALSE, 'JHT', TRUE),
('c0000000-0000-0000-0000-000000000023', 'BPJS-JP-K', 'BPJS JP (Karyawan)', 'Iuran BPJS Jaminan Pensiun ditanggung karyawan (1%)', 'DEDUCTION', TRUE, 1.0, NULL, TRUE, 230, FALSE, 'JP', TRUE),
('c0000000-0000-0000-0000-000000000024', 'PPH21', 'PPh Pasal 21', 'Pajak Penghasilan Pasal 21', 'DEDUCTION', FALSE, NULL, NULL, TRUE, 240, FALSE, NULL, TRUE),
('c0000000-0000-0000-0000-000000000025', 'POT-LAIN', 'Potongan Lain-lain', 'Potongan lainnya (pinjaman, dll)', 'DEDUCTION', FALSE, NULL, NULL, FALSE, 250, FALSE, NULL, TRUE);

-- ============================================
-- Additional Fixed Asset Accounts
-- ============================================

-- Add more fixed asset accounts to support Phase 4
INSERT INTO chart_of_accounts (id, account_code, account_name, account_type, normal_balance, id_parent, level, is_header, active, is_permanent) VALUES
-- Aset Tetap - Kendaraan
('10000000-0000-0000-0000-000000000141', '1.2.03', 'Kendaraan', 'ASSET', 'DEBIT', '10000000-0000-0000-0000-000000000012', 3, FALSE, TRUE, TRUE),
('10000000-0000-0000-0000-000000000142', '1.2.04', 'Akum. Penyusutan Kendaraan', 'ASSET', 'CREDIT', '10000000-0000-0000-0000-000000000012', 3, FALSE, TRUE, TRUE),
-- Aset Tetap - Peralatan Kantor
('10000000-0000-0000-0000-000000000143', '1.2.05', 'Peralatan Kantor', 'ASSET', 'DEBIT', '10000000-0000-0000-0000-000000000012', 3, FALSE, TRUE, TRUE),
('10000000-0000-0000-0000-000000000144', '1.2.06', 'Akum. Penyusutan Peralatan Kantor', 'ASSET', 'CREDIT', '10000000-0000-0000-0000-000000000012', 3, FALSE, TRUE, TRUE),
-- Aset Tetap - Mesin
('10000000-0000-0000-0000-000000000145', '1.2.07', 'Mesin', 'ASSET', 'DEBIT', '10000000-0000-0000-0000-000000000012', 3, FALSE, TRUE, TRUE),
('10000000-0000-0000-0000-000000000146', '1.2.08', 'Akum. Penyusutan Mesin', 'ASSET', 'CREDIT', '10000000-0000-0000-0000-000000000012', 3, FALSE, TRUE, TRUE);

-- Add Gain/Loss on Asset Disposal accounts
INSERT INTO chart_of_accounts (id, account_code, account_name, account_type, normal_balance, id_parent, level, is_header, active, is_permanent) VALUES
('40000000-0000-0000-0000-000000000122', '4.2.02', 'Laba Penjualan Aset', 'REVENUE', 'CREDIT', '40000000-0000-0000-0000-000000000012', 3, FALSE, TRUE, FALSE),
('50000000-0000-0000-0000-000000000122', '5.2.02', 'Rugi Penjualan Aset', 'EXPENSE', 'DEBIT', '50000000-0000-0000-0000-000000000012', 3, FALSE, TRUE, FALSE);

-- ============================================
-- Asset Categories
-- ============================================

INSERT INTO asset_categories (id, code, name, description, depreciation_method, useful_life_months, depreciation_rate, id_asset_account, id_accumulated_depreciation_account, id_depreciation_expense_account, active) VALUES
-- Peralatan Komputer - 4 years useful life
('a0000000-0000-0000-0000-000000000001', 'KOMPUTER', 'Peralatan Komputer', 'Laptop, desktop, monitor, printer, dan perangkat keras IT lainnya', 'STRAIGHT_LINE', 48, NULL, '10000000-0000-0000-0000-000000000121', '10000000-0000-0000-0000-000000000122', '50000000-0000-0000-0000-000000000107', TRUE),
-- Kendaraan - 8 years useful life
('a0000000-0000-0000-0000-000000000002', 'KENDARAAN', 'Kendaraan', 'Mobil, motor, dan alat transportasi perusahaan', 'STRAIGHT_LINE', 96, NULL, '10000000-0000-0000-0000-000000000141', '10000000-0000-0000-0000-000000000142', '50000000-0000-0000-0000-000000000107', TRUE),
-- Peralatan Kantor - 4 years useful life
('a0000000-0000-0000-0000-000000000003', 'PERALATAN', 'Peralatan Kantor', 'Meja, kursi, lemari, dan furnitur kantor lainnya', 'STRAIGHT_LINE', 48, NULL, '10000000-0000-0000-0000-000000000143', '10000000-0000-0000-0000-000000000144', '50000000-0000-0000-0000-000000000107', TRUE),
-- Mesin - 8 years useful life with declining balance
('a0000000-0000-0000-0000-000000000004', 'MESIN', 'Mesin', 'Mesin produksi dan peralatan berat', 'DECLINING_BALANCE', 96, 25.00, '10000000-0000-0000-0000-000000000145', '10000000-0000-0000-0000-000000000146', '50000000-0000-0000-0000-000000000107', TRUE);

-- ============================================
-- Fixed Asset Journal Templates (Phase 4)
-- ============================================

-- Template: Pembelian Aset Tetap (user template - general purpose)
-- Uses: assetCost for purchase amount
INSERT INTO journal_templates (id, template_name, category, cash_flow_category, template_type, description, is_system, active) VALUES
('e0000000-0000-0000-0000-000000000015', 'Pembelian Aset Tetap', 'EXPENSE', 'INVESTING', 'SIMPLE', 'Template untuk mencatat pembelian aset tetap. Variabel: assetCost', FALSE, TRUE);

INSERT INTO journal_template_lines (id, id_journal_template, id_account, position, formula, line_order, description, account_hint) VALUES
-- Debit: Asset account (use account_hint for dynamic selection)
('e1000000-0000-0000-0000-000000000032', 'e0000000-0000-0000-0000-000000000015', NULL, 'DEBIT', 'assetCost', 1, 'Aset tetap yang dibeli', 'ASET_TETAP'),
-- Credit: Bank/Cash
('e1000000-0000-0000-0000-000000000033', 'e0000000-0000-0000-0000-000000000015', '10000000-0000-0000-0000-000000000102', 'CREDIT', 'assetCost', 2, 'Pembayaran dari bank', NULL);

-- Template: Penyusutan Aset Tetap (Asset Depreciation)
-- Uses: depreciationAmount for monthly depreciation
INSERT INTO journal_templates (id, template_name, category, cash_flow_category, template_type, description, is_system, active) VALUES
('e0000000-0000-0000-0000-000000000016', 'Penyusutan Aset Tetap', 'EXPENSE', 'OPERATING', 'SIMPLE', 'Template untuk mencatat penyusutan aset tetap bulanan. Variabel: depreciationAmount', TRUE, TRUE);

INSERT INTO journal_template_lines (id, id_journal_template, id_account, position, formula, line_order, description, account_hint) VALUES
-- Debit: Depreciation expense
('e1000000-0000-0000-0000-000000000034', 'e0000000-0000-0000-0000-000000000016', '50000000-0000-0000-0000-000000000107', 'DEBIT', 'depreciationAmount', 1, 'Beban penyusutan', NULL),
-- Credit: Accumulated depreciation (use account_hint for dynamic selection)
('e1000000-0000-0000-0000-000000000035', 'e0000000-0000-0000-0000-000000000016', NULL, 'CREDIT', 'depreciationAmount', 2, 'Akumulasi penyusutan', 'AKUM_PENYUSUTAN');

-- Template: Pelepasan Aset Tetap (Asset Disposal)
-- Uses: bookValue, disposalProceeds, gainLoss
-- Note: This is a complex template, actual accounts depend on gain/loss
INSERT INTO journal_templates (id, template_name, category, cash_flow_category, template_type, description, is_system, active) VALUES
('e0000000-0000-0000-0000-000000000017', 'Pelepasan Aset Tetap', 'INCOME', 'INVESTING', 'DETAILED', 'Template untuk mencatat pelepasan/penjualan aset tetap. Variabel: bookValue, accumulatedDepreciation, assetCost, disposalProceeds, gainLoss', TRUE, TRUE);

INSERT INTO journal_template_lines (id, id_journal_template, id_account, position, formula, line_order, description, account_hint) VALUES
-- Debit: Bank/Cash (proceeds received)
('e1000000-0000-0000-0000-000000000036', 'e0000000-0000-0000-0000-000000000017', '10000000-0000-0000-0000-000000000102', 'DEBIT', 'disposalProceeds', 1, 'Penerimaan dari penjualan', NULL),
-- Debit: Accumulated depreciation (reverse accumulated)
('e1000000-0000-0000-0000-000000000037', 'e0000000-0000-0000-0000-000000000017', NULL, 'DEBIT', 'accumulatedDepreciation', 2, 'Hapus akumulasi penyusutan', 'AKUM_PENYUSUTAN'),
-- Debit: Loss on disposal (if loss, else 0)
('e1000000-0000-0000-0000-000000000038', 'e0000000-0000-0000-0000-000000000017', '50000000-0000-0000-0000-000000000122', 'DEBIT', 'gainLoss < 0 ? -gainLoss : 0', 3, 'Rugi penjualan aset', NULL),
-- Credit: Asset account (remove asset cost)
('e1000000-0000-0000-0000-000000000039', 'e0000000-0000-0000-0000-000000000017', NULL, 'CREDIT', 'assetCost', 4, 'Hapus nilai perolehan aset', 'ASET_TETAP'),
-- Credit: Gain on disposal (if gain, else 0)
('e1000000-0000-0000-0000-000000000040', 'e0000000-0000-0000-0000-000000000017', '40000000-0000-0000-0000-000000000122', 'CREDIT', 'gainLoss > 0 ? gainLoss : 0', 5, 'Laba penjualan aset', NULL);

-- ============================================
-- Inventory Chart of Accounts (Phase 5)
-- ============================================

-- Header: Persediaan under Aset Lancar (1.1)
INSERT INTO chart_of_accounts (id, account_code, account_name, account_type, normal_balance, id_parent, level, is_header, active, is_permanent) VALUES
('10000000-0000-0000-0000-000000000014', '1.4', 'Persediaan', 'ASSET', 'DEBIT', '10000000-0000-0000-0000-000000000001', 2, TRUE, TRUE, TRUE);

-- Inventory accounts
INSERT INTO chart_of_accounts (id, account_code, account_name, account_type, normal_balance, id_parent, level, is_header, active, is_permanent) VALUES
('10000000-0000-0000-0000-000000000151', '1.4.01', 'Persediaan Barang Dagangan', 'ASSET', 'DEBIT', '10000000-0000-0000-0000-000000000014', 3, FALSE, TRUE, TRUE),
('10000000-0000-0000-0000-000000000152', '1.4.02', 'Persediaan Bahan Baku', 'ASSET', 'DEBIT', '10000000-0000-0000-0000-000000000014', 3, FALSE, TRUE, TRUE),
('10000000-0000-0000-0000-000000000153', '1.4.03', 'Persediaan Barang Dalam Proses', 'ASSET', 'DEBIT', '10000000-0000-0000-0000-000000000014', 3, FALSE, TRUE, TRUE),
('10000000-0000-0000-0000-000000000154', '1.4.04', 'Persediaan Barang Jadi', 'ASSET', 'DEBIT', '10000000-0000-0000-0000-000000000014', 3, FALSE, TRUE, TRUE);

-- HPP (Harga Pokok Penjualan / COGS) accounts
INSERT INTO chart_of_accounts (id, account_code, account_name, account_type, normal_balance, id_parent, level, is_header, active, is_permanent) VALUES
('50000000-0000-0000-0000-000000000013', '5.3', 'Harga Pokok Penjualan', 'EXPENSE', 'DEBIT', '50000000-0000-0000-0000-000000000001', 2, TRUE, TRUE, FALSE);

INSERT INTO chart_of_accounts (id, account_code, account_name, account_type, normal_balance, id_parent, level, is_header, active, is_permanent) VALUES
('50000000-0000-0000-0000-000000000131', '5.3.01', 'HPP Barang Dagangan', 'EXPENSE', 'DEBIT', '50000000-0000-0000-0000-000000000013', 3, FALSE, TRUE, FALSE),
('50000000-0000-0000-0000-000000000132', '5.3.02', 'HPP Produksi', 'EXPENSE', 'DEBIT', '50000000-0000-0000-0000-000000000013', 3, FALSE, TRUE, FALSE);

-- Sales Revenue for inventory products
INSERT INTO chart_of_accounts (id, account_code, account_name, account_type, normal_balance, id_parent, level, is_header, active, is_permanent) VALUES
('40000000-0000-0000-0000-000000000104', '4.1.04', 'Pendapatan Penjualan Barang', 'REVENUE', 'CREDIT', '40000000-0000-0000-0000-000000000011', 3, FALSE, TRUE, FALSE);

-- ============================================
-- Inventory Journal Templates (Phase 5)
-- ============================================

-- Template: Pembelian Persediaan (Inventory Purchase)
-- Uses: inventoryAccount (dynamic), amount (purchase cost)
INSERT INTO journal_templates (id, template_name, category, cash_flow_category, template_type, description, is_system, active) VALUES
('f5000000-0000-0000-0000-000000000001', 'Pembelian Persediaan', 'EXPENSE', 'OPERATING', 'SIMPLE', 'Template untuk mencatat pembelian persediaan/barang. Variabel: amount (total pembelian)', TRUE, TRUE);

INSERT INTO journal_template_lines (id, id_journal_template, id_account, position, formula, line_order, description, account_hint) VALUES
-- Debit: Inventory account (dynamic from product)
('f5100000-0000-0000-0000-000000000001', 'f5000000-0000-0000-0000-000000000001', NULL, 'DEBIT', 'amount', 1, 'Persediaan masuk', 'PERSEDIAAN'),
-- Credit: Bank/Cash
('f5100000-0000-0000-0000-000000000002', 'f5000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000102', 'CREDIT', 'amount', 2, 'Pembayaran dari bank', NULL);

-- Template: Penjualan Persediaan (Inventory Sale - COGS)
-- Uses: cogsAmount (cost of goods sold), revenueAmount (selling price), inventoryAccount, cogsAccount, salesAccount (all dynamic)
INSERT INTO journal_templates (id, template_name, category, cash_flow_category, template_type, description, is_system, active) VALUES
('f5000000-0000-0000-0000-000000000002', 'Penjualan Persediaan', 'INCOME', 'OPERATING', 'DETAILED', 'Template untuk mencatat penjualan persediaan dengan HPP. Variabel: cogsAmount, revenueAmount', TRUE, TRUE);

INSERT INTO journal_template_lines (id, id_journal_template, id_account, position, formula, line_order, description, account_hint) VALUES
-- Debit: Bank/Cash (revenue received)
('f5100000-0000-0000-0000-000000000003', 'f5000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000102', 'DEBIT', 'revenueAmount', 1, 'Penerimaan penjualan', NULL),
-- Debit: COGS (cost of goods sold)
('f5100000-0000-0000-0000-000000000004', 'f5000000-0000-0000-0000-000000000002', NULL, 'DEBIT', 'cogsAmount', 2, 'Harga pokok penjualan', 'HPP'),
-- Credit: Sales Revenue
('f5100000-0000-0000-0000-000000000005', 'f5000000-0000-0000-0000-000000000002', NULL, 'CREDIT', 'revenueAmount', 3, 'Pendapatan penjualan', 'PENJUALAN'),
-- Credit: Inventory (reduce inventory)
('f5100000-0000-0000-0000-000000000006', 'f5000000-0000-0000-0000-000000000002', NULL, 'CREDIT', 'cogsAmount', 4, 'Persediaan keluar', 'PERSEDIAAN');

-- Template: Penyesuaian Persediaan Masuk (Inventory Adjustment In)
-- Uses: amount (adjustment value)
INSERT INTO journal_templates (id, template_name, category, cash_flow_category, template_type, description, is_system, active) VALUES
('f5000000-0000-0000-0000-000000000003', 'Penyesuaian Persediaan Masuk', 'EXPENSE', 'OPERATING', 'SIMPLE', 'Template untuk mencatat penyesuaian persediaan masuk (stock opname lebih). Variabel: amount', TRUE, TRUE);

INSERT INTO journal_template_lines (id, id_journal_template, id_account, position, formula, line_order, description, account_hint) VALUES
-- Debit: Inventory account
('f5100000-0000-0000-0000-000000000007', 'f5000000-0000-0000-0000-000000000003', NULL, 'DEBIT', 'amount', 1, 'Penyesuaian persediaan masuk', 'PERSEDIAAN'),
-- Credit: Other Income (inventory gain)
('f5100000-0000-0000-0000-000000000008', 'f5000000-0000-0000-0000-000000000003', '40000000-0000-0000-0000-000000000121', 'CREDIT', 'amount', 2, 'Pendapatan penyesuaian persediaan', NULL);

-- Template: Penyesuaian Persediaan Keluar (Inventory Adjustment Out)
-- Uses: amount (adjustment value)
INSERT INTO journal_templates (id, template_name, category, cash_flow_category, template_type, description, is_system, active) VALUES
('f5000000-0000-0000-0000-000000000004', 'Penyesuaian Persediaan Keluar', 'EXPENSE', 'OPERATING', 'SIMPLE', 'Template untuk mencatat penyesuaian persediaan keluar (stock opname kurang). Variabel: amount', TRUE, TRUE);

INSERT INTO journal_template_lines (id, id_journal_template, id_account, position, formula, line_order, description, account_hint) VALUES
-- Debit: Inventory shrinkage expense
('f5100000-0000-0000-0000-000000000009', 'f5000000-0000-0000-0000-000000000004', '50000000-0000-0000-0000-000000000121', 'DEBIT', 'amount', 1, 'Beban penyesuaian persediaan', NULL),
-- Credit: Inventory account
('f5100000-0000-0000-0000-000000000010', 'f5000000-0000-0000-0000-000000000004', NULL, 'CREDIT', 'amount', 2, 'Penyesuaian persediaan keluar', 'PERSEDIAAN');

-- ============================================
-- SYSTEM TEMPLATE: Manual Journal Entry
-- ============================================
-- Template: Jurnal Manual (Manual Journal Entry)
-- This template allows users to create custom one-off journal entries
-- by selecting both debit and credit accounts at transaction time.
INSERT INTO journal_templates (id, template_name, category, cash_flow_category, template_type, description, is_system, active) VALUES
('e0000000-0000-0000-0000-000000000099', 'Jurnal Manual', 'TRANSFER', 'OPERATING', 'SIMPLE', 'Template untuk jurnal manual. Pilih akun debit dan kredit saat membuat transaksi.', FALSE, TRUE);

-- Template lines for Jurnal Manual - both accounts are selectable
INSERT INTO journal_template_lines (id, id_journal_template, id_account, position, formula, line_order, description, account_hint) VALUES
-- Debit line: user selects account
('e0100000-0000-0000-0000-000000000001', 'e0000000-0000-0000-0000-000000000099', NULL, 'DEBIT', 'amount', 1, 'Akun yang didebit', 'Akun Debit'),
-- Credit line: user selects account
('e0100000-0000-0000-0000-000000000002', 'e0000000-0000-0000-0000-000000000099', NULL, 'CREDIT', 'amount', 2, 'Akun yang dikredit', 'Akun Kredit');

-- ============================================
-- SYSTEM TEMPLATE: Fiscal Year Closing
-- ============================================
-- Template: Jurnal Penutup (Fiscal Year Closing)
-- This is a special system template used for fiscal year closing entries.
-- It has no template lines - entries are system-generated based on account balances.
INSERT INTO journal_templates (id, template_name, category, cash_flow_category, template_type, description, is_system, active) VALUES
('e0000000-0000-0000-0000-000000000098', 'Jurnal Penutup Tahun', 'TRANSFER', 'OPERATING', 'SIMPLE', 'Template sistem untuk jurnal penutup akhir tahun. Baris debit/kredit dibuat otomatis berdasarkan saldo akun.', TRUE, TRUE);
-- Note: No template lines for CLOSING template - lines are system-generated
