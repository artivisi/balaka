-- V003: Journal Templates

CREATE TABLE journal_templates (
    id UUID PRIMARY KEY,
    template_name VARCHAR(255) NOT NULL,
    category VARCHAR(20) NOT NULL,
    cash_flow_category VARCHAR(20) NOT NULL,
    template_type VARCHAR(20) NOT NULL DEFAULT 'SIMPLE',
    description TEXT,
    is_favorite BOOLEAN NOT NULL DEFAULT FALSE,
    is_system BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version INTEGER NOT NULL DEFAULT 1,
    usage_count INTEGER NOT NULL DEFAULT 0,
    last_used_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by VARCHAR(100),
    updated_by VARCHAR(100),

    CONSTRAINT chk_category CHECK (category IN ('INCOME', 'EXPENSE', 'PAYMENT', 'RECEIPT', 'TRANSFER')),
    CONSTRAINT chk_cash_flow_category CHECK (cash_flow_category IN ('OPERATING', 'INVESTING', 'FINANCING')),
    CONSTRAINT chk_template_type CHECK (template_type IN ('SIMPLE', 'DETAILED'))
);

CREATE INDEX idx_jt_category ON journal_templates(category);
CREATE INDEX idx_jt_active ON journal_templates(active);
CREATE INDEX idx_jt_is_favorite ON journal_templates(is_favorite);

CREATE TABLE journal_template_lines (
    id UUID PRIMARY KEY,
    id_journal_template UUID NOT NULL REFERENCES journal_templates(id) ON DELETE CASCADE,
    id_account UUID NOT NULL REFERENCES chart_of_accounts(id),
    position VARCHAR(10) NOT NULL,
    formula VARCHAR(255) NOT NULL DEFAULT 'amount',
    line_order INTEGER NOT NULL,
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_position CHECK (position IN ('DEBIT', 'CREDIT'))
);

CREATE INDEX idx_jtl_template ON journal_template_lines(id_journal_template);
CREATE INDEX idx_jtl_account ON journal_template_lines(id_account);

-- Pre-seed IT Services Journal Templates

-- Template: Pendapatan Jasa Konsultasi
INSERT INTO journal_templates (id, template_name, category, cash_flow_category, template_type, description, is_system, active) VALUES
('e0000000-0000-0000-0000-000000000001', 'Pendapatan Jasa Konsultasi', 'INCOME', 'OPERATING', 'SIMPLE', 'Template untuk mencatat pendapatan dari jasa konsultasi IT', TRUE, TRUE);

INSERT INTO journal_template_lines (id, id_journal_template, id_account, position, formula, line_order) VALUES
('e1000000-0000-0000-0000-000000000001', 'e0000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000102', 'DEBIT', 'amount', 1),
('e1000000-0000-0000-0000-000000000002', 'e0000000-0000-0000-0000-000000000001', '40000000-0000-0000-0000-000000000101', 'CREDIT', 'amount', 2);

-- Template: Pendapatan Jasa Development
INSERT INTO journal_templates (id, template_name, category, cash_flow_category, template_type, description, is_system, active) VALUES
('e0000000-0000-0000-0000-000000000002', 'Pendapatan Jasa Development', 'INCOME', 'OPERATING', 'SIMPLE', 'Template untuk mencatat pendapatan dari jasa development', TRUE, TRUE);

INSERT INTO journal_template_lines (id, id_journal_template, id_account, position, formula, line_order) VALUES
('e1000000-0000-0000-0000-000000000003', 'e0000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000102', 'DEBIT', 'amount', 1),
('e1000000-0000-0000-0000-000000000004', 'e0000000-0000-0000-0000-000000000002', '40000000-0000-0000-0000-000000000102', 'CREDIT', 'amount', 2);

-- Template: Pendapatan Jasa Training
INSERT INTO journal_templates (id, template_name, category, cash_flow_category, template_type, description, is_system, active) VALUES
('e0000000-0000-0000-0000-000000000003', 'Pendapatan Jasa Training', 'INCOME', 'OPERATING', 'SIMPLE', 'Template untuk mencatat pendapatan dari jasa training', TRUE, TRUE);

