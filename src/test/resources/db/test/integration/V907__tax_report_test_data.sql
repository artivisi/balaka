-- Test data for Tax Report functional tests

-- ============================================
-- Company Config for Coretax Export
-- ============================================
INSERT INTO company_config (
    id, company_name, company_address, company_phone, company_email,
    tax_id, npwp, nitku, fiscal_year_start_month, currency_code,
    signing_officer_name, signing_officer_title,
    id_receivable_account, id_output_tax_account, created_at, updated_at
) VALUES (
    'a0000000-0000-0000-0000-000000000001',
    'PT Artivisi Intermedia',
    'Jl. Margonda Raya No. 123, Depok, Jawa Barat 16424',
    '021-7712345',
    'info@artivisi.com',
    '01.234.567.8-012.000',
    '01.234.567.8-012.000',
    '0000000000000000000001',
    1,
    'IDR',
    'Endy Muhardin',
    'Direktur',
    '10000000-0000-0000-0000-000000000104',  -- Piutang Usaha (AR)
    '20000000-0000-0000-0000-000000000103',  -- Hutang PPN (output tax)
    NOW(),
    NOW()
) ON CONFLICT (id) DO UPDATE SET
    npwp = EXCLUDED.npwp,
    nitku = EXCLUDED.nitku,
    id_receivable_account = EXCLUDED.id_receivable_account,
    id_output_tax_account = EXCLUDED.id_output_tax_account;

-- ============================================
-- Tax Transactions - Sales with PPN
-- ============================================

-- Transaction 1: Sales with PPN (Pendapatan Jasa + PPN Keluaran)
-- DPP: 10.000.000, PPN: 1.100.000, Total: 11.100.000
INSERT INTO transactions (id, transaction_number, transaction_date, id_journal_template, amount, description, reference_number, notes, status, posted_at, posted_by, created_at, updated_at)
VALUES (
    'c0000000-0000-0000-0000-000000000001',
    'TRX-TAX-0001',
    CURRENT_DATE - INTERVAL '5 day',
    'e0000000-0000-0000-0000-000000000001', -- Pendapatan Jasa Konsultasi
    11100000,
    'Pendapatan Jasa Konsultasi dengan PPN',
    'INV-TAX-001',
    'DPP: 10.000.000, PPN: 1.100.000',
    'POSTED',
    NOW(),
    'admin',
    NOW(),
    NOW()
);

-- Journal entries for TRX-TAX-0001
-- Debit: Bank BCA 11.100.000
INSERT INTO journal_entries (id, journal_number, id_transaction, id_account, debit_amount, credit_amount, posted_at, created_at, updated_at)
VALUES (
    'c1000000-0000-0000-0000-000000000001',
    'JE-TAX-0001',
    'c0000000-0000-0000-0000-000000000001',
    '10000000-0000-0000-0000-000000000102', -- Bank BCA (1.1.02)
    11100000,
    0,
    NOW(),
    NOW(),
    NOW()
);

-- Credit: Pendapatan Jasa Konsultasi 10.000.000
INSERT INTO journal_entries (id, journal_number, id_transaction, id_account, debit_amount, credit_amount, posted_at, created_at, updated_at)
VALUES (
    'c1000000-0000-0000-0000-000000000002',
    'JE-TAX-0001',
    'c0000000-0000-0000-0000-000000000001',
    '40000000-0000-0000-0000-000000000101', -- Pendapatan Jasa Konsultasi (4.1.01)
    0,
    10000000,
    NOW(),
    NOW(),
    NOW()
);

-- Credit: Hutang PPN 1.100.000 (PPN Keluaran)
INSERT INTO journal_entries (id, journal_number, id_transaction, id_account, debit_amount, credit_amount, posted_at, created_at, updated_at)
VALUES (
    'c1000000-0000-0000-0000-000000000003',
    'JE-TAX-0001',
    'c0000000-0000-0000-0000-000000000001',
    '20000000-0000-0000-0000-000000000103', -- Hutang PPN (2.1.03)
    0,
    1100000,
    NOW(),
    NOW(),
    NOW()
);

