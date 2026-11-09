-- =============================================
-- V009: Withholding on non-employee and vendor payments (issue #43)
--
-- 1. vendors become the payee master: type, default treatment, foreign/treaty data. NPWP and NIK are
--    encrypted by the application from now on (individuals' identifiers). Existing plaintext rows stay
--    readable (EncryptedStringConverter passes values without the ENC: prefix) and are encrypted on next save.
-- 2. chart_of_accounts.service_expense: TRUE/FALSE on non-header EXPENSE accounts. NULL = not classified;
--    existing accounts stay NULL because no rule can derive the value. The report lists them.
-- 3. tax_transaction_details: PPH_26, foreign counterparty data; counterparty_nik encrypted by the application.
-- 4. tax_deadlines: PPH_26_PAYMENT. Inserted only when the table already has rows; an empty table gets
--    the row from the seed pack.
-- 5. withholding_payments / withholding_deposits. Rows are deleted with their transaction.
-- 6. Journal templates, inserted only on a database with a chart of accounts; otherwise the seed pack
--    supplies them. is_system stays FALSE like "Tagihan Vendor": DataImportService skips the lines of system
--    templates, so a system template loses its lines on import.
-- 7. New scopes for existing device-flow tokens (same rule as V006). API clients get no new scope.
-- =============================================

ALTER TABLE vendors ALTER COLUMN npwp TYPE VARCHAR(255);
ALTER TABLE vendors ALTER COLUMN nik TYPE VARCHAR(255);
ALTER TABLE vendors ADD COLUMN payee_type VARCHAR(20);
ALTER TABLE vendors ADD COLUMN default_withholding VARCHAR(30);
ALTER TABLE vendors ADD COLUMN default_tax_object_code VARCHAR(20);
ALTER TABLE vendors ADD COLUMN country_code VARCHAR(2);
ALTER TABLE vendors ADD COLUMN foreign_tin VARCHAR(50);
ALTER TABLE vendors ADD COLUMN treaty_rate DECIMAL(5, 2);
ALTER TABLE vendors ADD COLUMN dgt_form_number VARCHAR(50);
ALTER TABLE vendors ADD COLUMN dgt_valid_until DATE;
ALTER TABLE vendors ADD CONSTRAINT chk_vendor_payee_type
    CHECK (payee_type IN ('BADAN', 'ORANG_PRIBADI', 'LUAR_NEGERI'));
ALTER TABLE vendors ADD CONSTRAINT chk_vendor_default_withholding
    CHECK (default_withholding IN ('PPH_21_BUKAN_PEGAWAI', 'PPH_23', 'PPH_26'));
ALTER TABLE vendors ADD CONSTRAINT chk_vendor_treaty_rate CHECK (treaty_rate BETWEEN 0 AND 100);

ALTER TABLE chart_of_accounts ADD COLUMN service_expense BOOLEAN;
ALTER TABLE chart_of_accounts ADD CONSTRAINT chk_coa_service_expense
    CHECK (service_expense IS NULL OR account_type = 'EXPENSE');

ALTER TABLE tax_transaction_details DROP CONSTRAINT chk_tax_type;
ALTER TABLE tax_transaction_details ADD CONSTRAINT chk_tax_type
    CHECK (tax_type IN ('PPN_KELUARAN', 'PPN_MASUKAN', 'PPH_21', 'PPH_23', 'PPH_26', 'PPH_42', 'PPH_25', 'PPH_29'));
ALTER TABLE tax_transaction_details ALTER COLUMN counterparty_nik TYPE VARCHAR(255);
ALTER TABLE tax_transaction_details ADD COLUMN counterparty_country VARCHAR(2);
ALTER TABLE tax_transaction_details ADD COLUMN counterparty_foreign_tin VARCHAR(50);
ALTER TABLE tax_transaction_details ADD COLUMN dgt_form_number VARCHAR(50);

ALTER TABLE tax_deadlines DROP CONSTRAINT chk_deadline_type;
ALTER TABLE tax_deadlines ADD CONSTRAINT chk_deadline_type CHECK (deadline_type IN (
    'PPH_21_PAYMENT', 'PPH_23_PAYMENT', 'PPH_26_PAYMENT', 'PPH_42_PAYMENT', 'PPH_25_PAYMENT', 'PPN_PAYMENT',
    'SPT_PPH_21', 'SPT_PPH_23', 'SPT_PPN'
));
INSERT INTO tax_deadlines (id, name, deadline_type, due_day, use_last_day_of_month, description, reminder_days_before, active)
SELECT gen_random_uuid(), 'Setor PPh 26', 'PPH_26_PAYMENT', 15, FALSE,
       'Pembayaran PPh Pasal 26 atas penghasilan penerima luar negeri. Jatuh tempo tanggal 15 bulan berikutnya.', 7, TRUE
WHERE EXISTS (SELECT 1 FROM tax_deadlines)
  AND NOT EXISTS (SELECT 1 FROM tax_deadlines WHERE deadline_type = 'PPH_26_PAYMENT');

CREATE TABLE withholding_payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_vendor UUID NOT NULL REFERENCES vendors(id),
    id_transaction UUID NOT NULL UNIQUE REFERENCES transactions(id) ON DELETE CASCADE,
    id_tax_detail UUID REFERENCES tax_transaction_details(id) ON DELETE SET NULL,
    id_bill UUID REFERENCES bills(id),
    id_bill_payment UUID REFERENCES bill_payments(id) ON DELETE SET NULL,
    treatment VARCHAR(30) NOT NULL,
    tax_object_code VARCHAR(20) NOT NULL,
    payment_date DATE NOT NULL,
    gross_amount DECIMAL(19, 2) NOT NULL,
    dpp_amount DECIMAL(19, 2) NOT NULL,
    tax_rate DECIMAL(5, 2) NOT NULL,
    tax_amount DECIMAL(19, 2) NOT NULL,
    settled_amount DECIMAL(19, 2) NOT NULL,
    net_amount DECIMAL(19, 2) NOT NULL,
    has_tax_id BOOLEAN NOT NULL,
    rate_basis VARCHAR(30) NOT NULL,
    payment_method VARCHAR(20),
    reference_number VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_withholding_treatment CHECK (treatment IN ('PPH_21_BUKAN_PEGAWAI', 'PPH_23', 'PPH_26')),
    CONSTRAINT chk_withholding_gross CHECK (gross_amount > 0),
    CONSTRAINT chk_withholding_tax CHECK (tax_amount >= 0),
    CONSTRAINT chk_withholding_net CHECK (net_amount = settled_amount - tax_amount AND net_amount >= 0),
    CONSTRAINT chk_withholding_bill_method CHECK (id_bill IS NULL OR payment_method IS NOT NULL)
);
CREATE INDEX idx_withholding_vendor_date ON withholding_payments(id_vendor, payment_date);
CREATE INDEX idx_withholding_date ON withholding_payments(payment_date);
CREATE INDEX idx_withholding_bill ON withholding_payments(id_bill);

CREATE TABLE withholding_deposits (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_transaction UUID NOT NULL UNIQUE REFERENCES transactions(id) ON DELETE CASCADE,
    treatment VARCHAR(30) NOT NULL,
    period_year INTEGER NOT NULL,
    period_month INTEGER NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    deposit_date DATE NOT NULL,
    billing_code VARCHAR(50),
    ntpn VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_withholding_deposit_treatment CHECK (treatment IN ('PPH_21_BUKAN_PEGAWAI', 'PPH_23', 'PPH_26')),
    CONSTRAINT chk_withholding_deposit_month CHECK (period_month BETWEEN 1 AND 12),
    CONSTRAINT chk_withholding_deposit_amount CHECK (amount > 0)
);
CREATE INDEX idx_withholding_deposit_period ON withholding_deposits(period_year, period_month);

DO $$
DECLARE
    payment_template UUID;
    deposit_template UUID;
    grant_pair RECORD;
BEGIN
    IF EXISTS (SELECT 1 FROM chart_of_accounts) THEN
        IF EXISTS (SELECT 1 FROM journal_templates
                   WHERE template_name IN ('Pembayaran dengan Pemotongan PPh', 'Setor PPh Dipotong')) THEN
            RAISE EXCEPTION 'V009: journal template names reserved for withholding are already in use; rename them first';
        END IF;
        payment_template := gen_random_uuid();
        deposit_template := gen_random_uuid();
        INSERT INTO journal_templates (id, template_name, category, cash_flow_category, template_type, description, is_system, active)
        VALUES (payment_template, 'Pembayaran dengan Pemotongan PPh', 'PAYMENT', 'OPERATING', 'DETAILED',
                'Pembayaran ke penerima dengan pemotongan PPh 21/23/26. Variabel: settledAmount, netAmount, taxAmount', FALSE, TRUE),
               (deposit_template, 'Setor PPh Dipotong', 'PAYMENT', 'OPERATING', 'SIMPLE',
                'Penyetoran PPh 21/23/26 hasil pemotongan ke kas negara', FALSE, TRUE);
        INSERT INTO journal_template_lines (id, id_journal_template, id_account, position, formula, line_order, description, account_hint)
        VALUES (gen_random_uuid(), payment_template, NULL, 'DEBIT', 'settledAmount', 1, 'Beban atau hutang usaha', 'BEBAN'),
               (gen_random_uuid(), payment_template, NULL, 'CREDIT', 'netAmount', 2, 'Pembayaran dari bank', 'BANK'),
               (gen_random_uuid(), payment_template, NULL, 'CREDIT', 'taxAmount', 3, 'Hutang PPh', 'HUTANG_PPH'),
               (gen_random_uuid(), deposit_template, NULL, 'DEBIT', 'amount', 1, 'Hutang PPh', 'HUTANG_PPH'),
               (gen_random_uuid(), deposit_template, NULL, 'CREDIT', 'amount', 2, 'Pembayaran dari bank', 'BANK');
    END IF;

    FOR grant_pair IN SELECT * FROM (VALUES ('tax-export:read', 'withholding:read'),
                                            ('transactions:post', 'withholding:write')) AS g(existing, added) LOOP
        UPDATE device_tokens t
           SET scopes = t.scopes || ',' || grant_pair.added
         WHERE t.scopes IS NOT NULL
           AND NOT EXISTS (SELECT 1 FROM api_clients c WHERE c.client_id = t.client_id)
           AND (',' || t.scopes || ',') LIKE ('%,' || grant_pair.existing || ',%')
           AND (',' || t.scopes || ',') NOT LIKE ('%,' || grant_pair.added || ',%');
    END LOOP;
END $$;
