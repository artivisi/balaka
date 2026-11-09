-- V901: Report Test Data
-- Test data for Basic Reports (1.3) - Trial Balance, General Ledger, Balance Sheet, Income Statement
-- This migration runs only in test profile

-- =============================================================================
-- TEST DATA SUMMARY
-- =============================================================================
-- Jan 2024: Capital injection + Equipment purchase
-- Feb 2024: Consulting revenue + Salary expense
-- Mar 2024: Development revenue + Cloud expense
-- Apr 2024: Consulting revenue + VOID entry (should be excluded)
-- May 2024: Salary expense
-- Jun 2024: Equipment on credit + Depreciation
--
-- VOID entry: JRN-2024-0008 should NOT be included in calculations

-- =============================================================================
-- EXPECTED TOTALS (for test assertions)
-- =============================================================================
-- TRIAL BALANCE:
--   Total Debits  = 163,000,000
--   Total Credits = 163,000,000
--
-- BALANCE SHEET (as of 2024-06-30):
--   Total Assets      = 143,000,000 (Cash 84M + BCA 30M + Peralatan 30M - Akum 1M)
--   Total Liabilities =  10,000,000 (Hutang Usaha)
--   Total Equity      = 100,000,000 (Modal Disetor)
--   Net Income        =  33,000,000 (Revenue 52M - Expense 19M)
--   A = L + E + NI    = 143,000,000 ✓
--
-- INCOME STATEMENT (2024-01-01 to 2024-06-30):
--   Total Revenue  = 52,000,000 (Konsultasi 27M + Development 25M)
--   Total Expense  = 19,000,000 (Gaji 16M + Server 2M + Penyusutan 1M)
--   Net Income     = 33,000,000

-- =============================================================================
-- ACCOUNT IDs (from V002 seed data)
-- =============================================================================
-- Assets:
--   Cash (1.1.01)                    : 10000000-0000-0000-0000-000000000101
--   Bank BCA (1.1.02)                : 10000000-0000-0000-0000-000000000102
--   Peralatan Komputer (1.2.01)      : 10000000-0000-0000-0000-000000000121
--   Akum. Peny. Peralatan (1.2.02)   : 10000000-0000-0000-0000-000000000122
--
-- Liabilities:
--   Hutang Usaha (2.1.01)            : 20000000-0000-0000-0000-000000000101
--
-- Equity:
--   Modal Disetor (3.1.01)           : 30000000-0000-0000-0000-000000000101
--
-- Revenue:
--   Pendapatan Jasa Konsultasi (4.1.01) : 40000000-0000-0000-0000-000000000101
--   Pendapatan Jasa Development (4.1.02): 40000000-0000-0000-0000-000000000102
--
-- Expense:
--   Beban Gaji (5.1.01)              : 50000000-0000-0000-0000-000000000101
--   Beban Server & Cloud (5.1.02)    : 50000000-0000-0000-0000-000000000102
--   Beban Penyusutan (5.1.07)        : 50000000-0000-0000-0000-000000000107

-- Template ID for manual journal entries (from V004 seed data)
-- MANUAL_ENTRY template: e0000000-0000-0000-0000-000000000099

-- =============================================================================
-- TRANSACTIONS (headers for journal entries)
-- =============================================================================

