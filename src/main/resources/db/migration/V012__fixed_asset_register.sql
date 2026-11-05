-- =============================================
-- V012: Fixed-asset register: identity, verification, disposal record (issue #49)
--
-- 1. fixed_assets: brand, model, custodian, condition, last verified date, photo. All nullable: existing assets
--    stay as they are and the yearly exceptions report lists what is missing. location and serial_number exist.
-- 2. disposal_type gains GIFTED.
-- 3. asset_disposals: generated record per disposal. Its transaction is a DRAFT; the asset becomes DISPOSED when
--    that transaction is posted. At most one DRAFT or POSTED record per asset.
-- 4. asset_verifications: one row per asset per year. asset_movements: custodian, location and condition changes.
-- 5. Gain and loss accounts and two templates, only on a database with a chart of accounts; otherwise the seed
--    pack supplies them. Disposal never worked before this: its hard-coded template id did not exist after import.
-- =============================================

ALTER TABLE fixed_assets
    ADD COLUMN brand VARCHAR(100),
    ADD COLUMN model VARCHAR(100),
    ADD COLUMN custodian VARCHAR(150),
    ADD COLUMN asset_condition VARCHAR(20),
    ADD COLUMN last_verified_date DATE,
    ADD COLUMN id_photo_document UUID REFERENCES documents(id) ON DELETE SET NULL,
    ADD CONSTRAINT chk_asset_condition CHECK (asset_condition IS NULL OR asset_condition IN ('GOOD', 'MINOR_DAMAGE', 'MAJOR_DAMAGE'));

ALTER TABLE fixed_assets DROP CONSTRAINT chk_disposal_type;
ALTER TABLE fixed_assets ADD CONSTRAINT chk_disposal_type
    CHECK (disposal_type IS NULL OR disposal_type IN ('SOLD', 'GIFTED', 'WRITTEN_OFF', 'TRANSFERRED'));

CREATE TABLE asset_disposals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    record_number VARCHAR(30) NOT NULL UNIQUE,
    id_fixed_asset UUID NOT NULL REFERENCES fixed_assets(id),
    disposal_type VARCHAR(20) NOT NULL,
    disposal_date DATE NOT NULL,
    reason VARCHAR(500) NOT NULL,
    counterparty VARCHAR(255),
    proceeds DECIMAL(19, 2) NOT NULL,
    purchase_cost DECIMAL(19, 2) NOT NULL,
    accumulated_depreciation DECIMAL(19, 2) NOT NULL,
    book_value DECIMAL(19, 2) NOT NULL,
    gain_loss DECIMAL(19, 2) NOT NULL,
    id_proceeds_account UUID REFERENCES chart_of_accounts(id),
    id_transaction UUID REFERENCES transactions(id) ON DELETE SET NULL,
    status VARCHAR(20) NOT NULL,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_asset_disposal_type CHECK (disposal_type IN ('SOLD', 'GIFTED', 'WRITTEN_OFF', 'TRANSFERRED')),
    CONSTRAINT chk_asset_disposal_status CHECK (status IN ('DRAFT', 'POSTED', 'VOIDED', 'CANCELLED')),
    CONSTRAINT chk_asset_disposal_gain_loss CHECK (gain_loss = proceeds - book_value),
    CONSTRAINT chk_asset_disposal_sale CHECK (
        (disposal_type = 'SOLD' AND proceeds > 0 AND id_proceeds_account IS NOT NULL)
        OR (disposal_type <> 'SOLD' AND proceeds = 0 AND id_proceeds_account IS NULL))
);
CREATE UNIQUE INDEX uk_asset_disposal_active ON asset_disposals(id_fixed_asset) WHERE status IN ('DRAFT', 'POSTED');
CREATE INDEX idx_asset_disposal_date ON asset_disposals(disposal_date);
CREATE INDEX idx_asset_disposal_transaction ON asset_disposals(id_transaction);

CREATE TABLE asset_verifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_fixed_asset UUID NOT NULL REFERENCES fixed_assets(id) ON DELETE CASCADE,
    verification_year INTEGER NOT NULL,
    verified_date DATE NOT NULL,
    result VARCHAR(20) NOT NULL,
    asset_condition VARCHAR(20),
    location VARCHAR(100),
    custodian VARCHAR(150),
    notes VARCHAR(500),
    verified_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_asset_verification_year UNIQUE (id_fixed_asset, verification_year),
    CONSTRAINT chk_asset_verification_date CHECK (EXTRACT(YEAR FROM verified_date) = verification_year),
    CONSTRAINT chk_asset_verification_result CHECK (
        (result = 'FOUND' AND asset_condition IN ('GOOD', 'MINOR_DAMAGE', 'MAJOR_DAMAGE') AND location IS NOT NULL AND custodian IS NOT NULL)
        OR (result = 'NOT_FOUND' AND asset_condition IS NULL AND location IS NULL AND custodian IS NULL))
);
CREATE INDEX idx_asset_verification_year ON asset_verifications(verification_year);

