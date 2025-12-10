-- V810: Service Industry Test Data
-- Company: PT ArtiVisi Intermedia (IT Consulting, PKP)
-- ID Convention: 51=Service, 1x=Client, 2x=Project, 3x=Employee, 4x=Transaction, 5x=Fiscal
-- Example: 51100001 = Service Client #1

-- ============================================
-- Company Configuration
-- ============================================
INSERT INTO company_config (id, company_name, company_address, company_phone, company_email, tax_id, npwp, fiscal_year_start_month, currency_code, signing_officer_name, signing_officer_title)
VALUES (
    '51000000-0000-0000-0000-000000000001',
    'PT ArtiVisi Intermedia',
    'Jl. Margonda Raya No. 100, Depok, Jawa Barat 16424',
    '021-7888-9999',
    'info@artivisi.com',
    '01.234.567.8-901.000',
    '01.234.567.8-901.000',
    1,
    'IDR',
    'Endy Muhardin',
    'Direktur'
);

-- ============================================
-- Fiscal Periods (2024 - 12 months)
-- ============================================
INSERT INTO fiscal_periods (id, year, month, status, notes)
VALUES
    ('51500001-0000-0000-0000-000000000001', 2024, 1, 'OPEN', 'Januari 2024'),
    ('51500002-0000-0000-0000-000000000001', 2024, 2, 'OPEN', 'Februari 2024'),
    ('51500003-0000-0000-0000-000000000001', 2024, 3, 'OPEN', 'Maret 2024'),
    ('51500004-0000-0000-0000-000000000001', 2024, 4, 'OPEN', 'April 2024'),
    ('51500005-0000-0000-0000-000000000001', 2024, 5, 'OPEN', 'Mei 2024'),
    ('51500006-0000-0000-0000-000000000001', 2024, 6, 'OPEN', 'Juni 2024'),
    ('51500007-0000-0000-0000-000000000001', 2024, 7, 'OPEN', 'Juli 2024'),
    ('51500008-0000-0000-0000-000000000001', 2024, 8, 'OPEN', 'Agustus 2024'),
    ('51500009-0000-0000-0000-000000000001', 2024, 9, 'OPEN', 'September 2024'),
    ('51500010-0000-0000-0000-000000000001', 2024, 10, 'OPEN', 'Oktober 2024'),
    ('51500011-0000-0000-0000-000000000001', 2024, 11, 'OPEN', 'November 2024'),
    ('51500012-0000-0000-0000-000000000001', 2024, 12, 'OPEN', 'Desember 2024');

-- ============================================
-- Clients (PKP companies that withhold PPh 23)
-- ============================================
INSERT INTO clients (id, code, name, address, phone, email, npwp, contact_person, active)
VALUES
    ('51100001-0000-0000-0000-000000000001', 'CLI-001', 'PT Bank Mandiri (Persero) Tbk', 'Jl. Jend. Gatot Subroto Kav. 36-38, Jakarta', '021-5249797', 'info@bankmandiri.co.id', '01.310.523.4-091.000', 'Budi Gunawan', TRUE),
    ('51100002-0000-0000-0000-000000000001', 'CLI-002', 'PT Telkom Indonesia (Persero) Tbk', 'Jl. Japati No. 1, Bandung', '022-4521234', 'info@telkom.co.id', '01.000.013.1-093.000', 'Ririek Adriansyah', TRUE),
    ('51100003-0000-0000-0000-000000000001', 'CLI-003', 'PT Pertamina (Persero)', 'Jl. Medan Merdeka Timur No. 1A, Jakarta', '021-3815111', 'pcc@pertamina.com', '01.002.028.1-093.000', 'Nicke Widyawati', TRUE);

-- ============================================
-- Projects
-- ============================================
INSERT INTO projects (id, code, name, id_client, description, start_date, end_date, contract_value, status)
VALUES
    -- Bank Mandiri Projects
    ('51200001-0000-0000-0000-000000000001', 'PRJ-2024-001', 'Core Banking System Consultation', '51100001-0000-0000-0000-000000000001', 'Konsultasi arsitektur sistem core banking', '2024-01-01', '2024-06-30', 600000000, 'ACTIVE'),
    ('51200002-0000-0000-0000-000000000001', 'PRJ-2024-002', 'IT Security Training', '51100001-0000-0000-0000-000000000001', 'Pelatihan keamanan IT untuk tim internal', '2024-02-01', '2024-02-28', 150000000, 'COMPLETED'),
    -- Telkom Projects
    ('51200003-0000-0000-0000-000000000001', 'PRJ-2024-003', 'Digital Transformation Roadmap', '51100002-0000-0000-0000-000000000001', 'Penyusunan roadmap transformasi digital', '2024-01-15', '2024-04-15', 450000000, 'ACTIVE'),
    -- Pertamina Projects
    ('51200004-0000-0000-0000-000000000001', 'PRJ-2024-004', 'ERP Implementation Support', '51100003-0000-0000-0000-000000000001', 'Pendampingan implementasi SAP S/4HANA', '2024-03-01', '2024-12-31', 1200000000, 'ACTIVE');

