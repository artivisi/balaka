-- V913: Screenshot Test Data
-- Rich test data for user manual screenshots
-- This migration builds on top of V800 (base), V810/V811 (service), V820/V821 (seller)
--
-- ID Convention: f913xxxx prefix for screenshot-specific data

-- ============================================
-- Link admin user to employee for self-service screenshots
-- ============================================
UPDATE employees SET id_user = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'
WHERE id = '51300001-0000-0000-0000-000000000001';

-- ============================================
-- Fixed Assets (using V800 asset_categories)
-- ============================================
INSERT INTO fixed_assets (
    id, asset_code, name, description, id_category,
    purchase_date, purchase_cost, supplier, invoice_number,
    depreciation_method, useful_life_months, residual_value, depreciation_start_date,
    accumulated_depreciation, book_value, last_depreciation_date, depreciation_periods_completed,
    status, id_asset_account, id_accumulated_depreciation_account, id_depreciation_expense_account,
    created_at, updated_at
) VALUES
-- Laptop MacBook Pro (Peralatan Komputer category: a0000000-0000-0000-0000-000000000001)
('f9130001-0000-0000-0000-000000000001', 'AST-2024-001', 'MacBook Pro 14" M3', 'Laptop untuk developer senior',
 'a0000000-0000-0000-0000-000000000001', '2024-01-15', 35000000, 'iBox Indonesia', 'INV-IBOX-2024-001',
 'STRAIGHT_LINE', 48, 3500000, '2024-02-01',
 6562500, 28437500, '2024-11-30', 10,
 'ACTIVE', '10000000-0000-0000-0000-000000000121', '10000000-0000-0000-0000-000000000122', '50000000-0000-0000-0000-000000000107',
 NOW(), NOW()),
-- Monitor Dell (Peralatan Komputer category)
('f9130002-0000-0000-0000-000000000001', 'AST-2024-002', 'Monitor Dell 27" 4K', 'Monitor untuk workstation',
 'a0000000-0000-0000-0000-000000000001', '2024-02-01', 8500000, 'Bhinneka', 'INV-BHN-2024-002',
 'STRAIGHT_LINE', 48, 850000, '2024-03-01',
 1434375, 7065625, '2024-11-30', 9,
 'ACTIVE', '10000000-0000-0000-0000-000000000121', '10000000-0000-0000-0000-000000000122', '50000000-0000-0000-0000-000000000107',
 NOW(), NOW()),
-- Kendaraan Toyota Avanza (Kendaraan category: a0000000-0000-0000-0000-000000000002)
('f9130003-0000-0000-0000-000000000001', 'AST-2023-001', 'Toyota Avanza Veloz', 'Kendaraan operasional kantor',
 'a0000000-0000-0000-0000-000000000002', '2023-06-01', 280000000, 'Auto2000', 'INV-AUTO-2023-001',
 'STRAIGHT_LINE', 96, 28000000, '2023-07-01',
 44625000, 235375000, '2024-11-30', 17,
 'ACTIVE', '10000000-0000-0000-0000-000000000141', '10000000-0000-0000-0000-000000000142', '50000000-0000-0000-0000-000000000107',
 NOW(), NOW()),
-- Meja Kantor (Peralatan Kantor category: a0000000-0000-0000-0000-000000000003)
('f9130004-0000-0000-0000-000000000001', 'AST-2024-003', 'Meja Kerja Ergonomis', 'Meja standing desk',
 'a0000000-0000-0000-0000-000000000003', '2024-03-01', 5500000, 'IKEA Indonesia', 'INV-IKEA-2024-001',
 'STRAIGHT_LINE', 48, 550000, '2024-04-01',
 825000, 4675000, '2024-11-30', 8,
 'ACTIVE', '10000000-0000-0000-0000-000000000143', '10000000-0000-0000-0000-000000000144', '50000000-0000-0000-0000-000000000107',
 NOW(), NOW());