CREATE TABLE asset_movements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_fixed_asset UUID NOT NULL REFERENCES fixed_assets(id) ON DELETE CASCADE,
    movement_date DATE NOT NULL,
    source VARCHAR(20) NOT NULL,
    from_custodian VARCHAR(150),
    to_custodian VARCHAR(150),
    from_location VARCHAR(100),
    to_location VARCHAR(100),
    from_condition VARCHAR(20),
    to_condition VARCHAR(20),
    recorded_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_asset_movement_source CHECK (source IN ('EDIT', 'VERIFICATION'))
);
CREATE INDEX idx_asset_movement_asset ON asset_movements(id_fixed_asset);
CREATE INDEX idx_asset_movement_date ON asset_movements(movement_date);

DO $$
DECLARE
    sale_template UUID := gen_random_uuid();
    non_sale_template UUID := gen_random_uuid();
    conflict TEXT;
    account RECORD;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM chart_of_accounts) THEN
        RETURN;
    END IF;

    IF EXISTS (SELECT 1 FROM journal_templates WHERE template_name IN ('Pelepasan Aset Tetap', 'Penghapusan Aset Tetap')) THEN
        RAISE EXCEPTION 'V012: journal template name "Pelepasan Aset Tetap" or "Penghapusan Aset Tetap" is already in use; rename it first';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM chart_of_accounts WHERE account_name = 'Beban Tidak Dapat Dikurangkan') THEN
        RAISE EXCEPTION 'V012: account "Beban Tidak Dapat Dikurangkan" is missing; a gift posts its loss there';
    END IF;

    FOR account IN SELECT * FROM (VALUES
            ('4.2.02', 'Laba Penjualan Aset', 'REVENUE', 'CREDIT', '4.2', NULL),
            ('5.2.02', 'Rugi Penjualan Aset', 'EXPENSE', 'DEBIT', '5.2', 'DEDUCTIBLE')
        ) AS a(code, name, type, balance, parent, treatment) LOOP
        IF NOT EXISTS (SELECT 1 FROM chart_of_accounts WHERE account_name = account.name) THEN
            SELECT account_name INTO conflict FROM chart_of_accounts WHERE account_code = account.code;
            IF conflict IS NOT NULL THEN
                RAISE EXCEPTION 'V012: account code % is used by "%"; create an account named "%" first', account.code, conflict, account.name;
            END IF;
            IF NOT EXISTS (SELECT 1 FROM chart_of_accounts WHERE account_code = account.parent) THEN
                RAISE EXCEPTION 'V012: parent account % for "%" is missing', account.parent, account.name;
            END IF;
            INSERT INTO chart_of_accounts (id, account_code, account_name, account_type, normal_balance,
                                           id_parent, level, is_header, active, is_permanent, fiscal_treatment)
            SELECT gen_random_uuid(), account.code, account.name, account.type, account.balance, p.id, p.level + 1,
                   FALSE, TRUE, FALSE, account.treatment
              FROM chart_of_accounts p WHERE p.account_code = account.parent;
        END IF;
    END LOOP;

    INSERT INTO journal_templates (id, template_name, category, cash_flow_category, template_type, description, is_system, active)
    VALUES (sale_template, 'Pelepasan Aset Tetap', 'INCOME', 'INVESTING', 'DETAILED',
            'Penjualan aset tetap. Dibuat dari menu Pelepasan Aset.', FALSE, TRUE),
           (non_sale_template, 'Penghapusan Aset Tetap', 'EXPENSE', 'INVESTING', 'DETAILED',
            'Hibah, penghapusan, atau pemindahan aset tetap tanpa hasil penjualan. Dibuat dari menu Pelepasan Aset.', FALSE, TRUE);

    INSERT INTO journal_template_lines (id, id_journal_template, id_account, position, formula, line_order, description, account_hint)
    VALUES (gen_random_uuid(), sale_template, NULL, 'DEBIT', 'disposalProceeds', 1, 'Hasil penjualan aset', 'KAS_PENERIMAAN'),
           (gen_random_uuid(), sale_template, NULL, 'DEBIT', 'accumulatedDepreciation', 2, 'Hapus akumulasi penyusutan', 'AKUM_PENYUSUTAN'),
           (gen_random_uuid(), sale_template, NULL, 'DEBIT', 'lossOnDisposal', 3, 'Rugi pelepasan aset', 'RUGI_PELEPASAN'),
           (gen_random_uuid(), sale_template, NULL, 'CREDIT', 'assetCost', 4, 'Hapus nilai perolehan aset', 'ASET_TETAP'),
           (gen_random_uuid(), sale_template, NULL, 'CREDIT', 'gainOnDisposal', 5, 'Laba pelepasan aset', 'LABA_PELEPASAN'),
           (gen_random_uuid(), non_sale_template, NULL, 'DEBIT', 'accumulatedDepreciation', 1, 'Hapus akumulasi penyusutan', 'AKUM_PENYUSUTAN'),
           (gen_random_uuid(), non_sale_template, NULL, 'DEBIT', 'lossOnDisposal', 2, 'Rugi pelepasan aset', 'RUGI_PELEPASAN'),
           (gen_random_uuid(), non_sale_template, NULL, 'CREDIT', 'assetCost', 3, 'Hapus nilai perolehan aset', 'ASET_TETAP');
END $$;