-- ============================================
-- Tax Transactions - Purchase with PPN
-- ============================================

-- Transaction 2: Purchase with PPN (Beban + PPN Masukan)
-- DPP: 5.000.000, PPN: 550.000, Total: 5.550.000
INSERT INTO transactions (id, transaction_number, transaction_date, id_journal_template, amount, description, reference_number, notes, status, posted_at, posted_by, created_at, updated_at)
VALUES (
    'c0000000-0000-0000-0000-000000000002',
    'TRX-TAX-0002',
    CURRENT_DATE - INTERVAL '4 day',
    'e0000000-0000-0000-0000-000000000005', -- Bayar Beban Server & Cloud
    5550000,
    'Pembelian Server dengan PPN',
    'PO-TAX-001',
    'DPP: 5.000.000, PPN: 550.000',
    'POSTED',
    NOW(),
    'admin',
    NOW(),
    NOW()
);

-- Journal entries for TRX-TAX-0002
-- Debit: Beban Server & Cloud 5.000.000
INSERT INTO journal_entries (id, journal_number, id_transaction, id_account, debit_amount, credit_amount, posted_at, created_at, updated_at)
VALUES (
    'c1000000-0000-0000-0000-000000000004',
    'JE-TAX-0002',
    'c0000000-0000-0000-0000-000000000002',
    '50000000-0000-0000-0000-000000000102', -- Beban Server & Cloud (5.1.02)
    5000000,
    0,
    NOW(),
    NOW(),
    NOW()
);

-- Debit: PPN Masukan 550.000
INSERT INTO journal_entries (id, journal_number, id_transaction, id_account, debit_amount, credit_amount, posted_at, created_at, updated_at)
VALUES (
    'c1000000-0000-0000-0000-000000000005',
    'JE-TAX-0002',
    'c0000000-0000-0000-0000-000000000002',
    '10000000-0000-0000-0000-000000000125', -- PPN Masukan (1.1.25)
    550000,
    0,
    NOW(),
    NOW(),
    NOW()
);

-- Credit: Bank BCA 5.550.000
INSERT INTO journal_entries (id, journal_number, id_transaction, id_account, debit_amount, credit_amount, posted_at, created_at, updated_at)
VALUES (
    'c1000000-0000-0000-0000-000000000006',
    'JE-TAX-0002',
    'c0000000-0000-0000-0000-000000000002',
    '10000000-0000-0000-0000-000000000102', -- Bank BCA (1.1.02)
    0,
    5550000,
    NOW(),
    NOW(),
    NOW()
);

-- ============================================
-- Tax Transactions - Payment with PPh 23 withholding
-- ============================================

-- Transaction 3: Payment to vendor with PPh 23 (2% withholding)
-- Gross: 2.000.000, PPh 23: 40.000, Net: 1.960.000
INSERT INTO transactions (id, transaction_number, transaction_date, id_journal_template, amount, description, reference_number, notes, status, posted_at, posted_by, created_at, updated_at)
VALUES (
    'c0000000-0000-0000-0000-000000000003',
    'TRX-TAX-0003',
    CURRENT_DATE - INTERVAL '3 day',
    'e0000000-0000-0000-0000-000000000006', -- Bayar Beban Software & Lisensi
    2000000,
    'Pembayaran Jasa dengan PPh 23',
    'PO-TAX-002',
    'Gross: 2.000.000, PPh 23 (2%): 40.000',
    'POSTED',
    NOW(),
    'admin',
    NOW(),
    NOW()
);

-- Journal entries for TRX-TAX-0003
-- Debit: Beban Software & Lisensi 2.000.000
INSERT INTO journal_entries (id, journal_number, id_transaction, id_account, debit_amount, credit_amount, posted_at, created_at, updated_at)
VALUES (
    'c1000000-0000-0000-0000-000000000007',
    'JE-TAX-0003',
    'c0000000-0000-0000-0000-000000000003',
    '50000000-0000-0000-0000-000000000103', -- Beban Software & Lisensi (5.1.03)
    2000000,
    0,
    NOW(),
    NOW(),
    NOW()
);

