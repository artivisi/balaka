-- V002: Chart of Accounts

CREATE TABLE chart_of_accounts (
    id UUID PRIMARY KEY,
    account_code VARCHAR(20) NOT NULL UNIQUE,
    account_name VARCHAR(255) NOT NULL,
    account_type VARCHAR(20) NOT NULL,
    normal_balance VARCHAR(10) NOT NULL,
    id_parent UUID REFERENCES chart_of_accounts(id),
    level INTEGER NOT NULL DEFAULT 1,
    is_header BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    is_permanent BOOLEAN NOT NULL DEFAULT TRUE,
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    deleted_at TIMESTAMP,

    CONSTRAINT chk_account_type CHECK (account_type IN ('ASSET', 'LIABILITY', 'EQUITY', 'REVENUE', 'EXPENSE')),
    CONSTRAINT chk_normal_balance CHECK (normal_balance IN ('DEBIT', 'CREDIT'))
);

CREATE INDEX idx_coa_account_code ON chart_of_accounts(account_code);
CREATE INDEX idx_coa_account_type ON chart_of_accounts(account_type);
CREATE INDEX idx_coa_parent ON chart_of_accounts(id_parent);
CREATE INDEX idx_coa_active ON chart_of_accounts(active);

-- Pre-seed IT Services Chart of Accounts

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
('20000000-0000-0000-0000-000000000104', '2.1.04', 'Pendapatan Diterima Dimuka', 'LIABILITY', 'CREDIT', '20000000-0000-0000-0000-000000000011', 3, FALSE, TRUE, TRUE);

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
('50000000-0000-0000-0000-000000000121', '5.2.01', 'Beban Bank', 'EXPENSE', 'DEBIT', '50000000-0000-0000-0000-000000000012', 3, FALSE, TRUE, FALSE);
