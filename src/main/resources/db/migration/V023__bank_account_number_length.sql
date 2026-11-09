-- V012: widen account_number to store encrypted values
ALTER TABLE company_bank_accounts ALTER COLUMN account_number TYPE VARCHAR(255);