INSERT INTO journal_template_lines (id, id_journal_template, id_account, position, formula, line_order) VALUES
('e1000000-0000-0000-0000-000000000005', 'e0000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000102', 'DEBIT', 'amount', 1),
('e1000000-0000-0000-0000-000000000006', 'e0000000-0000-0000-0000-000000000003', '40000000-0000-0000-0000-000000000103', 'CREDIT', 'amount', 2);

-- Template: Bayar Beban Gaji
INSERT INTO journal_templates (id, template_name, category, cash_flow_category, template_type, description, is_system, active) VALUES
('e0000000-0000-0000-0000-000000000004', 'Bayar Beban Gaji', 'EXPENSE', 'OPERATING', 'SIMPLE', 'Template untuk mencatat pembayaran gaji karyawan', TRUE, TRUE);

INSERT INTO journal_template_lines (id, id_journal_template, id_account, position, formula, line_order) VALUES
('e1000000-0000-0000-0000-000000000007', 'e0000000-0000-0000-0000-000000000004', '50000000-0000-0000-0000-000000000101', 'DEBIT', 'amount', 1),
('e1000000-0000-0000-0000-000000000008', 'e0000000-0000-0000-0000-000000000004', '10000000-0000-0000-0000-000000000102', 'CREDIT', 'amount', 2);

-- Template: Bayar Beban Server & Cloud
INSERT INTO journal_templates (id, template_name, category, cash_flow_category, template_type, description, is_system, active) VALUES
('e0000000-0000-0000-0000-000000000005', 'Bayar Beban Server & Cloud', 'EXPENSE', 'OPERATING', 'SIMPLE', 'Template untuk mencatat pembayaran server dan cloud', TRUE, TRUE);

INSERT INTO journal_template_lines (id, id_journal_template, id_account, position, formula, line_order) VALUES
('e1000000-0000-0000-0000-000000000009', 'e0000000-0000-0000-0000-000000000005', '50000000-0000-0000-0000-000000000102', 'DEBIT', 'amount', 1),
('e1000000-0000-0000-0000-000000000010', 'e0000000-0000-0000-0000-000000000005', '10000000-0000-0000-0000-000000000102', 'CREDIT', 'amount', 2);

-- Template: Bayar Beban Software & Lisensi
INSERT INTO journal_templates (id, template_name, category, cash_flow_category, template_type, description, is_system, active) VALUES
('e0000000-0000-0000-0000-000000000006', 'Bayar Beban Software & Lisensi', 'EXPENSE', 'OPERATING', 'SIMPLE', 'Template untuk mencatat pembayaran software dan lisensi', TRUE, TRUE);

INSERT INTO journal_template_lines (id, id_journal_template, id_account, position, formula, line_order) VALUES
('e1000000-0000-0000-0000-000000000011', 'e0000000-0000-0000-0000-000000000006', '50000000-0000-0000-0000-000000000103', 'DEBIT', 'amount', 1),
('e1000000-0000-0000-0000-000000000012', 'e0000000-0000-0000-0000-000000000006', '10000000-0000-0000-0000-000000000102', 'CREDIT', 'amount', 2);

-- Template: Bayar Beban Internet
INSERT INTO journal_templates (id, template_name, category, cash_flow_category, template_type, description, is_system, active) VALUES
('e0000000-0000-0000-0000-000000000007', 'Bayar Beban Internet & Telekomunikasi', 'EXPENSE', 'OPERATING', 'SIMPLE', 'Template untuk mencatat pembayaran internet dan telekomunikasi', TRUE, TRUE);

INSERT INTO journal_template_lines (id, id_journal_template, id_account, position, formula, line_order) VALUES
('e1000000-0000-0000-0000-000000000013', 'e0000000-0000-0000-0000-000000000007', '50000000-0000-0000-0000-000000000104', 'DEBIT', 'amount', 1),
('e1000000-0000-0000-0000-000000000014', 'e0000000-0000-0000-0000-000000000007', '10000000-0000-0000-0000-000000000102', 'CREDIT', 'amount', 2);

-- Template: Bayar Beban Administrasi
INSERT INTO journal_templates (id, template_name, category, cash_flow_category, template_type, description, is_system, active) VALUES
('e0000000-0000-0000-0000-000000000008', 'Bayar Beban Administrasi & Umum', 'EXPENSE', 'OPERATING', 'SIMPLE', 'Template untuk mencatat pembayaran beban administrasi dan umum', TRUE, TRUE);

