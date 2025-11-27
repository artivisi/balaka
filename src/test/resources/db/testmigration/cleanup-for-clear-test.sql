-- Cleanup script to enable testing clear COA and clear Templates functionality
-- This removes data that would prevent clearing (journal entries and transactions)
-- Templates and accounts are cleared by the service's clearAllData() method

-- Delete documents first (FK references to transactions, journal_entries, invoices)
DELETE FROM documents;

-- Delete invoices (FK reference to transactions via id_transaction)
DELETE FROM invoices;

-- Delete all journal entries
DELETE FROM journal_entries;

-- Delete all transactions
DELETE FROM transactions;