-- ============================================
-- Project Milestones
-- ============================================
INSERT INTO project_milestones (id, id_project, sequence, name, description, target_date, weight_percent, status, actual_date)
VALUES
    -- Core Banking Milestones
    ('51210001-0000-0000-0000-000000000001', '51200001-0000-0000-0000-000000000001', 1, 'Requirement Analysis', 'Analisis kebutuhan sistem', '2024-02-15', 30, 'COMPLETED', '2024-02-10'),
    ('51210002-0000-0000-0000-000000000001', '51200001-0000-0000-0000-000000000001', 2, 'Architecture Design', 'Desain arsitektur sistem', '2024-04-15', 40, 'PENDING', NULL),
    ('51210003-0000-0000-0000-000000000001', '51200001-0000-0000-0000-000000000001', 3, 'Final Report', 'Laporan akhir dan rekomendasi', '2024-06-30', 30, 'PENDING', NULL),
    -- Training Milestones
    ('51210004-0000-0000-0000-000000000001', '51200002-0000-0000-0000-000000000001', 1, 'Training Execution', 'Pelaksanaan training', '2024-02-28', 100, 'COMPLETED', '2024-02-28');

-- ============================================
-- Employees
-- ============================================
INSERT INTO employees (id, employee_id, name, email, phone, address, hire_date, npwp, nik_ktp, ptkp_status, employment_type, employment_status, job_title, department, bank_name, bank_account_number, bank_account_name)
VALUES
    ('51300001-0000-0000-0000-000000000001', 'EMP-001', 'Budi Santoso', 'budi@artivisi.com', '081234567801', 'Jl. Kenanga No. 10, Depok', '2020-01-15', '12.345.678.9-012.000', '3276012345670001', 'K_2', 'PERMANENT', 'ACTIVE', 'Senior Consultant', 'Consulting', 'BCA', '1234567890', 'Budi Santoso'),
    ('51300002-0000-0000-0000-000000000001', 'EMP-002', 'Dewi Lestari', 'dewi@artivisi.com', '081234567802', 'Jl. Melati No. 20, Jakarta Selatan', '2021-03-01', '12.345.678.9-013.000', '3276012345670002', 'TK_0', 'PERMANENT', 'ACTIVE', 'Consultant', 'Consulting', 'BCA', '1234567891', 'Dewi Lestari'),
    ('51300003-0000-0000-0000-000000000001', 'EMP-003', 'Agus Wijaya', 'agus@artivisi.com', '081234567803', 'Jl. Mawar No. 30, Bogor', '2023-06-01', '12.345.678.9-014.000', '3276012345670003', 'TK_0', 'CONTRACT', 'ACTIVE', 'Junior Consultant', 'Consulting', 'Mandiri', '9876543210', 'Agus Wijaya');

-- ============================================
-- Employee Salary Components
-- ============================================
-- Budi Santoso - Senior Consultant (Rp 15,000,000 base)
INSERT INTO employee_salary_components (id, employee_id, salary_component_id, amount, effective_date, notes)
VALUES
    ('51310001-0000-0000-0000-000000000001', '51300001-0000-0000-0000-000000000001', 'c0000000-0000-0000-0000-000000000001', 15000000, '2024-01-01', 'Gaji pokok 2024'),
    ('51310002-0000-0000-0000-000000000001', '51300001-0000-0000-0000-000000000001', 'c0000000-0000-0000-0000-000000000004', 2000000, '2024-01-01', 'Tunjangan jabatan Senior');

-- Dewi Lestari - Consultant (Rp 10,000,000 base)
INSERT INTO employee_salary_components (id, employee_id, salary_component_id, amount, effective_date, notes)
VALUES
    ('51310003-0000-0000-0000-000000000001', '51300002-0000-0000-0000-000000000001', 'c0000000-0000-0000-0000-000000000001', 10000000, '2024-01-01', 'Gaji pokok 2024'),
    ('51310004-0000-0000-0000-000000000001', '51300002-0000-0000-0000-000000000001', 'c0000000-0000-0000-0000-000000000004', 1000000, '2024-01-01', 'Tunjangan jabatan Consultant');

-- Agus Wijaya - Junior Consultant (Rp 5,000,000 base)
INSERT INTO employee_salary_components (id, employee_id, salary_component_id, amount, effective_date, notes)
VALUES
    ('51310005-0000-0000-0000-000000000001', '51300003-0000-0000-0000-000000000001', 'c0000000-0000-0000-0000-000000000001', 5000000, '2024-01-01', 'Gaji pokok 2024');

-- ============================================
-- Test Users (Operator and Auditor)
-- ============================================
INSERT INTO users (id, username, password, full_name, email, active, created_at, updated_at)
VALUES
    ('51600001-0000-0000-0000-000000000001', 'operator', '$2a$10$mMan.18CFTqJA/FVpkJr3OgCD0uTuhF9Enjf99QHm9tWPJH.nCj5S', 'Operator Test', 'operator@artivisi.com', TRUE, NOW(), NOW()),
    ('51600002-0000-0000-0000-000000000001', 'auditor', '$2a$10$mMan.18CFTqJA/FVpkJr3OgCD0uTuhF9Enjf99QHm9tWPJH.nCj5S', 'Auditor Test', 'auditor@artivisi.com', TRUE, NOW(), NOW());

INSERT INTO user_roles (id, id_user, role, created_at, created_by)
VALUES
    ('51610001-0000-0000-0000-000000000001', '51600001-0000-0000-0000-000000000001', 'OPERATOR', NOW(), 'system'),
    ('51610002-0000-0000-0000-000000000001', '51600002-0000-0000-0000-000000000001', 'AUDITOR', NOW(), 'system');
