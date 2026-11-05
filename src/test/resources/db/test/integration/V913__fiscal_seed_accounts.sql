-- Seed accounts of production V008 (a no-op on an empty chart of accounts) for the V800 chart.
INSERT INTO chart_of_accounts (id, account_code, account_name, account_type, normal_balance, id_parent, level, is_header, active, is_permanent, fiscal_treatment)
SELECT gen_random_uuid(), s.code, s.name, s.type, s.balance, p.id, p.level + 1, s.header, TRUE, s.permanent, s.treatment
FROM (VALUES
    ('2.1.25', 'Hutang PPh 26', 'LIABILITY', 'CREDIT', '2.1', FALSE, TRUE, NULL),
    ('2.1.40', 'Hutang Dividen', 'LIABILITY', 'CREDIT', '2.1', FALSE, TRUE, NULL),
    ('3.2.03', 'Dividen', 'EQUITY', 'DEBIT', '3.2', FALSE, FALSE, NULL),
    ('5.8', 'Beban Karyawan & Koreksi Fiskal', 'EXPENSE', 'DEBIT', '5', TRUE, FALSE, NULL)
) AS s(code, name, type, balance, parent, header, permanent, treatment)
JOIN chart_of_accounts p ON p.account_code = s.parent;

INSERT INTO chart_of_accounts (id, account_code, account_name, account_type, normal_balance, id_parent, level, is_header, active, is_permanent, fiscal_treatment)
SELECT gen_random_uuid(), s.code, s.name, 'EXPENSE', 'DEBIT', p.id, p.level + 1, FALSE, TRUE, FALSE, s.treatment
FROM (VALUES
    ('5.8.01', 'Beban Bonus', 'DEDUCTIBLE'),
    ('5.8.02', 'Beban THR', 'DEDUCTIBLE'),
    ('5.8.03', 'Beban Natura/Kenikmatan', 'CONDITIONAL'),
    ('5.8.04', 'Beban Tidak Dapat Dikurangkan', 'NON_DEDUCTIBLE')
) AS s(code, name, treatment)
JOIN chart_of_accounts p ON p.account_code = '5.8';