-- Depreciation entries for MacBook (f9130001)
INSERT INTO depreciation_entries (
    id, id_fixed_asset, period_number, period_start, period_end,
    depreciation_amount, accumulated_depreciation, book_value,
    status, generated_at, posted_at, created_at, updated_at
) VALUES
('de913001-0000-0000-0000-000000000001', 'f9130001-0000-0000-0000-000000000001', 1, '2024-02-01', '2024-02-29', 656250, 656250, 34343750, 'POSTED', '2024-02-28', '2024-02-28', NOW(), NOW()),
('de913002-0000-0000-0000-000000000001', 'f9130001-0000-0000-0000-000000000001', 2, '2024-03-01', '2024-03-31', 656250, 1312500, 33687500, 'POSTED', '2024-03-31', '2024-03-31', NOW(), NOW()),
('de913003-0000-0000-0000-000000000001', 'f9130001-0000-0000-0000-000000000001', 3, '2024-04-01', '2024-04-30', 656250, 1968750, 33031250, 'POSTED', '2024-04-30', '2024-04-30', NOW(), NOW()),
('de913004-0000-0000-0000-000000000001', 'f9130001-0000-0000-0000-000000000001', 4, '2024-05-01', '2024-05-31', 656250, 2625000, 32375000, 'POSTED', '2024-05-31', '2024-05-31', NOW(), NOW()),
('de913005-0000-0000-0000-000000000001', 'f9130001-0000-0000-0000-000000000001', 5, '2024-06-01', '2024-06-30', 656250, 3281250, 31718750, 'POSTED', '2024-06-30', '2024-06-30', NOW(), NOW()),
('de913006-0000-0000-0000-000000000001', 'f9130001-0000-0000-0000-000000000001', 6, '2024-07-01', '2024-07-31', 656250, 3937500, 31062500, 'POSTED', '2024-07-31', '2024-07-31', NOW(), NOW()),
('de913007-0000-0000-0000-000000000001', 'f9130001-0000-0000-0000-000000000001', 7, '2024-08-01', '2024-08-31', 656250, 4593750, 30406250, 'POSTED', '2024-08-31', '2024-08-31', NOW(), NOW()),
('de913008-0000-0000-0000-000000000001', 'f9130001-0000-0000-0000-000000000001', 8, '2024-09-01', '2024-09-30', 656250, 5250000, 29750000, 'POSTED', '2024-09-30', '2024-09-30', NOW(), NOW()),
('de913009-0000-0000-0000-000000000001', 'f9130001-0000-0000-0000-000000000001', 9, '2024-10-01', '2024-10-31', 656250, 5906250, 29093750, 'POSTED', '2024-10-31', '2024-10-31', NOW(), NOW()),
('de913010-0000-0000-0000-000000000001', 'f9130001-0000-0000-0000-000000000001', 10, '2024-11-01', '2024-11-30', 656250, 6562500, 28437500, 'POSTED', '2024-11-30', '2024-11-30', NOW(), NOW()),
('de913011-0000-0000-0000-000000000001', 'f9130001-0000-0000-0000-000000000001', 11, '2024-12-01', '2024-12-31', 656250, 7218750, 27781250, 'PENDING', NULL, NULL, NOW(), NOW());

-- ============================================
-- Bill of Materials (using V820 products)
-- Product: USB Cable (52200003) as finished good, using iPhone/Samsung as components (just for demo)
-- ============================================
INSERT INTO bill_of_materials (
    id, id_product, code, name, description, output_quantity, active, created_at, updated_at
) VALUES
('f9130010-0000-0000-0000-000000000001', '52200003-0000-0000-0000-000000000001', 'BOM-BUNDLE-001', 'Paket Aksesori HP', 'Paket bundle USB Cable dan Case', 1, true, NOW(), NOW());

