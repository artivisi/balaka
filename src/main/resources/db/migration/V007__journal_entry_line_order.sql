-- =============================================
-- V007: journal entry line order
--
-- Journal lines had no column preserving their order within a transaction.
-- Free-form entries (POST /api/transactions/journal-entry) are stored without
-- journal numbers and numbered at posting in the order the lines were read
-- back, which PostgreSQL does not guarantee to be insertion order.
--
-- line_order is the 1-based position of the line within its transaction.
-- Reversal lines added on void follow the original lines.
--
-- Backfill: per transaction, by journal number (base number, then numeric
-- line suffix so "-10" follows "-09"), then created_at, then id for rows
-- without a journal number.
-- =============================================

ALTER TABLE journal_entries ADD COLUMN line_order INTEGER;

UPDATE journal_entries je
SET line_order = ordered.rn
FROM (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY id_transaction
               ORDER BY regexp_replace(journal_number, '-[0-9]+$', '') NULLS LAST,
                        (regexp_match(journal_number, '-([0-9]+)$'))[1]::INTEGER NULLS LAST,
                        journal_number NULLS LAST,
                        created_at,
                        id
           ) AS rn
    FROM journal_entries
) ordered
WHERE je.id = ordered.id;

ALTER TABLE journal_entries ALTER COLUMN line_order SET NOT NULL;
ALTER TABLE journal_entries ADD CONSTRAINT chk_je_line_order_positive CHECK (line_order > 0);
ALTER TABLE journal_entries ADD CONSTRAINT uk_je_transaction_line_order UNIQUE (id_transaction, line_order);
