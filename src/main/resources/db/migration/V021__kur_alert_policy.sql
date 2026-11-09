-- =============================================
-- V010: KUR repayment alert threshold policy (FAS-96)
--
-- The alert state (green/yellow/orange/red) may only be computed from a policy
-- whose values have been approved by the CEO and the Finance Controller. This
-- table stores no defaults: either the row is incomplete (and the API reports
-- the policy as "unconfigured") or every threshold, the approver and the
-- approval reference are present together.
--
-- Money columns are DECIMAL(19,2): no binary floating point.
-- A reserved amount is never a payment; this table only stores thresholds.
-- =============================================

CREATE TABLE kur_alert_policies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    loan_id UUID NOT NULL UNIQUE REFERENCES kur_loans(id),
    minimum_cash DECIMAL(19, 2),
    target_reserve DECIMAL(19, 2),
    funding_pace_tolerance_percent DECIMAL(7, 4),
    orange_shortfall DECIMAL(19, 2),
    red_shortfall DECIMAL(19, 2),
    -- Evidence that the CEO + Finance Controller approved these values, e.g.
    -- "FAS-123 (CEO + Finance Controller, 2026-11-02)". Never inferred.
    approval_reference VARCHAR(200),
    approved_by VARCHAR(200),
    approved_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_kur_alert_policy_complete CHECK (
        (minimum_cash IS NULL
            AND target_reserve IS NULL
            AND funding_pace_tolerance_percent IS NULL
            AND orange_shortfall IS NULL
            AND red_shortfall IS NULL
            AND approval_reference IS NULL
            AND approved_by IS NULL
            AND approved_at IS NULL)
        OR
        (minimum_cash IS NOT NULL
            AND target_reserve IS NOT NULL
            AND funding_pace_tolerance_percent IS NOT NULL
            AND orange_shortfall IS NOT NULL
            AND red_shortfall IS NOT NULL
            AND approval_reference IS NOT NULL
            AND length(trim(approval_reference)) > 0
            AND approved_by IS NOT NULL
            AND length(trim(approved_by)) > 0
            AND approved_at IS NOT NULL)
    ),
    CONSTRAINT chk_kur_alert_policy_amounts CHECK (
        (minimum_cash IS NULL OR minimum_cash >= 0)
        AND (target_reserve IS NULL OR target_reserve >= 0)
        AND (funding_pace_tolerance_percent IS NULL
            OR (funding_pace_tolerance_percent >= 0 AND funding_pace_tolerance_percent <= 100))
        AND (orange_shortfall IS NULL OR orange_shortfall > 0)
        AND (red_shortfall IS NULL OR (red_shortfall > 0 AND red_shortfall > orange_shortfall))
    )
);

COMMENT ON TABLE kur_alert_policies IS
    'Approved KUR repayment alert thresholds (FAS-96). Unconfigured until CEO + Finance Controller approve values.';