INSERT INTO bill_of_material_lines (
    id, id_bill_of_material, id_component, quantity, line_order, created_at, updated_at
) VALUES
('f9130011-0000-0000-0000-000000000001', 'f9130010-0000-0000-0000-000000000001', '52200003-0000-0000-0000-000000000001', 1.00, 1, NOW(), NOW()),
('f9130012-0000-0000-0000-000000000001', 'f9130010-0000-0000-0000-000000000001', '52200004-0000-0000-0000-000000000001', 1.00, 2, NOW(), NOW());

-- ============================================
-- Production Orders (using BOM above)
-- ============================================
INSERT INTO production_orders (
    id, order_number, id_bill_of_material, quantity, order_date, planned_completion_date, actual_completion_date,
    status, notes, total_component_cost, unit_cost, created_at, updated_at
) VALUES
('f9130020-0000-0000-0000-000000000001', 'PROD-2024-001', 'f9130010-0000-0000-0000-000000000001', 20, '2024-11-15', '2024-11-20', '2024-11-18', 'COMPLETED', 'Produksi paket aksesori', 1700000, 85000, NOW(), NOW()),
('f9130021-0000-0000-0000-000000000001', 'PROD-2024-002', 'f9130010-0000-0000-0000-000000000001', 30, '2024-11-25', '2024-11-30', '2024-11-28', 'COMPLETED', 'Produksi untuk promo', 2550000, 85000, NOW(), NOW()),
('f9130022-0000-0000-0000-000000000001', 'PROD-2024-003', 'f9130010-0000-0000-0000-000000000001', 50, '2024-12-05', '2024-12-10', NULL, 'DRAFT', 'Produksi untuk Natal', 0, 0, NOW(), NOW());

-- ============================================
-- Additional Transactions for Screenshots
-- Using V810's Service Industry templates and accounts
-- ============================================
INSERT INTO transactions (
    id, transaction_number, transaction_date, id_journal_template, description,
    status, amount, id_project,
    created_at, updated_at
) VALUES
('f9130030-0000-0000-0000-000000000001', 'TRX-SHOT-001', '2024-11-01', 'e0000000-0000-0000-0000-000000000001', 'Pendapatan jasa konsultasi IT - Screenshot Demo',
 'POSTED', 25000000, '51200001-0000-0000-0000-000000000001', NOW(), NOW()),
('f9130031-0000-0000-0000-000000000001', 'TRX-SHOT-002', '2024-11-05', 'e0000000-0000-0000-0000-000000000008', 'Pembelian peralatan kantor - Screenshot Demo',
 'POSTED', 5500000, NULL, NOW(), NOW()),
('f9130032-0000-0000-0000-000000000001', 'TRX-SHOT-003', '2024-11-10', 'e0000000-0000-0000-0000-000000000004', 'Pembayaran gaji karyawan November - Screenshot Demo',
 'POSTED', 36000000, NULL, NOW(), NOW()),
('f9130033-0000-0000-0000-000000000001', 'TRX-SHOT-004', '2024-11-15', 'e0000000-0000-0000-0000-000000000010', 'Penerimaan kas dari pelanggan - Screenshot Demo',
 'POSTED', 15000000, NULL, NOW(), NOW()),
('f9130034-0000-0000-0000-000000000001', 'TRX-SHOT-005', '2024-11-20', 'e0000000-0000-0000-0000-000000000008', 'Pembayaran sewa kantor Desember - Screenshot Demo',
 'POSTED', 8000000, NULL, NOW(), NOW());

