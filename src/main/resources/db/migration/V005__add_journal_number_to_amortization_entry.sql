-- Add journal_number column to amortization_entries table
ALTER TABLE amortization_entries
ADD COLUMN journal_number VARCHAR(20);

-- Create index for faster lookups
CREATE INDEX idx_amortization_entries_journal_number ON amortization_entries(journal_number);

-- Update existing entries with journal numbers (if any)
UPDATE amortization_entries ae
SET journal_number = je.journal_number
FROM journal_entries je
WHERE ae.id_journal_entry = je.id
  AND ae.id_journal_entry IS NOT NULL;
