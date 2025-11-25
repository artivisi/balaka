-- Test data for Transaction functional tests

-- Test transaction 1: Draft transaction (Income)
INSERT INTO transactions (id, transaction_number, transaction_date, id_journal_template, amount, description, reference_number, notes, status, created_at, updated_at)
VALUES (
    'a0000000-0000-0000-0000-000000000001',
    'TRX-TEST-0001',
    CURRENT_DATE,
    'e0000000-0000-0000-0000-000000000001', -- Pendapatan Jasa Konsultasi template
    10000000,
    'Test Income Transaction - Draft',
    'INV-TEST-001',
    'Test notes for draft transaction',
    'DRAFT',
    NOW(),
    NOW()
);

-- Test transaction 2: Posted transaction (Income) - for void testing
INSERT INTO transactions (id, transaction_number, transaction_date, id_journal_template, amount, description, reference_number, notes, status, posted_at, posted_by, created_at, updated_at)
VALUES (
    'a0000000-0000-0000-0000-000000000002',
    'TRX-TEST-0002',
    CURRENT_DATE,
    'e0000000-0000-0000-0000-000000000001', -- Pendapatan Jasa Konsultasi template
    15000000,
    'Test Income Transaction - Posted',
    'INV-TEST-002',
    'Test notes for posted transaction',
    'POSTED',
    NOW(),
    'admin',
    NOW(),
    NOW()
);

-- Journal entries for posted transaction (TRX-TEST-0002)
-- Debit: Bank BCA (1.1.02)
INSERT INTO journal_entries (id, journal_number, journal_date, id_account, debit_amount, credit_amount, description, status, id_transaction, created_at, updated_at)
VALUES (
    'b0000000-0000-0000-0000-000000000001',
    'JE-TEST-0001',
    CURRENT_DATE,
    '10000000-0000-0000-0000-000000000102', -- Bank BCA (1.1.02)
    15000000,
    0,
    'Test Income Transaction - Posted',
    'POSTED',
    'a0000000-0000-0000-0000-000000000002',
    NOW(),
    NOW()
);

-- Credit: Pendapatan Jasa Konsultasi (4.1.01)
INSERT INTO journal_entries (id, journal_number, journal_date, id_account, debit_amount, credit_amount, description, status, id_transaction, created_at, updated_at)
VALUES (
    'b0000000-0000-0000-0000-000000000002',
    'JE-TEST-0001',
    CURRENT_DATE,
    '40000000-0000-0000-0000-000000000101', -- Pendapatan Jasa Konsultasi (4.1.01)
    0,
    15000000,
    'Test Income Transaction - Posted',
    'POSTED',
    'a0000000-0000-0000-0000-000000000002',
    NOW(),
    NOW()
);

-- Test transaction 3: Voided transaction (Expense)
INSERT INTO transactions (id, transaction_number, transaction_date, id_journal_template, amount, description, reference_number, notes, status, posted_at, posted_by, voided_at, voided_by, void_reason, void_notes, created_at, updated_at)
VALUES (
    'a0000000-0000-0000-0000-000000000003',
    'TRX-TEST-0003',
    CURRENT_DATE - INTERVAL '1 day',
    'e0000000-0000-0000-0000-000000000004', -- Bayar Beban Gaji (EXPENSE template)
    5000000,
    'Test Expense Transaction - Voided',
    'PO-TEST-001',
    'Test notes for voided transaction',
    'VOID',
    NOW() - INTERVAL '1 hour',
    'admin',
    NOW(),
    'admin',
    'INPUT_ERROR',
    'Salah input jumlah',
    NOW() - INTERVAL '2 hours',
    NOW()
);
