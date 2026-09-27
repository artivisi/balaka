-- V905: Profitability Report Test Data
-- Test data for Project Profitability, Client Profitability, and Client Ranking reports
-- This migration runs only in test profile

-- =============================================================================
-- TEST DATA SUMMARY
-- =============================================================================
-- Client 1 (PT ABC): 2 projects with revenue and expenses
-- Client 2 (PT XYZ): 1 project with revenue only
-- Client 3 (CV DEF): 1 project with no activity (should not appear in ranking)
--
-- Project Profitability:
--   PRJ-001: Revenue 25M, Expense 10M, Profit 15M (60% margin)
--   PRJ-002: Revenue 12M, Expense 8M, Profit 4M (33.33% margin)
--   PRJ-003: Revenue 18M, Expense 0, Profit 18M (100% margin)
--
-- Client Profitability:
--   PT ABC: Revenue 37M, Profit 19M (from PRJ-001 + PRJ-002)
--   PT XYZ: Revenue 18M, Profit 18M (from PRJ-003)
--
-- Client Ranking (by revenue):
--   1. PT ABC - 37M (67.27%)
--   2. PT XYZ - 18M (32.73%)

-- =============================================================================
-- CLIENTS
-- =============================================================================
INSERT INTO clients (id, code, name, email, phone, address, notes, active, created_at, updated_at)
VALUES
('c0500000-0000-0000-0000-000000000001', 'CLI-001', 'PT ABC Technology', 'abc@example.com', '021-1234567', 'Jakarta', 'Test client 1', true, NOW(), NOW()),
('c0500000-0000-0000-0000-000000000002', 'CLI-002', 'PT XYZ Solutions', 'xyz@example.com', '021-7654321', 'Bandung', 'Test client 2', true, NOW(), NOW()),
('c0500000-0000-0000-0000-000000000003', 'CLI-003', 'CV DEF Services', 'def@example.com', '021-9876543', 'Surabaya', 'Test client 3 - no project activity', true, NOW(), NOW());

-- =============================================================================
-- PROJECTS
-- =============================================================================
INSERT INTO projects (id, code, name, description, status, id_client, start_date, end_date, contract_value, budget_amount, created_at, updated_at)
VALUES
-- PT ABC Project 1: Website Development
('a0500000-0000-0000-0000-000000000001', 'PRJ-TEST-001', 'Website Development ABC', 'Corporate website development', 'ACTIVE', 'c0500000-0000-0000-0000-000000000001', '2024-01-01', '2024-06-30', 30000000, 12000000, NOW(), NOW()),
-- PT ABC Project 2: Mobile App
('a0500000-0000-0000-0000-000000000002', 'PRJ-TEST-002', 'Mobile App ABC', 'Mobile application development', 'ACTIVE', 'c0500000-0000-0000-0000-000000000001', '2024-03-01', '2024-09-30', 15000000, 10000000, NOW(), NOW()),
-- PT XYZ Project 1: Consulting
('a0500000-0000-0000-0000-000000000003', 'PRJ-TEST-003', 'IT Consulting XYZ', 'IT strategy consulting', 'ACTIVE', 'c0500000-0000-0000-0000-000000000002', '2024-02-01', '2024-08-31', 20000000, 5000000, NOW(), NOW()),
-- CV DEF Project: No activity
('a0500000-0000-0000-0000-000000000004', 'PRJ-TEST-004', 'Infrastructure DEF', 'Infrastructure upgrade', 'ACTIVE', 'c0500000-0000-0000-0000-000000000003', '2024-07-01', '2024-12-31', 50000000, 40000000, NOW(), NOW());

