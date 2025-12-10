-- V811: Service Industry Transactions
-- PKP Tax Transactions: PPN Keluaran, PPh 23 Withholding, PPN Masukan
-- ID Convention: 51=Service, 4x=Transaction, 7x=Journal Entry, 8x=Payroll

-- ============================================
-- Transactions - January 2024
-- ============================================

-- TRX-001: Service revenue from Bank Mandiri (PRJ-2024-001 Milestone 1)
-- Invoice: 180,000,000 + PPN 11% = 199,800,000
-- PPh 23 withheld by client: 2% x 180,000,000 = 3,600,000
-- Cash received: 199,800,000 - 3,600,000 = 196,200,000
INSERT INTO transactions (id, transaction_number, transaction_date, id_journal_template, description, reference_number, id_project, amount, status, posted_at, posted_by, created_at, created_by)
VALUES ('51400001-0000-0000-0000-000000000001', 'TRX-2024-0001', '2024-01-15', 'e0000000-0000-0000-0000-000000000001', 'Konsultasi Core Banking - Milestone 1 (Requirement Analysis)', 'INV-2024-001', '51200001-0000-0000-0000-000000000001', 196200000, 'POSTED', NOW(), 'admin', NOW(), 'admin');

-- Journal entries for TRX-001 (each line is a separate row)
INSERT INTO journal_entries (id, id_transaction, id_account, debit_amount, credit_amount, created_at, created_by)
VALUES
    ('51700001-0000-0000-0000-000000000001', '51400001-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000102', 196200000, 0, NOW(), 'admin'),
    ('51700002-0000-0000-0000-000000000001', '51400001-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000125', 3600000, 0, NOW(), 'admin'),
    ('51700003-0000-0000-0000-000000000001', '51400001-0000-0000-0000-000000000001', '40000000-0000-0000-0000-000000000101', 0, 180000000, NOW(), 'admin'),
    ('51700004-0000-0000-0000-000000000001', '51400001-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000103', 0, 19800000, NOW(), 'admin');

-- TRX-002: Service revenue from Bank Mandiri (PRJ-2024-002 Training)
-- Invoice: 150,000,000 + PPN 11% = 166,500,000
-- PPh 23 withheld: 2% x 150,000,000 = 3,000,000
-- Cash received: 166,500,000 - 3,000,000 = 163,500,000
INSERT INTO transactions (id, transaction_number, transaction_date, id_journal_template, description, reference_number, id_project, amount, status, posted_at, posted_by, created_at, created_by)
VALUES ('51400002-0000-0000-0000-000000000001', 'TRX-2024-0002', '2024-02-28', 'e0000000-0000-0000-0000-000000000001', 'IT Security Training - Full Payment', 'INV-2024-002', '51200002-0000-0000-0000-000000000001', 163500000, 'POSTED', NOW(), 'admin', NOW(), 'admin');

INSERT INTO journal_entries (id, id_transaction, id_account, debit_amount, credit_amount, created_at, created_by)
VALUES
    ('51700005-0000-0000-0000-000000000001', '51400002-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000102', 163500000, 0, NOW(), 'admin'),
    ('51700006-0000-0000-0000-000000000001', '51400002-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000125', 3000000, 0, NOW(), 'admin'),
    ('51700007-0000-0000-0000-000000000001', '51400002-0000-0000-0000-000000000001', '40000000-0000-0000-0000-000000000103', 0, 150000000, NOW(), 'admin'),
    ('51700008-0000-0000-0000-000000000001', '51400002-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000103', 0, 16500000, NOW(), 'admin');

-- TRX-003: Operating expense - Server & Cloud (with PPN Masukan)
-- AWS Invoice: 5,000,000 + PPN 11% = 5,550,000
INSERT INTO transactions (id, transaction_number, transaction_date, id_journal_template, description, reference_number, id_project, amount, status, posted_at, posted_by, created_at, created_by)
VALUES ('51400003-0000-0000-0000-000000000001', 'TRX-2024-0003', '2024-01-31', 'e0000000-0000-0000-0000-000000000005', 'AWS Cloud Services Jan 2024', 'AWS-2024-001', NULL, 5550000, 'POSTED', NOW(), 'admin', NOW(), 'admin');