INSERT INTO transactions (id, transaction_number, transaction_date, id_journal_template, amount, description, reference_number, status, posted_at, voided_at, void_reason, created_at, updated_at, created_by, updated_by)
VALUES
-- JRN-2024-0001: Initial Capital Injection (Jan 2024)
('90100000-0000-0000-1000-000000000001', 'TRX-2024-0001', '2024-01-05', 'e0000000-0000-0000-0000-000000000099', 100000000.00, 'Setoran modal awal', 'REF-001', 'POSTED', '2024-01-05 10:00:00', NULL, NULL, NOW(), NOW(), 'system', 'system'),
-- JRN-2024-0002: Equipment Purchase via Bank Transfer (Jan 2024)
('90100000-0000-0000-1000-000000000002', 'TRX-2024-0002', '2024-01-15', 'e0000000-0000-0000-0000-000000000099', 20000000.00, 'Pembelian laptop dan server', 'REF-002', 'POSTED', '2024-01-15 14:00:00', NULL, NULL, NOW(), NOW(), 'system', 'system'),
-- JRN-2024-0003: Consulting Revenue (Feb 2024)
('90100000-0000-0000-1000-000000000003', 'TRX-2024-0003', '2024-02-10', 'e0000000-0000-0000-0000-000000000099', 15000000.00, 'Pembayaran jasa konsultasi PT ABC', 'REF-003', 'POSTED', '2024-02-10 11:00:00', NULL, NULL, NOW(), NOW(), 'system', 'system'),
-- JRN-2024-0004: Salary Expense (Feb 2024)
('90100000-0000-0000-1000-000000000004', 'TRX-2024-0004', '2024-02-28', 'e0000000-0000-0000-0000-000000000099', 8000000.00, 'Gaji karyawan Februari 2024', 'REF-004', 'POSTED', '2024-02-28 16:00:00', NULL, NULL, NOW(), NOW(), 'system', 'system'),
-- JRN-2024-0005: Development Project Payment (Mar 2024)
('90100000-0000-0000-1000-000000000005', 'TRX-2024-0005', '2024-03-15', 'e0000000-0000-0000-0000-000000000099', 25000000.00, 'Pembayaran proyek development PT XYZ', 'REF-005', 'POSTED', '2024-03-15 10:00:00', NULL, NULL, NOW(), NOW(), 'system', 'system'),
-- JRN-2024-0006: Cloud Expense (Mar 2024)
('90100000-0000-0000-1000-000000000006', 'TRX-2024-0006', '2024-03-20', 'e0000000-0000-0000-0000-000000000099', 2000000.00, 'Biaya AWS bulan Maret', 'REF-006', 'POSTED', '2024-03-20 09:00:00', NULL, NULL, NOW(), NOW(), 'system', 'system'),
-- JRN-2024-0007: Consulting Revenue (Apr 2024)
('90100000-0000-0000-1000-000000000007', 'TRX-2024-0007', '2024-04-05', 'e0000000-0000-0000-0000-000000000099', 12000000.00, 'Pembayaran jasa konsultasi PT DEF', 'REF-007', 'POSTED', '2024-04-05 11:00:00', NULL, NULL, NOW(), NOW(), 'system', 'system'),
-- JRN-2024-0008: VOID Entry (Apr 2024) - Should be EXCLUDED from calculations
('90100000-0000-0000-1000-000000000008', 'TRX-2024-0008', '2024-04-15', 'e0000000-0000-0000-0000-000000000099', 5000000.00, 'Gaji karyawan - VOID', 'REF-008', 'VOID', '2024-04-15 10:00:00', '2024-04-16 09:00:00', 'INPUT_ERROR', NOW(), NOW(), 'system', 'system'),
-- JRN-2024-0009: Salary Expense (May 2024)
('90100000-0000-0000-1000-000000000009', 'TRX-2024-0009', '2024-05-31', 'e0000000-0000-0000-0000-000000000099', 8000000.00, 'Gaji karyawan Mei 2024', 'REF-009', 'POSTED', '2024-05-31 16:00:00', NULL, NULL, NOW(), NOW(), 'system', 'system'),
-- JRN-2024-0010: Equipment Purchase on Credit (Jun 2024)
('90100000-0000-0000-1000-000000000010', 'TRX-2024-0010', '2024-06-10', 'e0000000-0000-0000-0000-000000000099', 10000000.00, 'Pembelian monitor kredit', 'REF-010', 'POSTED', '2024-06-10 14:00:00', NULL, NULL, NOW(), NOW(), 'system', 'system'),
-- JRN-2024-0011: Depreciation (Jun 2024)
('90100000-0000-0000-1000-000000000011', 'TRX-2024-0011', '2024-06-30', 'e0000000-0000-0000-0000-000000000099', 1000000.00, 'Penyusutan peralatan Q2 2024', 'REF-011', 'POSTED', '2024-06-30 17:00:00', NULL, NULL, NOW(), NOW(), 'system', 'system'),
-- JRN-2024-0012: DRAFT Entry (should be excluded from report calculations)
('90100000-0000-0000-1000-000000000012', 'TRX-2024-0012', '2024-06-30', 'e0000000-0000-0000-0000-000000000099', 3000000.00, 'Bonus karyawan - DRAFT', 'REF-012', 'DRAFT', NULL, NULL, NULL, NOW(), NOW(), 'system', 'system'),
-- JRN-2023-0001: Prior Year Capital (Dec 2023)
('90100000-0000-0000-1000-000000000013', 'TRX-2023-0001', '2023-12-01', 'e0000000-0000-0000-0000-000000000099', 50000000.00, 'Modal awal tahun 2023', 'REF-2023-001', 'POSTED', '2023-12-01 10:00:00', NULL, NULL, NOW(), NOW(), 'system', 'system'),
-- JRN-2023-0002: Prior Year Revenue (Dec 2023)
('90100000-0000-0000-1000-000000000014', 'TRX-2023-0002', '2023-12-15', 'e0000000-0000-0000-0000-000000000099', 10000000.00, 'Jasa konsultasi 2023', 'REF-2023-002', 'POSTED', '2023-12-15 11:00:00', NULL, NULL, NOW(), NOW(), 'system', 'system'),
-- JRN-2024-0013: July 2024 Revenue (should be excluded from Q2 reports)
('90100000-0000-0000-1000-000000000015', 'TRX-2024-0013', '2024-07-05', 'e0000000-0000-0000-0000-000000000099', 20000000.00, 'Jasa konsultasi Juli 2024', 'REF-013', 'POSTED', '2024-07-05 10:00:00', NULL, NULL, NOW(), NOW(), 'system', 'system'),
-- JRN-2024-0014: July 2024 Expense (should be excluded from Q2 reports)
('90100000-0000-0000-1000-000000000016', 'TRX-2024-0014', '2024-07-31', 'e0000000-0000-0000-0000-000000000099', 8000000.00, 'Gaji Juli 2024', 'REF-014', 'POSTED', '2024-07-31 16:00:00', NULL, NULL, NOW(), NOW(), 'system', 'system'),
-- JRN-2024-0015: Soft-deleted entry
('90100000-0000-0000-1000-000000000017', 'TRX-2024-0015', '2024-05-15', 'e0000000-0000-0000-0000-000000000099', 7000000.00, 'Entry yang dihapus - SOFT DELETE', 'REF-015', 'POSTED', '2024-05-15 10:00:00', NULL, NULL, NOW(), NOW(), 'system', 'system');