-- =============================================================================
-- PROJECT MILESTONES (for overdue detection test)
-- =============================================================================
INSERT INTO project_milestones (id, id_project, sequence, name, description, weight_percent, target_date, actual_date, status, created_at, updated_at)
VALUES
-- PRJ-TEST-001: Website Development ABC - 3 milestones
-- Milestone 1: Completed on time
('b0500000-0000-0000-0000-000000000001', 'a0500000-0000-0000-0000-000000000001', 1, 'Design Phase', 'UI/UX design and wireframes', 25, '2024-02-15', '2024-02-10', 'COMPLETED', NOW(), NOW()),
-- Milestone 2: Overdue (past target, still in progress)
('b0500000-0000-0000-0000-000000000002', 'a0500000-0000-0000-0000-000000000001', 2, 'Development Phase', 'Backend and frontend development', 50, '2024-03-15', NULL, 'IN_PROGRESS', NOW(), NOW()),
-- Milestone 3: Pending with future target
('b0500000-0000-0000-0000-000000000003', 'a0500000-0000-0000-0000-000000000001', 3, 'Testing Phase', 'QA and user acceptance testing', 25, '2099-12-31', NULL, 'PENDING', NOW(), NOW());

-- =============================================================================
-- TRANSACTIONS (headers for journal entries)
-- =============================================================================
INSERT INTO transactions (id, transaction_number, transaction_date, id_journal_template, id_project, amount, description, status, posted_at, created_at, updated_at, created_by, updated_by)
VALUES
-- PRJ-001 Revenue Entry 1: Development payment 15M (Feb 2024)
('90500000-0000-0000-1000-000000000001', 'TRX-PRJ-T001-01', '2024-02-15', 'e0000000-0000-0000-0000-000000000099', 'a0500000-0000-0000-0000-000000000001', 15000000.00, 'Pembayaran development PRJ-001', 'POSTED', '2024-02-15 10:00:00', NOW(), NOW(), 'system', 'system'),
-- PRJ-001 Revenue Entry 2: Consulting payment 10M (Apr 2024)
('90500000-0000-0000-1000-000000000002', 'TRX-PRJ-T001-02', '2024-04-20', 'e0000000-0000-0000-0000-000000000099', 'a0500000-0000-0000-0000-000000000001', 10000000.00, 'Pembayaran konsultasi PRJ-001', 'POSTED', '2024-04-20 10:00:00', NOW(), NOW(), 'system', 'system'),
-- PRJ-001 Expense Entry 1: Salary 8M (Mar 2024)
('90500000-0000-0000-1000-000000000003', 'TRX-PRJ-T001-03', '2024-03-31', 'e0000000-0000-0000-0000-000000000099', 'a0500000-0000-0000-0000-000000000001', 8000000.00, 'Gaji tim PRJ-001', 'POSTED', '2024-03-31 16:00:00', NOW(), NOW(), 'system', 'system'),
-- PRJ-001 Expense Entry 2: Cloud/Server 2M (Apr 2024)
('90500000-0000-0000-1000-000000000004', 'TRX-PRJ-T001-04', '2024-04-30', 'e0000000-0000-0000-0000-000000000099', 'a0500000-0000-0000-0000-000000000001', 2000000.00, 'Biaya server PRJ-001', 'POSTED', '2024-04-30 09:00:00', NOW(), NOW(), 'system', 'system'),
-- PRJ-002 Revenue Entry: Development payment 12M (May 2024)
('90500000-0000-0000-1000-000000000005', 'TRX-PRJ-T002-01', '2024-05-15', 'e0000000-0000-0000-0000-000000000099', 'a0500000-0000-0000-0000-000000000002', 12000000.00, 'Pembayaran development PRJ-002', 'POSTED', '2024-05-15 10:00:00', NOW(), NOW(), 'system', 'system'),
-- PRJ-002 Expense Entry: Salary 8M (May 2024)
('90500000-0000-0000-1000-000000000006', 'TRX-PRJ-T002-02', '2024-05-31', 'e0000000-0000-0000-0000-000000000099', 'a0500000-0000-0000-0000-000000000002', 8000000.00, 'Gaji tim PRJ-002', 'POSTED', '2024-05-31 16:00:00', NOW(), NOW(), 'system', 'system'),
-- PRJ-003 Revenue Entry: Consulting payment 18M (Mar 2024)
('90500000-0000-0000-1000-000000000007', 'TRX-PRJ-T003-01', '2024-03-20', 'e0000000-0000-0000-0000-000000000099', 'a0500000-0000-0000-0000-000000000003', 18000000.00, 'Pembayaran konsultasi PRJ-003', 'POSTED', '2024-03-20 10:00:00', NOW(), NOW(), 'system', 'system');

-- =============================================================================
-- PROJECT JOURNAL ENTRIES
-- =============================================================================

