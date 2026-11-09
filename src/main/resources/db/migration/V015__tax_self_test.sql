-- =============================================
-- V015: Tax-office self-test (issue #45)
--
-- 1. chart_of_accounts.self_test_role: SERVICE_PPH23, RENT_PPH42, OTHER_EXPENSE (expense) and
--    NON_OPERATING_ASSET (asset), non-header accounts only. NULL = no role. Existing accounts stay NULL: the
--    report shows an error on a line whose role no account carries.
-- 2. self_test_explanations: one explanation per year, line and month (0 = whole-year line).
-- 3. self-test:read for device-flow tokens holding tax-export:read (same rule as V009/V010).
-- =============================================

ALTER TABLE chart_of_accounts ADD COLUMN self_test_role VARCHAR(30);
ALTER TABLE chart_of_accounts ADD CONSTRAINT chk_coa_self_test_role CHECK (
    self_test_role IS NULL
    OR (is_header = FALSE AND (
        (self_test_role IN ('SERVICE_PPH23', 'RENT_PPH42', 'OTHER_EXPENSE') AND account_type = 'EXPENSE')
        OR (self_test_role = 'NON_OPERATING_ASSET' AND account_type = 'ASSET')
    ))
);

CREATE TABLE self_test_explanations (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    report_year  INTEGER      NOT NULL,
    line_code    VARCHAR(40)  NOT NULL,
    period_month INTEGER      NOT NULL,
    explanation  TEXT         NOT NULL,
    updated_by   VARCHAR(100) NOT NULL,
    created_at   TIMESTAMP    NOT NULL,
    updated_at   TIMESTAMP    NOT NULL,
    CONSTRAINT uk_self_test_explanation UNIQUE (report_year, line_code, period_month),
    CONSTRAINT chk_self_test_month CHECK (period_month BETWEEN 0 AND 12),
    CONSTRAINT chk_self_test_line CHECK (line_code IN ('REVENUE_VS_PPN_DPP', 'SALARY_VS_PPH21_DPP',
        'SERVICE_VS_PPH23_DPP', 'RENT_VS_PPH42_DPP', 'FIXED_ASSET_ADDITIONS', 'FIXED_ASSET_DISPOSALS',
        'CAPITAL_MOVEMENT', 'NON_OPERATING_ASSET_MOVEMENT', 'UNITEMISED_OTHER_EXPENSE')),
    CONSTRAINT chk_self_test_explanation CHECK (LENGTH(TRIM(explanation)) > 0)
);

UPDATE device_tokens t
   SET scopes = t.scopes || ',self-test:read'
 WHERE t.scopes IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM api_clients c WHERE c.client_id = t.client_id)
   AND (',' || t.scopes || ',') LIKE '%,tax-export:read,%'
   AND (',' || t.scopes || ',') NOT LIKE '%,self-test:read,%';
