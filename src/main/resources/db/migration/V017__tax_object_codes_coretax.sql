-- Kode Objek Pajak corrected to the Coretax list (e-Bupot Unifikasi numbering of PER-24/PJ/2021,
-- BP21 codes of PER-11/PJ/2025). Earlier releases stored codes that point to another tax object
-- (e.g. 24-104-04 meant "Jasa Akuntansi" in the app but is "Jasa Penilai" in the list) or that do
-- not exist. Stored strings are remapped by the meaning the app gave them.
--
-- 24-104-99 ("Jasa Lainnya") and 21-100-09 ("Honorarium/Imbalan Bukan Pegawai") have no single
-- target: the list has no catch-all PPh 23 service code, and the PPh 21 bukan pegawai code depends on
-- the recipient. Rows carrying them must be recoded by the operator before this migration runs.

DO $$
DECLARE
    v_details BIGINT;
    v_vendors BIGINT;
    v_payments BIGINT;
BEGIN
    SELECT count(*) INTO v_details FROM tax_transaction_details
     WHERE tax_object_code IN ('24-104-99', '21-100-09');
    SELECT count(*) INTO v_vendors FROM vendors
     WHERE default_tax_object_code IN ('24-104-99', '21-100-09');
    SELECT count(*) INTO v_payments FROM withholding_payments
     WHERE tax_object_code IN ('24-104-99', '21-100-09');

    IF v_details + v_vendors + v_payments > 0 THEN
        RAISE EXCEPTION 'V017: rows with tax object code 24-104-99 or 21-100-09 have no Coretax equivalent '
            '(tax_transaction_details: %, vendors.default_tax_object_code: %, withholding_payments: %). '
            'Recode them to a specific 24-104-xx or 21-100-07/18/19/20 code, then rerun.',
            v_details, v_vendors, v_payments;
    END IF;
END $$;

-- One CASE per table: the map is a permutation (04->06, 05->04, 06->05), so chained updates would
-- map a row twice.
UPDATE tax_transaction_details SET tax_object_code = CASE tax_object_code
    WHEN '24-104-04' THEN '24-104-06'
    WHEN '24-104-05' THEN '24-104-04'
    WHEN '24-104-06' THEN '24-104-05'
    WHEN '24-104-07' THEN '24-104-10'
    WHEN '24-104-10' THEN '24-104-18'
    WHEN '24-104-14' THEN '24-104-29'
    WHEN '24-104-16' THEN '24-104-31'
    WHEN '24-104-21' THEN '24-104-39'
    WHEN '24-104-22' THEN '24-104-36'
    WHEN '24-104-34' THEN '24-104-28'
    WHEN '24-100-09' THEN '24-100-02'
    WHEN '28-409-01' THEN '28-403-02'
    WHEN '28-409-07' THEN '28-409-22'
    WHEN '28-409-08' THEN '28-409-24'
    WHEN '28-409-09' THEN '28-409-27'
    WHEN '27-100-99' THEN '27-104-01'
    END
WHERE tax_object_code IN ('24-104-04', '24-104-05', '24-104-06', '24-104-07', '24-104-10', '24-104-14',
    '24-104-16', '24-104-21', '24-104-22', '24-104-34', '24-100-09', '28-409-01', '28-409-07',
    '28-409-08', '28-409-09', '27-100-99');

UPDATE vendors SET default_tax_object_code = CASE default_tax_object_code
    WHEN '24-104-04' THEN '24-104-06'
    WHEN '24-104-05' THEN '24-104-04'
    WHEN '24-104-06' THEN '24-104-05'
    WHEN '24-104-07' THEN '24-104-10'
    WHEN '24-104-10' THEN '24-104-18'
    WHEN '24-104-14' THEN '24-104-29'
    WHEN '24-104-16' THEN '24-104-31'
    WHEN '24-104-21' THEN '24-104-39'
    WHEN '24-104-22' THEN '24-104-36'
    WHEN '24-104-34' THEN '24-104-28'
    WHEN '24-100-09' THEN '24-100-02'
    WHEN '28-409-01' THEN '28-403-02'
    WHEN '28-409-07' THEN '28-409-22'
    WHEN '28-409-08' THEN '28-409-24'
    WHEN '28-409-09' THEN '28-409-27'
    WHEN '27-100-99' THEN '27-104-01'
    END
WHERE default_tax_object_code IN ('24-104-04', '24-104-05', '24-104-06', '24-104-07', '24-104-10', '24-104-14',
    '24-104-16', '24-104-21', '24-104-22', '24-104-34', '24-100-09', '28-409-01', '28-409-07',
    '28-409-08', '28-409-09', '27-100-99');

UPDATE withholding_payments SET tax_object_code = CASE tax_object_code
    WHEN '24-104-04' THEN '24-104-06'
    WHEN '24-104-05' THEN '24-104-04'
    WHEN '24-104-06' THEN '24-104-05'
    WHEN '24-104-07' THEN '24-104-10'
    WHEN '24-104-10' THEN '24-104-18'
    WHEN '24-104-14' THEN '24-104-29'
    WHEN '24-104-16' THEN '24-104-31'
    WHEN '24-104-21' THEN '24-104-39'
    WHEN '24-104-22' THEN '24-104-36'
    WHEN '24-104-34' THEN '24-104-28'
    WHEN '24-100-09' THEN '24-100-02'
    WHEN '28-409-01' THEN '28-403-02'
    WHEN '28-409-07' THEN '28-409-22'
    WHEN '28-409-08' THEN '28-409-24'
    WHEN '28-409-09' THEN '28-409-27'
    WHEN '27-100-99' THEN '27-104-01'
    END
WHERE tax_object_code IN ('24-104-04', '24-104-05', '24-104-06', '24-104-07', '24-104-10', '24-104-14',
    '24-104-16', '24-104-21', '24-104-22', '24-104-34', '24-100-09', '28-409-01', '28-409-07',
    '28-409-08', '28-409-09', '27-100-99');