-- PRJ-TEST-001: Website Development ABC
-- Revenue Entry 1: Development payment 15M (Feb 2024)
INSERT INTO journal_entries (id, journal_number, posted_at, id_transaction, id_account, id_project, debit_amount, credit_amount, created_at, updated_at, created_by, updated_by, line_order)
VALUES
('90500000-0000-0000-0000-000000000001', 'JRN-PRJ-T001-01', '2024-02-15 10:00:00', '90500000-0000-0000-1000-000000000001', '10000000-0000-0000-0000-000000000102', 'a0500000-0000-0000-0000-000000000001', 15000000.00, 0.00, NOW(), NOW(), 'system', 'system', 1),
('90500000-0000-0000-0000-000000000002', 'JRN-PRJ-T001-01', '2024-02-15 10:00:00', '90500000-0000-0000-1000-000000000001', '40000000-0000-0000-0000-000000000102', 'a0500000-0000-0000-0000-000000000001', 0.00, 15000000.00, NOW(), NOW(), 'system', 'system', 2);

-- Revenue Entry 2: Consulting payment 10M (Apr 2024)
INSERT INTO journal_entries (id, journal_number, posted_at, id_transaction, id_account, id_project, debit_amount, credit_amount, created_at, updated_at, created_by, updated_by, line_order)
VALUES
('90500000-0000-0000-0000-000000000003', 'JRN-PRJ-T001-02', '2024-04-20 10:00:00', '90500000-0000-0000-1000-000000000002', '10000000-0000-0000-0000-000000000102', 'a0500000-0000-0000-0000-000000000001', 10000000.00, 0.00, NOW(), NOW(), 'system', 'system', 1),
('90500000-0000-0000-0000-000000000004', 'JRN-PRJ-T001-02', '2024-04-20 10:00:00', '90500000-0000-0000-1000-000000000002', '40000000-0000-0000-0000-000000000101', 'a0500000-0000-0000-0000-000000000001', 0.00, 10000000.00, NOW(), NOW(), 'system', 'system', 2);

-- Expense Entry 1: Salary 8M (Mar 2024)
INSERT INTO journal_entries (id, journal_number, posted_at, id_transaction, id_account, id_project, debit_amount, credit_amount, created_at, updated_at, created_by, updated_by, line_order)
VALUES
('90500000-0000-0000-0000-000000000005', 'JRN-PRJ-T001-03', '2024-03-31 16:00:00', '90500000-0000-0000-1000-000000000003', '50000000-0000-0000-0000-000000000101', 'a0500000-0000-0000-0000-000000000001', 8000000.00, 0.00, NOW(), NOW(), 'system', 'system', 1),
('90500000-0000-0000-0000-000000000006', 'JRN-PRJ-T001-03', '2024-03-31 16:00:00', '90500000-0000-0000-1000-000000000003', '10000000-0000-0000-0000-000000000101', 'a0500000-0000-0000-0000-000000000001', 0.00, 8000000.00, NOW(), NOW(), 'system', 'system', 2);

-- Expense Entry 2: Cloud/Server 2M (Apr 2024)
INSERT INTO journal_entries (id, journal_number, posted_at, id_transaction, id_account, id_project, debit_amount, credit_amount, created_at, updated_at, created_by, updated_by, line_order)
VALUES
('90500000-0000-0000-0000-000000000007', 'JRN-PRJ-T001-04', '2024-04-30 09:00:00', '90500000-0000-0000-1000-000000000004', '50000000-0000-0000-0000-000000000102', 'a0500000-0000-0000-0000-000000000001', 2000000.00, 0.00, NOW(), NOW(), 'system', 'system', 1),
('90500000-0000-0000-0000-000000000008', 'JRN-PRJ-T001-04', '2024-04-30 09:00:00', '90500000-0000-0000-1000-000000000004', '10000000-0000-0000-0000-000000000102', 'a0500000-0000-0000-0000-000000000001', 0.00, 2000000.00, NOW(), NOW(), 'system', 'system', 2);

