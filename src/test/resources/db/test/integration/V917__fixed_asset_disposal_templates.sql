-- V012 runs before V800 and skips accounts and templates on an empty chart. V800 has its own "Pelepasan Aset Tetap"
-- with fixed accounts; bring it to the shape production and the seed packs have.
DELETE FROM journal_template_lines WHERE id_journal_template = 'e0000000-0000-0000-0000-000000000017';
UPDATE journal_templates SET is_system = FALSE, description = 'Penjualan aset tetap. Dibuat dari menu Pelepasan Aset.'
WHERE id = 'e0000000-0000-0000-0000-000000000017';

INSERT INTO journal_templates (id, template_name, category, cash_flow_category, template_type, description, is_system, active) VALUES
('e0000000-0000-0000-0000-000000000049', 'Penghapusan Aset Tetap', 'EXPENSE', 'INVESTING', 'DETAILED',
 'Hibah, penghapusan, atau pemindahan aset tetap tanpa hasil penjualan. Dibuat dari menu Pelepasan Aset.', FALSE, TRUE);

INSERT INTO journal_template_lines (id, id_journal_template, id_account, position, formula, line_order, description, account_hint) VALUES
(gen_random_uuid(), 'e0000000-0000-0000-0000-000000000017', NULL, 'DEBIT', 'disposalProceeds', 1, 'Hasil penjualan aset', 'KAS_PENERIMAAN'),
(gen_random_uuid(), 'e0000000-0000-0000-0000-000000000017', NULL, 'DEBIT', 'accumulatedDepreciation', 2, 'Hapus akumulasi penyusutan', 'AKUM_PENYUSUTAN'),
(gen_random_uuid(), 'e0000000-0000-0000-0000-000000000017', NULL, 'DEBIT', 'lossOnDisposal', 3, 'Rugi pelepasan aset', 'RUGI_PELEPASAN'),
(gen_random_uuid(), 'e0000000-0000-0000-0000-000000000017', NULL, 'CREDIT', 'assetCost', 4, 'Hapus nilai perolehan aset', 'ASET_TETAP'),
(gen_random_uuid(), 'e0000000-0000-0000-0000-000000000017', NULL, 'CREDIT', 'gainOnDisposal', 5, 'Laba pelepasan aset', 'LABA_PELEPASAN'),
(gen_random_uuid(), 'e0000000-0000-0000-0000-000000000049', NULL, 'DEBIT', 'accumulatedDepreciation', 1, 'Hapus akumulasi penyusutan', 'AKUM_PENYUSUTAN'),
(gen_random_uuid(), 'e0000000-0000-0000-0000-000000000049', NULL, 'DEBIT', 'lossOnDisposal', 2, 'Rugi pelepasan aset', 'RUGI_PELEPASAN'),
(gen_random_uuid(), 'e0000000-0000-0000-0000-000000000049', NULL, 'CREDIT', 'assetCost', 3, 'Hapus nilai perolehan aset', 'ASET_TETAP');