INSERT INTO journal_entries (id, id_transaction, id_account, debit_amount, credit_amount, created_at, created_by)
VALUES
    ('51700009-0000-0000-0000-000000000001', '51400003-0000-0000-0000-000000000001', '50000000-0000-0000-0000-000000000102', 5000000, 0, NOW(), 'admin'),
    ('51700010-0000-0000-0000-000000000001', '51400003-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000125', 550000, 0, NOW(), 'admin'),
    ('51700011-0000-0000-0000-000000000001', '51400003-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000102', 0, 5550000, NOW(), 'admin');

-- TRX-004: Operating expense - Software License (with PPN Masukan)
-- JetBrains IntelliJ: 3,000,000 + PPN 11% = 3,330,000
INSERT INTO transactions (id, transaction_number, transaction_date, id_journal_template, description, reference_number, id_project, amount, status, posted_at, posted_by, created_at, created_by)
VALUES ('51400004-0000-0000-0000-000000000001', 'TRX-2024-0004', '2024-01-15', 'e0000000-0000-0000-0000-000000000006', 'JetBrains IntelliJ License 2024', 'JB-2024-001', NULL, 3330000, 'POSTED', NOW(), 'admin', NOW(), 'admin');

INSERT INTO journal_entries (id, id_transaction, id_account, debit_amount, credit_amount, created_at, created_by)
VALUES
    ('51700012-0000-0000-0000-000000000001', '51400004-0000-0000-0000-000000000001', '50000000-0000-0000-0000-000000000103', 3000000, 0, NOW(), 'admin'),
    ('51700013-0000-0000-0000-000000000001', '51400004-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000125', 330000, 0, NOW(), 'admin'),
    ('51700014-0000-0000-0000-000000000001', '51400004-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000102', 0, 3330000, NOW(), 'admin');

-- TRX-005: Capital injection
INSERT INTO transactions (id, transaction_number, transaction_date, id_journal_template, description, reference_number, id_project, amount, status, posted_at, posted_by, created_at, created_by)
VALUES ('51400005-0000-0000-0000-000000000001', 'TRX-2024-0005', '2024-01-01', 'e0000000-0000-0000-0000-000000000012', 'Setoran Modal Awal 2024', 'CAP-2024-001', NULL, 500000000, 'POSTED', NOW(), 'admin', NOW(), 'admin');

INSERT INTO journal_entries (id, id_transaction, id_account, debit_amount, credit_amount, created_at, created_by)
VALUES
    ('51700015-0000-0000-0000-000000000001', '51400005-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000102', 500000000, 0, NOW(), 'admin'),
    ('51700016-0000-0000-0000-000000000001', '51400005-0000-0000-0000-000000000001', '30000000-0000-0000-0000-000000000101', 0, 500000000, NOW(), 'admin');

-- ============================================
-- Payroll Runs
-- ============================================
INSERT INTO payroll_runs (id, payroll_period, period_start, period_end, status, total_gross, total_deductions, total_net_pay, employee_count, notes)
VALUES
    ('51800001-0000-0000-0000-000000000001', '2024-01', '2024-01-01', '2024-01-31', 'POSTED', 31000000, 4650000, 26350000, 3, 'Payroll Januari 2024'),
    ('51800002-0000-0000-0000-000000000001', '2024-02', '2024-02-01', '2024-02-29', 'POSTED', 31000000, 4650000, 26350000, 3, 'Payroll Februari 2024');

-- ============================================
-- Payroll Details (January 2024)
-- ============================================
-- Budi Santoso: Gapok 15M + Tunjangan 2M = 17M gross
INSERT INTO payroll_details (id, id_payroll_run, id_employee, base_salary, gross_salary, total_deductions, net_pay, pph21, bpjs_kes_employee, bpjs_jht_employee, bpjs_jp_employee, bpjs_kes_company, bpjs_jht_company, bpjs_jkk, bpjs_jkm, bpjs_jp_company)
VALUES ('51810001-0000-0000-0000-000000000001', '51800001-0000-0000-0000-000000000001', '51300001-0000-0000-0000-000000000001', 15000000, 17000000, 2700000, 14300000, 500000, 180000, 360000, 180000, 720000, 666000, 43200, 54000, 360000);