INSERT INTO journal_template_lines (id, id_journal_template, id_account, position, formula, line_order) VALUES
('e1000000-0000-0000-0000-000000000015', 'e0000000-0000-0000-0000-000000000008', '50000000-0000-0000-0000-000000000105', 'DEBIT', 'amount', 1),
('e1000000-0000-0000-0000-000000000016', 'e0000000-0000-0000-0000-000000000008', '10000000-0000-0000-0000-000000000102', 'CREDIT', 'amount', 2);

-- Template: Transfer Antar Bank
INSERT INTO journal_templates (id, template_name, category, cash_flow_category, template_type, description, is_system, active) VALUES
('e0000000-0000-0000-0000-000000000009', 'Transfer Antar Bank', 'TRANSFER', 'OPERATING', 'SIMPLE', 'Template untuk mencatat transfer antar rekening bank', TRUE, TRUE);

INSERT INTO journal_template_lines (id, id_journal_template, id_account, position, formula, line_order) VALUES
('e1000000-0000-0000-0000-000000000017', 'e0000000-0000-0000-0000-000000000009', '10000000-0000-0000-0000-000000000102', 'DEBIT', 'amount', 1),
('e1000000-0000-0000-0000-000000000018', 'e0000000-0000-0000-0000-000000000009', '10000000-0000-0000-0000-000000000103', 'CREDIT', 'amount', 2);

-- Template: Terima Pelunasan Piutang
INSERT INTO journal_templates (id, template_name, category, cash_flow_category, template_type, description, is_system, active) VALUES
('e0000000-0000-0000-0000-000000000010', 'Terima Pelunasan Piutang', 'RECEIPT', 'OPERATING', 'SIMPLE', 'Template untuk mencatat penerimaan pelunasan piutang', TRUE, TRUE);

INSERT INTO journal_template_lines (id, id_journal_template, id_account, position, formula, line_order) VALUES
('e1000000-0000-0000-0000-000000000019', 'e0000000-0000-0000-0000-000000000010', '10000000-0000-0000-0000-000000000102', 'DEBIT', 'amount', 1),
('e1000000-0000-0000-0000-000000000020', 'e0000000-0000-0000-0000-000000000010', '10000000-0000-0000-0000-000000000104', 'CREDIT', 'amount', 2);

-- Template: Bayar Hutang Usaha
INSERT INTO journal_templates (id, template_name, category, cash_flow_category, template_type, description, is_system, active) VALUES
('e0000000-0000-0000-0000-000000000011', 'Bayar Hutang Usaha', 'PAYMENT', 'OPERATING', 'SIMPLE', 'Template untuk mencatat pembayaran hutang usaha', TRUE, TRUE);

INSERT INTO journal_template_lines (id, id_journal_template, id_account, position, formula, line_order) VALUES
('e1000000-0000-0000-0000-000000000021', 'e0000000-0000-0000-0000-000000000011', '20000000-0000-0000-0000-000000000101', 'DEBIT', 'amount', 1),
('e1000000-0000-0000-0000-000000000022', 'e0000000-0000-0000-0000-000000000011', '10000000-0000-0000-0000-000000000102', 'CREDIT', 'amount', 2);

-- Template: Setoran Modal
INSERT INTO journal_templates (id, template_name, category, cash_flow_category, template_type, description, is_system, active) VALUES
('e0000000-0000-0000-0000-000000000012', 'Setoran Modal', 'RECEIPT', 'FINANCING', 'SIMPLE', 'Template untuk mencatat setoran modal dari pemilik', TRUE, TRUE);

INSERT INTO journal_template_lines (id, id_journal_template, id_account, position, formula, line_order) VALUES
('e1000000-0000-0000-0000-000000000023', 'e0000000-0000-0000-0000-000000000012', '10000000-0000-0000-0000-000000000102', 'DEBIT', 'amount', 1),
('e1000000-0000-0000-0000-000000000024', 'e0000000-0000-0000-0000-000000000012', '30000000-0000-0000-0000-000000000101', 'CREDIT', 'amount', 2);
