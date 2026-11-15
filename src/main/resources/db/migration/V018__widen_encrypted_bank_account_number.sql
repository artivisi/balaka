-- company_bank_accounts.account_number is encrypted by EncryptedStringConverter when
-- app.encryption.key is set. The stored form is "ENC:" + Base64(12-byte IV + ciphertext +
-- 16-byte GCM tag), so a 7-digit account number already exceeds the VARCHAR(50) from V002 and
-- the insert fails. V009 widened the other encrypted columns; this one was missed.
ALTER TABLE company_bank_accounts ALTER COLUMN account_number TYPE VARCHAR(255);
