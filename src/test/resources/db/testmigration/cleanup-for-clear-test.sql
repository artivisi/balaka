-- Cleanup script to enable testing clear COA and clear Templates functionality
-- This removes data that would prevent clearing (journal entries and transactions)
-- Templates and accounts are cleared by the service's clearAllData() method

-- Delete all journal entries
DELETE FROM journal_entries;

-- Delete all transactions
DELETE FROM transactions;