-- Credit: Bank BCA 1.960.000 (Net payment)
INSERT INTO journal_entries (id, journal_number, id_transaction, id_account, debit_amount, credit_amount, posted_at, created_at, updated_at)
VALUES (
    'c1000000-0000-0000-0000-000000000008',
    'JE-TAX-0003',
    'c0000000-0000-0000-0000-000000000003',
    '10000000-0000-0000-0000-000000000102', -- Bank BCA (1.1.02)
    0,
    1960000,
    NOW(),
    NOW(),
    NOW()
);

-- Credit: Hutang PPh 23 40.000 (Withheld)
INSERT INTO journal_entries (id, journal_number, id_transaction, id_account, debit_amount, credit_amount, posted_at, created_at, updated_at)
VALUES (
    'c1000000-0000-0000-0000-000000000009',
    'JE-TAX-0003',
    'c0000000-0000-0000-0000-000000000003',
    '20000000-0000-0000-0000-000000000121', -- Hutang PPh 23 (2.1.21)
    0,
    40000,
    NOW(),
    NOW(),
    NOW()
);

-- ============================================
-- Tax Transactions - Another Sales with PPN
-- ============================================

-- Transaction 4: Another Sales with PPN
-- DPP: 15.000.000, PPN: 1.650.000, Total: 16.650.000
INSERT INTO transactions (id, transaction_number, transaction_date, id_journal_template, amount, description, reference_number, notes, status, posted_at, posted_by, created_at, updated_at)
VALUES (
    'c0000000-0000-0000-0000-000000000004',
    'TRX-TAX-0004',
    CURRENT_DATE - INTERVAL '2 day',
    'e0000000-0000-0000-0000-000000000002', -- Pendapatan Jasa Development
    16650000,
    'Pendapatan Jasa Development dengan PPN',
    'INV-TAX-002',
    'DPP: 15.000.000, PPN: 1.650.000',
    'POSTED',
    NOW(),
    'admin',
    NOW(),
    NOW()
);

-- Journal entries for TRX-TAX-0004
-- Debit: Bank BCA 16.650.000
INSERT INTO journal_entries (id, journal_number, id_transaction, id_account, debit_amount, credit_amount, posted_at, created_at, updated_at)
VALUES (
    'c1000000-0000-0000-0000-000000000010',
    'JE-TAX-0004',
    'c0000000-0000-0000-0000-000000000004',
    '10000000-0000-0000-0000-000000000102', -- Bank BCA (1.1.02)
    16650000,
    0,
    NOW(),
    NOW(),
    NOW()
);

-- Credit: Pendapatan Jasa Development 15.000.000
INSERT INTO journal_entries (id, journal_number, id_transaction, id_account, debit_amount, credit_amount, posted_at, created_at, updated_at)
VALUES (
    'c1000000-0000-0000-0000-000000000011',
    'JE-TAX-0004',
    'c0000000-0000-0000-0000-000000000004',
    '40000000-0000-0000-0000-000000000102', -- Pendapatan Jasa Development (4.1.02)
    0,
    15000000,
    NOW(),
    NOW(),
    NOW()
);

-- Credit: Hutang PPN 1.650.000 (PPN Keluaran)
INSERT INTO journal_entries (id, journal_number, id_transaction, id_account, debit_amount, credit_amount, posted_at, created_at, updated_at)
VALUES (
    'c1000000-0000-0000-0000-000000000012',
    'JE-TAX-0004',
    'c0000000-0000-0000-0000-000000000004',
    '20000000-0000-0000-0000-000000000103', -- Hutang PPN (2.1.03)
    0,
    1650000,
    NOW(),
    NOW(),
    NOW()
);

-- ============================================
-- Tax Transaction Details for Coretax Export
-- ============================================