-- =============================================================================
-- JOURNAL ENTRIES
-- =============================================================================

-- JRN-2024-0001: Initial Capital Injection (Jan 2024)
-- Cash (D) 100,000,000 / Modal Disetor (C) 100,000,000
INSERT INTO journal_entries (id, journal_number, posted_at, id_transaction, id_account, debit_amount, credit_amount, created_at, updated_at, created_by, updated_by, line_order)
VALUES
('90100000-0000-0000-0000-000000000001', 'JRN-2024-0001', '2024-01-05 10:00:00', '90100000-0000-0000-1000-000000000001', '10000000-0000-0000-0000-000000000101', 100000000.00, 0.00, NOW(), NOW(), 'system', 'system', 1),
('90100000-0000-0000-0000-000000000002', 'JRN-2024-0001', '2024-01-05 10:00:00', '90100000-0000-0000-1000-000000000001', '30000000-0000-0000-0000-000000000101', 0.00, 100000000.00, NOW(), NOW(), 'system', 'system', 2);

-- JRN-2024-0002: Equipment Purchase via Bank Transfer (Jan 2024)
-- Peralatan Komputer (D) 20,000,000 / Bank BCA (C) 20,000,000
INSERT INTO journal_entries (id, journal_number, posted_at, id_transaction, id_account, debit_amount, credit_amount, created_at, updated_at, created_by, updated_by, line_order)
VALUES
('90100000-0000-0000-0000-000000000003', 'JRN-2024-0002', '2024-01-15 14:00:00', '90100000-0000-0000-1000-000000000002', '10000000-0000-0000-0000-000000000121', 20000000.00, 0.00, NOW(), NOW(), 'system', 'system', 1),
('90100000-0000-0000-0000-000000000004', 'JRN-2024-0002', '2024-01-15 14:00:00', '90100000-0000-0000-1000-000000000002', '10000000-0000-0000-0000-000000000102', 0.00, 20000000.00, NOW(), NOW(), 'system', 'system', 2);