-- PRJ-TEST-002: Mobile App ABC
-- Revenue Entry: Development payment 12M (May 2024)
INSERT INTO journal_entries (id, journal_number, posted_at, id_transaction, id_account, id_project, debit_amount, credit_amount, created_at, updated_at, created_by, updated_by, line_order)
VALUES
('90500000-0000-0000-0000-000000000009', 'JRN-PRJ-T002-01', '2024-05-15 10:00:00', '90500000-0000-0000-1000-000000000005', '10000000-0000-0000-0000-000000000102', 'a0500000-0000-0000-0000-000000000002', 12000000.00, 0.00, NOW(), NOW(), 'system', 'system', 1),
('90500000-0000-0000-0000-000000000010', 'JRN-PRJ-T002-01', '2024-05-15 10:00:00', '90500000-0000-0000-1000-000000000005', '40000000-0000-0000-0000-000000000102', 'a0500000-0000-0000-0000-000000000002', 0.00, 12000000.00, NOW(), NOW(), 'system', 'system', 2);

-- Expense Entry: Salary 8M (May 2024)
INSERT INTO journal_entries (id, journal_number, posted_at, id_transaction, id_account, id_project, debit_amount, credit_amount, created_at, updated_at, created_by, updated_by, line_order)
VALUES
('90500000-0000-0000-0000-000000000011', 'JRN-PRJ-T002-02', '2024-05-31 16:00:00', '90500000-0000-0000-1000-000000000006', '50000000-0000-0000-0000-000000000101', 'a0500000-0000-0000-0000-000000000002', 8000000.00, 0.00, NOW(), NOW(), 'system', 'system', 1),
('90500000-0000-0000-0000-000000000012', 'JRN-PRJ-T002-02', '2024-05-31 16:00:00', '90500000-0000-0000-1000-000000000006', '10000000-0000-0000-0000-000000000101', 'a0500000-0000-0000-0000-000000000002', 0.00, 8000000.00, NOW(), NOW(), 'system', 'system', 2);

-- PRJ-TEST-003: IT Consulting XYZ
-- Revenue Entry: Consulting payment 18M (Mar 2024)
INSERT INTO journal_entries (id, journal_number, posted_at, id_transaction, id_account, id_project, debit_amount, credit_amount, created_at, updated_at, created_by, updated_by, line_order)
VALUES
('90500000-0000-0000-0000-000000000013', 'JRN-PRJ-T003-01', '2024-03-20 10:00:00', '90500000-0000-0000-1000-000000000007', '10000000-0000-0000-0000-000000000102', 'a0500000-0000-0000-0000-000000000003', 18000000.00, 0.00, NOW(), NOW(), 'system', 'system', 1),
('90500000-0000-0000-0000-000000000014', 'JRN-PRJ-T003-01', '2024-03-20 10:00:00', '90500000-0000-0000-1000-000000000007', '40000000-0000-0000-0000-000000000101', 'a0500000-0000-0000-0000-000000000003', 0.00, 18000000.00, NOW(), NOW(), 'system', 'system', 2);

-- =============================================================================
-- EXPECTED PROFITABILITY RESULTS (for test assertions)
-- =============================================================================
--
-- PRJ-001 (Website Development ABC):
--   Revenue: 25,000,000 (15M + 10M)
--   Expense: 10,000,000 (8M + 2M)
--   Profit:  15,000,000
--   Margin:  60%
--
-- PRJ-002 (Mobile App ABC):
--   Revenue: 12,000,000
--   Expense: 8,000,000
--   Profit:  4,000,000
--   Margin:  33.33%
--
-- PRJ-003 (IT Consulting XYZ):
--   Revenue: 18,000,000
--   Expense: 0
--   Profit:  18,000,000
--   Margin:  100%
--
-- PT ABC (Client Profitability):
--   Total Revenue: 37,000,000 (25M + 12M)
--   Total Profit:  19,000,000 (15M + 4M)
--   Overall Margin: 51.35%
--   Project Count: 2
--
-- PT XYZ (Client Profitability):
--   Total Revenue: 18,000,000
--   Total Profit:  18,000,000
--   Overall Margin: 100%
--   Project Count: 1
--
-- Client Ranking (by revenue, 2024-01-01 to 2024-06-30):
--   1. PT ABC - 37,000,000 (67.27%)
--   2. PT XYZ - 18,000,000 (32.73%)
--   (CV DEF not in ranking - no revenue)