-- Journal entries (debit/credit lines for screenshot transactions)
INSERT INTO journal_entries (
    id, id_transaction, id_account, debit_amount, credit_amount, created_at, updated_at, line_order
) VALUES
-- TRX-SHOT-001: Pendapatan Jasa
('f9130040-0000-0000-0000-000000000001', 'f9130030-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000104', 25000000, 0, NOW(), NOW(), 1),
('f9130041-0000-0000-0000-000000000001', 'f9130030-0000-0000-0000-000000000001', '40000000-0000-0000-0000-000000000101', 0, 25000000, NOW(), NOW(), 2),
-- TRX-SHOT-002: Pembelian Peralatan
('f9130042-0000-0000-0000-000000000001', 'f9130031-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000143', 5500000, 0, NOW(), NOW(), 1),
('f9130043-0000-0000-0000-000000000001', 'f9130031-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000102', 0, 5500000, NOW(), NOW(), 2),
-- TRX-SHOT-003: Gaji
('f9130044-0000-0000-0000-000000000001', 'f9130032-0000-0000-0000-000000000001', '50000000-0000-0000-0000-000000000101', 36000000, 0, NOW(), NOW(), 1),
('f9130045-0000-0000-0000-000000000001', 'f9130032-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000102', 0, 36000000, NOW(), NOW(), 2),
-- TRX-SHOT-004: Penerimaan Kas
('f9130046-0000-0000-0000-000000000001', 'f9130033-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000102', 15000000, 0, NOW(), NOW(), 1),
('f9130047-0000-0000-0000-000000000001', 'f9130033-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000104', 0, 15000000, NOW(), NOW(), 2),
-- TRX-SHOT-005: Sewa
('f9130048-0000-0000-0000-000000000001', 'f9130034-0000-0000-0000-000000000001', '50000000-0000-0000-0000-000000000104', 8000000, 0, NOW(), NOW(), 1),
('f9130049-0000-0000-0000-000000000001', 'f9130034-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000102', 0, 8000000, NOW(), NOW(), 2);

-- ============================================
-- Additional Payroll Runs for bukti potong screenshots
-- Using V810's employees (51300001-51300003)
-- ============================================

-- July 2024 payroll (for bukti potong history)
INSERT INTO payroll_runs (id, payroll_period, period_start, period_end, status, total_gross, total_deductions, total_net_pay, total_pph21, employee_count, created_at, updated_at)
VALUES ('f9130050-0000-0000-0000-000000000001', '2024-07', '2024-07-01', '2024-07-31', 'POSTED', 36000000, 5400000, 30600000, 2760000, 3, NOW(), NOW());

INSERT INTO payroll_details (id, id_payroll_run, id_employee, base_salary, gross_salary, pph21, bpjs_jht_employee, bpjs_jp_employee, bpjs_kes_employee, total_deductions, net_pay, jkk_risk_class, created_at, updated_at)
VALUES
('f9130051-0000-0000-0000-000000000001', 'f9130050-0000-0000-0000-000000000001', '51300001-0000-0000-0000-000000000001', 12000000, 12000000, 720000, 240000, 24000, 60000, 1044000, 10956000, 2, NOW(), NOW()),
('f9130052-0000-0000-0000-000000000001', 'f9130050-0000-0000-0000-000000000001', '51300002-0000-0000-0000-000000000001', 12000000, 12000000, 600000, 240000, 24000, 60000, 924000, 11076000, 2, NOW(), NOW()),
('f9130053-0000-0000-0000-000000000001', 'f9130050-0000-0000-0000-000000000001', '51300003-0000-0000-0000-000000000001', 12000000, 12000000, 1440000, 240000, 24000, 60000, 1764000, 10236000, 2, NOW(), NOW());

-- August 2024 payroll
INSERT INTO payroll_runs (id, payroll_period, period_start, period_end, status, total_gross, total_deductions, total_net_pay, total_pph21, employee_count, created_at, updated_at)
VALUES ('f9130060-0000-0000-0000-000000000001', '2024-08', '2024-08-01', '2024-08-31', 'POSTED', 36000000, 5400000, 30600000, 2760000, 3, NOW(), NOW());