-- JRN-2024-0003: Consulting Revenue (Feb 2024)
-- Bank BCA (D) 15,000,000 / Pendapatan Jasa Konsultasi (C) 15,000,000
INSERT INTO journal_entries (id, journal_number, posted_at, id_transaction, id_account, debit_amount, credit_amount, created_at, updated_at, created_by, updated_by, line_order)
VALUES
('90100000-0000-0000-0000-000000000005', 'JRN-2024-0003', '2024-02-10 11:00:00', '90100000-0000-0000-1000-000000000003', '10000000-0000-0000-0000-000000000102', 15000000.00, 0.00, NOW(), NOW(), 'system', 'system', 1),
('90100000-0000-0000-0000-000000000006', 'JRN-2024-0003', '2024-02-10 11:00:00', '90100000-0000-0000-1000-000000000003', '40000000-0000-0000-0000-000000000101', 0.00, 15000000.00, NOW(), NOW(), 'system', 'system', 2);

-- JRN-2024-0004: Salary Expense (Feb 2024)
-- Beban Gaji (D) 8,000,000 / Cash (C) 8,000,000
INSERT INTO journal_entries (id, journal_number, posted_at, id_transaction, id_account, debit_amount, credit_amount, created_at, updated_at, created_by, updated_by, line_order)
VALUES
('90100000-0000-0000-0000-000000000007', 'JRN-2024-0004', '2024-02-28 16:00:00', '90100000-0000-0000-1000-000000000004', '50000000-0000-0000-0000-000000000101', 8000000.00, 0.00, NOW(), NOW(), 'system', 'system', 1),
('90100000-0000-0000-0000-000000000008', 'JRN-2024-0004', '2024-02-28 16:00:00', '90100000-0000-0000-1000-000000000004', '10000000-0000-0000-0000-000000000101', 0.00, 8000000.00, NOW(), NOW(), 'system', 'system', 2);

-- JRN-2024-0005: Development Project Payment (Mar 2024)
-- Bank BCA (D) 25,000,000 / Pendapatan Jasa Development (C) 25,000,000
INSERT INTO journal_entries (id, journal_number, posted_at, id_transaction, id_account, debit_amount, credit_amount, created_at, updated_at, created_by, updated_by, line_order)
VALUES
('90100000-0000-0000-0000-000000000009', 'JRN-2024-0005', '2024-03-15 10:00:00', '90100000-0000-0000-1000-000000000005', '10000000-0000-0000-0000-000000000102', 25000000.00, 0.00, NOW(), NOW(), 'system', 'system', 1),
('90100000-0000-0000-0000-000000000010', 'JRN-2024-0005', '2024-03-15 10:00:00', '90100000-0000-0000-1000-000000000005', '40000000-0000-0000-0000-000000000102', 0.00, 25000000.00, NOW(), NOW(), 'system', 'system', 2);

