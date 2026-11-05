-- =============================================
-- V010: Sales tax chain: invoice, faktur, receipt, bupot (issue #44)
--
-- Faktur and bupot numbers stay on tax_transaction_details only. A detail belongs to an invoice through its
-- transaction (transactions.source_document_type/id, or invoices.id_transaction), so no second copy exists.
--
-- 1. invoice_payments.pph23_withheld: the part of amount the customer withheld as PPh 23. Existing rows are 0:
--    nothing recorded a withheld amount before. amount keeps its meaning (receivable settled), so balances,
--    aging and statements do not change.
-- 2. tax_transaction_details.id_invoice_payment: the receipt a bupot belongs to.
-- 3. Account "Kredit Pajak PPh 23" and template "Pemotongan PPh 23 oleh Pelanggan", only on a database with a
--    chart of accounts; otherwise the seed pack supplies them.
-- 4. New scopes for existing device-flow tokens (same rule as V006). API clients get no new scope.
-- =============================================

ALTER TABLE invoice_payments ADD COLUMN pph23_withheld DECIMAL(19, 2) NOT NULL DEFAULT 0;
ALTER TABLE invoice_payments ADD CONSTRAINT chk_invoice_payment_pph23
    CHECK (pph23_withheld >= 0 AND pph23_withheld <= amount);

ALTER TABLE tax_transaction_details
    ADD COLUMN id_invoice_payment UUID REFERENCES invoice_payments(id) ON DELETE SET NULL;
CREATE INDEX idx_tax_detail_invoice_payment ON tax_transaction_details(id_invoice_payment);

DO $$
DECLARE
    template_id UUID;
    conflict TEXT;
    grant_pair RECORD;
BEGIN
    IF EXISTS (SELECT 1 FROM chart_of_accounts) THEN
        IF EXISTS (SELECT 1 FROM journal_templates WHERE template_name = 'Pemotongan PPh 23 oleh Pelanggan') THEN
            RAISE EXCEPTION 'V010: journal template name "Pemotongan PPh 23 oleh Pelanggan" is already in use; rename it first';
        END IF;

        IF NOT EXISTS (SELECT 1 FROM chart_of_accounts WHERE account_name = 'Kredit Pajak PPh 23') THEN
            SELECT account_name INTO conflict FROM chart_of_accounts WHERE account_code = '1.1.26';
            IF conflict IS NOT NULL THEN
                RAISE EXCEPTION 'V010: account code 1.1.26 is used by "%"; create an account named "Kredit Pajak PPh 23" first', conflict;
            END IF;
            IF NOT EXISTS (SELECT 1 FROM chart_of_accounts WHERE account_code = '1.1') THEN
                RAISE EXCEPTION 'V010: parent account 1.1 for "Kredit Pajak PPh 23" is missing';
            END IF;
            INSERT INTO chart_of_accounts (id, account_code, account_name, account_type, normal_balance,
                                           id_parent, level, is_header, active, is_permanent)
            SELECT gen_random_uuid(), '1.1.26', 'Kredit Pajak PPh 23', 'ASSET', 'DEBIT', p.id, p.level + 1, FALSE, TRUE, TRUE
              FROM chart_of_accounts p WHERE p.account_code = '1.1';
        END IF;

        template_id := gen_random_uuid();
        INSERT INTO journal_templates (id, template_name, category, cash_flow_category, template_type, description, is_system, active)
        VALUES (template_id, 'Pemotongan PPh 23 oleh Pelanggan', 'RECEIPT', 'OPERATING', 'SIMPLE',
                'Kredit pajak atas PPh 23 yang dipotong pelanggan dari piutang invoice', FALSE, TRUE);
        INSERT INTO journal_template_lines (id, id_journal_template, id_account, position, formula, line_order, description, account_hint)
        VALUES (gen_random_uuid(), template_id, NULL, 'DEBIT', 'amount', 1, 'Kredit Pajak PPh 23', 'KREDIT_PPH23'),
               (gen_random_uuid(), template_id, NULL, 'CREDIT', 'amount', 2, 'Piutang usaha', 'PIUTANG');
    END IF;

    FOR grant_pair IN SELECT * FROM (VALUES ('tax-export:read', 'sales-tax-chain:read'),
                                            ('transactions:post', 'sales-tax-chain:write')) AS g(existing, added) LOOP
        UPDATE device_tokens t
           SET scopes = t.scopes || ',' || grant_pair.added
         WHERE t.scopes IS NOT NULL
           AND NOT EXISTS (SELECT 1 FROM api_clients c WHERE c.client_id = t.client_id)
           AND (',' || t.scopes || ',') LIKE ('%,' || grant_pair.existing || ',%')
           AND (',' || t.scopes || ',') NOT LIKE ('%,' || grant_pair.added || ',%');
    END LOOP;
END $$;