-- Dewi Lestari: Gapok 10M + Tunjangan 1M = 11M gross
INSERT INTO payroll_details (id, id_payroll_run, id_employee, base_salary, gross_salary, total_deductions, net_pay, pph21, bpjs_kes_employee, bpjs_jht_employee, bpjs_jp_employee, bpjs_kes_company, bpjs_jht_company, bpjs_jkk, bpjs_jkm, bpjs_jp_company)
VALUES ('51810002-0000-0000-0000-000000000001', '51800001-0000-0000-0000-000000000001', '51300002-0000-0000-0000-000000000001', 10000000, 11000000, 1800000, 9200000, 250000, 120000, 240000, 120000, 480000, 444000, 28800, 36000, 240000);

-- Agus Wijaya: Gapok 5M (TK/0, lower PPh 21)
INSERT INTO payroll_details (id, id_payroll_run, id_employee, base_salary, gross_salary, total_deductions, net_pay, pph21, bpjs_kes_employee, bpjs_jht_employee, bpjs_jp_employee, bpjs_kes_company, bpjs_jht_company, bpjs_jkk, bpjs_jkm, bpjs_jp_company)
VALUES ('51810003-0000-0000-0000-000000000001', '51800001-0000-0000-0000-000000000001', '51300003-0000-0000-0000-000000000001', 5000000, 5000000, 900000, 4100000, 50000, 60000, 120000, 60000, 240000, 222000, 14400, 18000, 120000);

-- February 2024 Payroll (same structure)
INSERT INTO payroll_details (id, id_payroll_run, id_employee, base_salary, gross_salary, total_deductions, net_pay, pph21, bpjs_kes_employee, bpjs_jht_employee, bpjs_jp_employee, bpjs_kes_company, bpjs_jht_company, bpjs_jkk, bpjs_jkm, bpjs_jp_company)
VALUES
    ('51810004-0000-0000-0000-000000000001', '51800002-0000-0000-0000-000000000001', '51300001-0000-0000-0000-000000000001', 15000000, 17000000, 2700000, 14300000, 500000, 180000, 360000, 180000, 720000, 666000, 43200, 54000, 360000),
    ('51810005-0000-0000-0000-000000000001', '51800002-0000-0000-0000-000000000001', '51300002-0000-0000-0000-000000000001', 10000000, 11000000, 1800000, 9200000, 250000, 120000, 240000, 120000, 480000, 444000, 28800, 36000, 240000),
    ('51810006-0000-0000-0000-000000000001', '51800002-0000-0000-0000-000000000001', '51300003-0000-0000-0000-000000000001', 5000000, 5000000, 900000, 4100000, 50000, 60000, 120000, 60000, 240000, 222000, 14400, 18000, 120000);

-- ============================================
-- Tax Deadline Completions
-- ============================================
INSERT INTO tax_deadline_completions (id, id_tax_deadline, year, month, completed_date, completed_by, reference_number, notes)
VALUES
    ('51900001-0000-0000-0000-000000000001', 'f0000000-0000-0000-0000-000000000001', 2024, 1, '2024-02-08', 'admin', 'NTPN-001-2024', 'Setor PPh 21 Januari 2024'),
    ('51900002-0000-0000-0000-000000000001', 'f0000000-0000-0000-0000-000000000006', 2024, 1, '2024-02-18', 'admin', 'SPT-001-2024', 'Lapor SPT PPh 21 Januari 2024');

-- ============================================
-- Update Transaction Sequences
-- ============================================
UPDATE transaction_sequences SET last_number = 5 WHERE sequence_type = 'TRANSACTION' AND year = 2024;
UPDATE transaction_sequences SET last_number = 5 WHERE sequence_type = 'JOURNAL' AND year = 2024;