-- JRN-2024-0006: Cloud Expense (Mar 2024)
-- Beban Server & Cloud (D) 2,000,000 / Bank BCA (C) 2,000,000
INSERT INTO journal_entries (id, journal_number, posted_at, id_transaction, id_account, debit_amount, credit_amount, created_at, updated_at, created_by, updated_by, line_order)
VALUES
('90100000-0000-0000-0000-000000000011', 'JRN-2024-0006', '2024-03-20 09:00:00', '90100000-0000-0000-1000-000000000006', '50000000-0000-0000-0000-000000000102', 2000000.00, 0.00, NOW(), NOW(), 'system', 'system', 1),
('90100000-0000-0000-0000-000000000012', 'JRN-2024-0006', '2024-03-20 09:00:00', '90100000-0000-0000-1000-000000000006', '10000000-0000-0000-0000-000000000102', 0.00, 2000000.00, NOW(), NOW(), 'system', 'system', 2);

-- JRN-2024-0007: Consulting Revenue (Apr 2024)
-- Bank BCA (D) 12,000,000 / Pendapatan Jasa Konsultasi (C) 12,000,000
INSERT INTO journal_entries (id, journal_number, posted_at, id_transaction, id_account, debit_amount, credit_amount, created_at, updated_at, created_by, updated_by, line_order)
VALUES
('90100000-0000-0000-0000-000000000013', 'JRN-2024-0007', '2024-04-05 11:00:00', '90100000-0000-0000-1000-000000000007', '10000000-0000-0000-0000-000000000102', 12000000.00, 0.00, NOW(), NOW(), 'system', 'system', 1),
('90100000-0000-0000-0000-000000000014', 'JRN-2024-0007', '2024-04-05 11:00:00', '90100000-0000-0000-1000-000000000007', '40000000-0000-0000-0000-000000000101', 0.00, 12000000.00, NOW(), NOW(), 'system', 'system', 2);

-- JRN-2024-0008: VOID Entry (Apr 2024) - Should be EXCLUDED from calculations
-- Beban Gaji (D) 5,000,000 / Cash (C) 5,000,000
INSERT INTO journal_entries (id, journal_number, posted_at, voided_at, void_reason, id_transaction, id_account, debit_amount, credit_amount, created_at, updated_at, created_by, updated_by, line_order)
VALUES
('90100000-0000-0000-0000-000000000015', 'JRN-2024-0008', '2024-04-15 10:00:00', '2024-04-16 09:00:00', 'Salah input jumlah', '90100000-0000-0000-1000-000000000008', '50000000-0000-0000-0000-000000000101', 5000000.00, 0.00, NOW(), NOW(), 'system', 'system', 1),
('90100000-0000-0000-0000-000000000016', 'JRN-2024-0008', '2024-04-15 10:00:00', '2024-04-16 09:00:00', 'Salah input jumlah', '90100000-0000-0000-1000-000000000008', '10000000-0000-0000-0000-000000000101', 0.00, 5000000.00, NOW(), NOW(), 'system', 'system', 2);

-- JRN-2024-0009: Salary Expense (May 2024)
-- Beban Gaji (D) 8,000,000 / Cash (C) 8,000,000
INSERT INTO journal_entries (id, journal_number, posted_at, id_transaction, id_account, debit_amount, credit_amount, created_at, updated_at, created_by, updated_by, line_order)
VALUES
('90100000-0000-0000-0000-000000000017', 'JRN-2024-0009', '2024-05-31 16:00:00', '90100000-0000-0000-1000-000000000009', '50000000-0000-0000-0000-000000000101', 8000000.00, 0.00, NOW(), NOW(), 'system', 'system', 1),
('90100000-0000-0000-0000-000000000018', 'JRN-2024-0009', '2024-05-31 16:00:00', '90100000-0000-0000-1000-000000000009', '10000000-0000-0000-0000-000000000101', 0.00, 8000000.00, NOW(), NOW(), 'system', 'system', 2);

