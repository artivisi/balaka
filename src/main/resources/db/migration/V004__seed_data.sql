-- V004: Seed Data
-- Transaction sequences, bank parser configs, alert rules

-- ============================================
-- Transaction Sequences - Initialize for 2025
-- ============================================

INSERT INTO transaction_sequences (id, sequence_type, prefix, year, last_number) VALUES
('d0000000-0000-0000-0000-000000000001', 'TRANSACTION', 'TRX', 2025, 0),
('d0000000-0000-0000-0000-000000000002', 'JOURNAL', 'JE', 2025, 0);

-- ============================================
-- Bank Statement Parser Configs
-- ============================================

-- BCA (Bank Central Asia)
-- CSV format: Date, Description, Branch, Amount (negative=debit), Balance
INSERT INTO bank_statement_parser_configs (id, bank_type, config_name, description, date_column, description_column, debit_column, credit_column, balance_column, date_format, delimiter, skip_header_rows, encoding, decimal_separator, thousand_separator, is_system, active)
VALUES (gen_random_uuid(), 'BCA', 'BCA - CSV Standar', 'Format CSV standar dari BCA KlikBCA/myBCA', 0, 1, 3, 4, 5, 'dd/MM/yyyy', ',', 1, 'UTF-8', '.', ',', TRUE, TRUE);

-- Mandiri
-- CSV format: Date, Description, Debit, Credit, Balance
INSERT INTO bank_statement_parser_configs (id, bank_type, config_name, description, date_column, description_column, debit_column, credit_column, balance_column, date_format, delimiter, skip_header_rows, encoding, decimal_separator, thousand_separator, is_system, active)
VALUES (gen_random_uuid(), 'MANDIRI', 'Mandiri - CSV Standar', 'Format CSV standar dari Mandiri Online/Livin', 0, 1, 2, 3, 4, 'dd/MM/yyyy', ',', 1, 'UTF-8', '.', ',', TRUE, TRUE);

-- BNI (Bank Negara Indonesia)
-- CSV format: Date, Description, Branch, Debit, Credit, Balance
INSERT INTO bank_statement_parser_configs (id, bank_type, config_name, description, date_column, description_column, debit_column, credit_column, balance_column, date_format, delimiter, skip_header_rows, encoding, decimal_separator, thousand_separator, is_system, active)
VALUES (gen_random_uuid(), 'BNI', 'BNI - CSV Standar', 'Format CSV standar dari BNI Internet Banking', 0, 1, 3, 4, 5, 'dd/MM/yyyy', ',', 1, 'UTF-8', '.', ',', TRUE, TRUE);

-- BSI (Bank Syariah Indonesia)
-- CSV format: Date, Description, Debit, Credit, Balance
INSERT INTO bank_statement_parser_configs (id, bank_type, config_name, description, date_column, description_column, debit_column, credit_column, balance_column, date_format, delimiter, skip_header_rows, encoding, decimal_separator, thousand_separator, is_system, active)
VALUES (gen_random_uuid(), 'BSI', 'BSI - CSV Standar', 'Format CSV standar dari BSI Mobile/Net Banking', 0, 1, 2, 3, 4, 'dd/MM/yyyy', ',', 1, 'UTF-8', '.', ',', TRUE, TRUE);

-- CIMB Niaga
-- CSV format: Date, Description, Debit, Credit, Balance
INSERT INTO bank_statement_parser_configs (id, bank_type, config_name, description, date_column, description_column, debit_column, credit_column, balance_column, date_format, delimiter, skip_header_rows, encoding, decimal_separator, thousand_separator, is_system, active)
VALUES (gen_random_uuid(), 'CIMB', 'CIMB Niaga - CSV Standar', 'Format CSV standar dari CIMB Niaga OCTO', 0, 1, 2, 3, 4, 'dd/MM/yyyy', ',', 1, 'UTF-8', '.', ',', TRUE, TRUE);

-- ============================================
-- Smart Alert Rules
-- ============================================

INSERT INTO alert_rules (id, row_version, alert_type, threshold, enabled, description) VALUES
    (gen_random_uuid(), 0, 'CASH_LOW', 10000000.00, true, 'Peringatan jika saldo kas + bank di bawah ambang batas'),
    (gen_random_uuid(), 0, 'RECEIVABLE_OVERDUE', 0, true, 'Peringatan jika ada piutang yang jatuh tempo'),
    (gen_random_uuid(), 0, 'EXPENSE_SPIKE', 30.00, true, 'Peringatan jika biaya bulan ini naik lebih dari X% dari rata-rata 3 bulan sebelumnya'),
    (gen_random_uuid(), 0, 'PROJECT_COST_OVERRUN', 0, true, 'Peringatan jika ada proyek yang melebihi anggaran'),
    (gen_random_uuid(), 0, 'PROJECT_MARGIN_DROP', 10.00, true, 'Peringatan jika margin proyek turun di bawah X%'),
    (gen_random_uuid(), 0, 'COLLECTION_SLOWDOWN', 30.00, true, 'Peringatan jika rata-rata hari penagihan melebihi X hari'),
    (gen_random_uuid(), 0, 'CLIENT_CONCENTRATION', 50.00, true, 'Peringatan jika satu klien menyumbang lebih dari X% pendapatan');