-- TaxTransactionDetail for TRX-TAX-0001 (PPN Keluaran - Sales)
INSERT INTO tax_transaction_details (
    id, id_transaction,
    faktur_number, faktur_date, transaction_code, dpp, ppn, ppnbm,
    counterparty_npwp, counterparty_nitku, counterparty_id_type, counterparty_name, counterparty_address,
    tax_type, created_at, updated_at
) VALUES (
    'd0000000-0000-0000-0000-000000000001',
    'c0000000-0000-0000-0000-000000000001',
    '010.000-24.00000001', CURRENT_DATE - INTERVAL '5 day', '01', 10000000, 1100000, 0,
    '01.234.567.8-901.000', '0000000000000000000001', 'TIN', 'PT Client ABC', 'Jl. Sudirman No. 123, Jakarta',
    'PPN_KELUARAN', NOW(), NOW()
);

-- TaxTransactionDetail for TRX-TAX-0002 (PPN Masukan - Purchase)
INSERT INTO tax_transaction_details (
    id, id_transaction,
    faktur_number, faktur_date, transaction_code, dpp, ppn, ppnbm,
    counterparty_npwp, counterparty_nitku, counterparty_id_type, counterparty_name, counterparty_address,
    tax_type, created_at, updated_at
) VALUES (
    'd0000000-0000-0000-0000-000000000002',
    'c0000000-0000-0000-0000-000000000002',
    '010.000-24.00000002', CURRENT_DATE - INTERVAL '4 day', '01', 5000000, 550000, 0,
    '02.345.678.9-012.000', '0000000000000000000002', 'TIN', 'PT Vendor XYZ', 'Jl. Gatot Subroto No. 456, Jakarta',
    'PPN_MASUKAN', NOW(), NOW()
);

-- TaxTransactionDetail for TRX-TAX-0003 (PPh 23 Withholding)
INSERT INTO tax_transaction_details (
    id, id_transaction,
    bupot_number, tax_object_code, gross_amount, tax_rate, tax_amount,
    counterparty_npwp, counterparty_nitku, counterparty_id_type, counterparty_name, counterparty_address,
    tax_type, created_at, updated_at
) VALUES (
    'd0000000-0000-0000-0000-000000000003',
    'c0000000-0000-0000-0000-000000000003',
    'BP-2024-00001', '24-104-03', 2000000, 2.00, 40000,
    '03.456.789.0-123.000', '0000000000000000000003', 'TIN', 'CV Konsultan DEF', 'Jl. HR Rasuna Said No. 789, Jakarta',
    'PPH_23', NOW(), NOW()
);

-- TaxTransactionDetail for TRX-TAX-0004 (PPN Keluaran - Sales 2)
INSERT INTO tax_transaction_details (
    id, id_transaction,
    faktur_number, faktur_date, transaction_code, dpp, ppn, ppnbm,
    counterparty_npwp, counterparty_nitku, counterparty_id_type, counterparty_name, counterparty_address,
    tax_type, created_at, updated_at
) VALUES (
    'd0000000-0000-0000-0000-000000000004',
    'c0000000-0000-0000-0000-000000000004',
    '010.000-24.00000003', CURRENT_DATE - INTERVAL '2 day', '01', 15000000, 1650000, 0,
    '04.567.890.1-234.000', '0000000000000000000004', 'TIN', 'PT Customer GHI', 'Jl. Thamrin No. 321, Jakarta',
    'PPN_KELUARAN', NOW(), NOW()
);

-- ============================================
-- Expected Results Summary:
-- PPN Keluaran (Hutang PPN): 1.100.000 + 1.650.000 = 2.750.000
-- PPN Masukan: 550.000
-- Net PPN (Kurang Bayar): 2.750.000 - 550.000 = 2.200.000
--
-- PPh 23 Withheld: 40.000
-- PPh 23 Deposited: 0
-- PPh 23 Balance: 40.000
--
-- Coretax Export Expected:
-- e-Faktur Keluaran: 2 records (TRX-TAX-0001, TRX-TAX-0004)
-- e-Faktur Masukan: 1 record (TRX-TAX-0002)
-- e-Bupot Unifikasi: 1 record (TRX-TAX-0003)
-- ============================================