-- JRN-2024-0010: Equipment Purchase on Credit (Jun 2024)
-- Peralatan Komputer (D) 10,000,000 / Hutang Usaha (C) 10,000,000
INSERT INTO journal_entries (id, journal_number, posted_at, id_transaction, id_account, debit_amount, credit_amount, created_at, updated_at, created_by, updated_by, line_order)
VALUES
('90100000-0000-0000-0000-000000000019', 'JRN-2024-0010', '2024-06-10 14:00:00', '90100000-0000-0000-1000-000000000010', '10000000-0000-0000-0000-000000000121', 10000000.00, 0.00, NOW(), NOW(), 'system', 'system', 1),
('90100000-0000-0000-0000-000000000020', 'JRN-2024-0010', '2024-06-10 14:00:00', '90100000-0000-0000-1000-000000000010', '20000000-0000-0000-0000-000000000101', 0.00, 10000000.00, NOW(), NOW(), 'system', 'system', 2);

-- JRN-2024-0011: Depreciation (Jun 2024)
-- Beban Penyusutan (D) 1,000,000 / Akum. Penyusutan Peralatan (C) 1,000,000
INSERT INTO journal_entries (id, journal_number, posted_at, id_transaction, id_account, debit_amount, credit_amount, created_at, updated_at, created_by, updated_by, line_order)
VALUES
('90100000-0000-0000-0000-000000000021', 'JRN-2024-0011', '2024-06-30 17:00:00', '90100000-0000-0000-1000-000000000011', '50000000-0000-0000-0000-000000000107', 1000000.00, 0.00, NOW(), NOW(), 'system', 'system', 1),
('90100000-0000-0000-0000-000000000022', 'JRN-2024-0011', '2024-06-30 17:00:00', '90100000-0000-0000-1000-000000000011', '10000000-0000-0000-0000-000000000122', 0.00, 1000000.00, NOW(), NOW(), 'system', 'system', 2);

-- JRN-2024-0012: DRAFT Entry (should be excluded from report calculations)
-- Beban Gaji (D) 3,000,000 / Cash (C) 3,000,000
INSERT INTO journal_entries (id, journal_number, id_transaction, id_account, debit_amount, credit_amount, created_at, updated_at, created_by, updated_by, line_order)
VALUES
('90100000-0000-0000-0000-000000000023', 'JRN-2024-0012', '90100000-0000-0000-1000-000000000012', '50000000-0000-0000-0000-000000000101', 3000000.00, 0.00, NOW(), NOW(), 'system', 'system', 1),
('90100000-0000-0000-0000-000000000024', 'JRN-2024-0012', '90100000-0000-0000-1000-000000000012', '10000000-0000-0000-0000-000000000101', 0.00, 3000000.00, NOW(), NOW(), 'system', 'system', 2);

-- =============================================================================
-- PRIOR PERIOD ENTRIES (2023) - Should be included in Balance Sheet, excluded from 2024 Income Statement
-- =============================================================================

-- JRN-2023-0001: Prior Year Capital (Dec 2023)
-- Cash (D) 50,000,000 / Modal Disetor (C) 50,000,000
INSERT INTO journal_entries (id, journal_number, posted_at, id_transaction, id_account, debit_amount, credit_amount, created_at, updated_at, created_by, updated_by, line_order)
VALUES
('90100000-0000-0000-0000-000000000025', 'JRN-2023-0001', '2023-12-01 10:00:00', '90100000-0000-0000-1000-000000000013', '10000000-0000-0000-0000-000000000101', 50000000.00, 0.00, NOW(), NOW(), 'system', 'system', 1),
('90100000-0000-0000-0000-000000000026', 'JRN-2023-0001', '2023-12-01 10:00:00', '90100000-0000-0000-1000-000000000013', '30000000-0000-0000-0000-000000000101', 0.00, 50000000.00, NOW(), NOW(), 'system', 'system', 2);

