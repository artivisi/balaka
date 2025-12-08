# Data Export Manifest

Export Date: 2025-01-01
Application: Aplikasi Akunting
Format Version: 2.0

## Company Information
Name: PT Artivisi Intermedia
NPWP: -

## Export Contents
- Chart of Accounts: 97 records
- Salary Components: 17 records
- Journal Templates: 68 records
- Asset Categories: 4 records
- Journal Entries: 15 records
- Transactions: 1 records
- Clients: 0 records
- Projects: 0 records
- Invoices: 0 records
- Employees: 0 records
- Payroll Runs: 0 records
- Users: 0 records
- Documents: 0 files
- Audit Logs: 0 records

## Notes
This is a seed data export containing:
- Chart of Accounts (Artivisi IT Services COA v3.1 with improved tax expense structure)
- Salary Components (Indonesian payroll with BPJS and PPh 21)
- Journal Templates (Artivisi templates v3.0)
- Asset Categories (Fixed asset depreciation settings)
- Initial Balance Transaction (Januari 2025)

All other tables are empty placeholders.

## Version History
- v1.0: Initial COA and basic templates
- v2.0: Added PPN, PPh 23, PPh 4(2) tax templates
- v2.1: Added payroll, BPJS, investment templates
- v3.0: Added fixed asset accounts, asset categories, depreciation templates, salary components
- v3.1: Sharia compliance - Dana Non-Halal account, bunga masuk ke liability bukan revenue
- v3.2: Fix Saldo Awal Tahun template category from ADJUSTMENT to TRANSFER (enum alignment)
- v3.3: Added Bank DKI (1.1.07), initial balance transaction from Neraca Januari 2025
- v3.4: Made bank account selection flexible in templates (BANK hint instead of hardcoded BCA)
