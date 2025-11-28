-- V908: Payroll Test Data
-- Creates test employees for payroll testing

-- Test Employee 1 - Active, TK/0
INSERT INTO employees (id, employee_id, name, email, phone, ptkp_status, hire_date, employment_type, employment_status, job_title, department, npwp, active)
VALUES ('e0000000-0000-0000-0000-000000000001', 'EMP001', 'Budi Santoso', 'budi@test.com', '081234567890', 'TK_0', '2024-01-01', 'PERMANENT', 'ACTIVE', 'Software Developer', 'Engineering', '99.888.777.6-012.345', TRUE);

-- Test Employee 2 - Active, K/2
INSERT INTO employees (id, employee_id, name, email, phone, ptkp_status, hire_date, employment_type, employment_status, job_title, department, npwp, active)
VALUES ('e0000000-0000-0000-0000-000000000002', 'EMP002', 'Dewi Lestari', 'dewi@test.com', '081234567891', 'K_2', '2024-02-01', 'PERMANENT', 'ACTIVE', 'Project Manager', 'Management', '99.888.777.6-012.346', TRUE);

-- Test Employee 3 - Active, TK/1, no NPWP
INSERT INTO employees (id, employee_id, name, email, phone, ptkp_status, hire_date, employment_type, employment_status, job_title, department, active)
VALUES ('e0000000-0000-0000-0000-000000000003', 'EMP003', 'Agus Wijaya', 'agus@test.com', '081234567892', 'TK_1', '2024-03-01', 'CONTRACT', 'ACTIVE', 'QA Engineer', 'Engineering', TRUE);

-- Test Employee 4 - Inactive
INSERT INTO employees (id, employee_id, name, email, phone, ptkp_status, hire_date, resign_date, employment_type, employment_status, job_title, department, active)
VALUES ('e0000000-0000-0000-0000-000000000004', 'EMP004', 'Siti Rahayu', 'siti@test.com', '081234567893', 'K_0', '2023-01-01', '2024-06-30', 'PERMANENT', 'RESIGNED', 'Designer', 'Creative', FALSE);