-- JRN-2023-0002: Prior Year Revenue (Dec 2023)
-- Bank BCA (D) 10,000,000 / Pendapatan Jasa Konsultasi (C) 10,000,000
INSERT INTO journal_entries (id, journal_number, posted_at, id_transaction, id_account, debit_amount, credit_amount, created_at, updated_at, created_by, updated_by, line_order)
VALUES
('90100000-0000-0000-0000-000000000027', 'JRN-2023-0002', '2023-12-15 11:00:00', '90100000-0000-0000-1000-000000000014', '10000000-0000-0000-0000-000000000102', 10000000.00, 0.00, NOW(), NOW(), 'system', 'system', 1),
('90100000-0000-0000-0000-000000000028', 'JRN-2023-0002', '2023-12-15 11:00:00', '90100000-0000-0000-1000-000000000014', '40000000-0000-0000-0000-000000000101', 0.00, 10000000.00, NOW(), NOW(), 'system', 'system', 2);

-- =============================================================================
-- FUTURE PERIOD ENTRIES (Jul 2024+) - Should be excluded when reporting as of 2024-06-30
-- =============================================================================

-- JRN-2024-0013: July 2024 Revenue (should be excluded from Q2 reports)
-- Bank BCA (D) 20,000,000 / Pendapatan Jasa Konsultasi (C) 20,000,000
INSERT INTO journal_entries (id, journal_number, posted_at, id_transaction, id_account, debit_amount, credit_amount, created_at, updated_at, created_by, updated_by, line_order)
VALUES
('90100000-0000-0000-0000-000000000029', 'JRN-2024-0013', '2024-07-05 10:00:00', '90100000-0000-0000-1000-000000000015', '10000000-0000-0000-0000-000000000102', 20000000.00, 0.00, NOW(), NOW(), 'system', 'system', 1),
('90100000-0000-0000-0000-000000000030', 'JRN-2024-0013', '2024-07-05 10:00:00', '90100000-0000-0000-1000-000000000015', '40000000-0000-0000-0000-000000000101', 0.00, 20000000.00, NOW(), NOW(), 'system', 'system', 2);

-- JRN-2024-0014: July 2024 Expense (should be excluded from Q2 reports)
-- Beban Gaji (D) 8,000,000 / Cash (C) 8,000,000
INSERT INTO journal_entries (id, journal_number, posted_at, id_transaction, id_account, debit_amount, credit_amount, created_at, updated_at, created_by, updated_by, line_order)
VALUES
('90100000-0000-0000-0000-000000000031', 'JRN-2024-0014', '2024-07-31 16:00:00', '90100000-0000-0000-1000-000000000016', '50000000-0000-0000-0000-000000000101', 8000000.00, 0.00, NOW(), NOW(), 'system', 'system', 1),
('90100000-0000-0000-0000-000000000032', 'JRN-2024-0014', '2024-07-31 16:00:00', '90100000-0000-0000-1000-000000000016', '10000000-0000-0000-0000-000000000101', 0.00, 8000000.00, NOW(), NOW(), 'system', 'system', 2);

-- =============================================================================
-- SOFT-DELETED ENTRIES - Should be excluded by @SQLRestriction
-- =============================================================================

-- JRN-2024-0015: Soft-deleted entry (deleted_at IS NOT NULL)
-- Should NOT appear in any reports or calculations
INSERT INTO journal_entries (id, journal_number, posted_at, id_transaction, id_account, debit_amount, credit_amount, created_at, updated_at, created_by, updated_by, deleted_at, line_order)
VALUES
('90100000-0000-0000-0000-000000000033', 'JRN-2024-0015', '2024-05-15 10:00:00', '90100000-0000-0000-1000-000000000017', '50000000-0000-0000-0000-000000000101', 7000000.00, 0.00, NOW(), NOW(), 'system', 'system', NOW(), 1),
('90100000-0000-0000-0000-000000000034', 'JRN-2024-0015', '2024-05-15 10:00:00', '90100000-0000-0000-1000-000000000017', '10000000-0000-0000-0000-000000000101', 0.00, 7000000.00, NOW(), NOW(), 'system', 'system', NOW(), 2);