INSERT INTO payroll_details (id, id_payroll_run, id_employee, base_salary, gross_salary, pph21, bpjs_jht_employee, bpjs_jp_employee, bpjs_kes_employee, total_deductions, net_pay, jkk_risk_class, created_at, updated_at)
VALUES
('f9130061-0000-0000-0000-000000000001', 'f9130060-0000-0000-0000-000000000001', '51300001-0000-0000-0000-000000000001', 12000000, 12000000, 720000, 240000, 24000, 60000, 1044000, 10956000, 2, NOW(), NOW()),
('f9130062-0000-0000-0000-000000000001', 'f9130060-0000-0000-0000-000000000001', '51300002-0000-0000-0000-000000000001', 12000000, 12000000, 600000, 240000, 24000, 60000, 924000, 11076000, 2, NOW(), NOW()),
('f9130063-0000-0000-0000-000000000001', 'f9130060-0000-0000-0000-000000000001', '51300003-0000-0000-0000-000000000001', 12000000, 12000000, 1440000, 240000, 24000, 60000, 1764000, 10236000, 2, NOW(), NOW());

-- ============================================
-- More Fiscal Periods for fiscal closing screenshots
-- Note: V810 already has Jan-Dec 2024, we add 2023 periods
-- ============================================
INSERT INTO fiscal_periods (
    id, year, month, status, month_closed_at, tax_filed_at, notes, created_at, updated_at
) VALUES
-- 2023 - All months TAX_FILED
('f9130070-0000-0000-0000-000000000001', 2023, 1, 'TAX_FILED', '2023-02-10', '2023-02-15', 'Periode Januari 2023', NOW(), NOW()),
('f9130071-0000-0000-0000-000000000001', 2023, 2, 'TAX_FILED', '2023-03-10', '2023-03-15', 'Periode Februari 2023', NOW(), NOW()),
('f9130072-0000-0000-0000-000000000001', 2023, 3, 'TAX_FILED', '2023-04-10', '2023-04-15', 'Periode Maret 2023', NOW(), NOW()),
('f9130073-0000-0000-0000-000000000001', 2023, 4, 'TAX_FILED', '2023-05-10', '2023-05-15', 'Periode April 2023', NOW(), NOW()),
('f9130074-0000-0000-0000-000000000001', 2023, 5, 'TAX_FILED', '2023-06-10', '2023-06-15', 'Periode Mei 2023', NOW(), NOW()),
('f9130075-0000-0000-0000-000000000001', 2023, 6, 'TAX_FILED', '2023-07-10', '2023-07-15', 'Periode Juni 2023', NOW(), NOW()),
('f9130076-0000-0000-0000-000000000001', 2023, 7, 'TAX_FILED', '2023-08-10', '2023-08-15', 'Periode Juli 2023', NOW(), NOW()),
('f9130077-0000-0000-0000-000000000001', 2023, 8, 'TAX_FILED', '2023-09-10', '2023-09-15', 'Periode Agustus 2023', NOW(), NOW()),
('f9130078-0000-0000-0000-000000000001', 2023, 9, 'TAX_FILED', '2023-10-10', '2023-10-15', 'Periode September 2023', NOW(), NOW()),
('f9130079-0000-0000-0000-000000000001', 2023, 10, 'TAX_FILED', '2023-11-10', '2023-11-15', 'Periode Oktober 2023', NOW(), NOW()),
('f9130080-0000-0000-0000-000000000001', 2023, 11, 'TAX_FILED', '2023-12-10', '2023-12-15', 'Periode November 2023', NOW(), NOW()),
('f9130081-0000-0000-0000-000000000001', 2023, 12, 'TAX_FILED', '2024-01-10', '2024-01-15', 'Periode Desember 2023', NOW(), NOW());

-- Update 2024 fiscal periods status for screenshot variety
UPDATE fiscal_periods SET status = 'MONTH_CLOSED', month_closed_at = '2024-12-05' WHERE year = 2024 AND month = 11;
UPDATE fiscal_periods SET status = 'OPEN' WHERE year = 2024 AND month = 12;
