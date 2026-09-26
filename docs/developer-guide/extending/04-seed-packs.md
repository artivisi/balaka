# Industry Seed Packs

Seed packs provide industry-specific starter data: chart of accounts, journal templates, salary components, tax deadlines, and more. They are loaded via `DataImportService` during initial setup or via the data import API.

## Directory Structure

```
industry-seed/<industry-name>/seed-data/
├── 01_company_config.csv
├── 02_chart_of_accounts.csv
├── 03_salary_components.csv
├── 04_journal_templates.csv
├── 05_journal_template_lines.csv
├── 06_journal_template_tags.csv
├── 07_clients.csv
├── 08_projects.csv
├── 09_project_milestones.csv
├── 10_project_payment_terms.csv
├── 11_fiscal_periods.csv
├── 12_tax_deadlines.csv
├── 13_company_bank_accounts.csv
├── 14_merchant_mappings.csv
├── 15_employees.csv
├── 16_employee_salary_components.csv
├── 17_invoices.csv
├── 18_transactions.csv
├── 19_transaction_account_mappings.csv
├── 20_journal_entries.csv
├── 21_payroll_runs.csv
├── 22_payroll_details.csv
├── 23_amortization_schedules.csv
├── 24_amortization_entries.csv
├── 25_tax_transaction_details.csv
├── 26_tax_deadline_completions.csv
├── 27_draft_transactions.csv
├── 30_user_template_preferences.csv
├── 31_telegram_user_links.csv
├── 32_audit_logs.csv
├── 33_transaction_sequences.csv
├── 34_asset_categories.csv
├── MANIFEST.md
└── documents/
```

Files are numbered to enforce import order (foreign key dependencies). The `MANIFEST.md` documents the pack contents and version.

## Existing Packs

| Pack | Industry | PKP Status | Key Features |
|------|----------|------------|--------------|
| `it-service` | IT Services | PKP | 75 COA accounts, PPN templates, PPh 23 withholding |
| `online-seller` | E-commerce | Non-PKP | Marketplace templates, inventory accounts |
| `coffee-shop` | Food & Beverage | Non-PKP | Production/BOM templates, inventory |
| `campus` | Education | Non-PKP | Tuition revenue, multi-program COA |

## Creating a New Seed Pack

### 1. Create Directory

```bash
mkdir -p industry-seed/<name>/seed-data/
```

### 2. Company Config (01_company_config.csv)

A seed pack carries no company identity. Its single row leaves `company_name` and the other identity columns empty and sets only `industry` and the posting accounts, by COA code:

```csv
company_name,company_address,company_phone,company_email,tax_id,npwp,nitku,fiscal_year_start_month,currency_code,signing_officer_name,signing_officer_title,company_logo_path,established_date,is_pkp,pkp_since,industry,id_receivable_account,id_payable_account,id_output_tax_account,id_input_tax_account
,,,,,,,,,,,,,,,online-seller,1.1.10,2.1.02,2.1.03,1.1.25
```

Import rules for this file:

- Row with `company_name`: replaces the company config; empty account columns clear that account.
- Row without `company_name`: updates the existing company config (created on first use when absent). Only `industry` and the account columns that are filled are applied. Any other filled column rejects the import.
- An account code that is not in the chart of accounts rejects the whole import.

The `industry` field is used by AI analysis to determine relevant KPIs and analysis types. The receivable/payable accounts drive the dashboard AR/AP KPIs and `/api/analysis/{snapshot,receivables,payables}`; invoices and bills also require the output/input tax accounts. Without them those screens show "Akun ... belum dikonfigurasi di Pengaturan Perusahaan".

### 3. Chart of Accounts (02_chart_of_accounts.csv)

Design the COA following SAK EMKM structure:

```csv
id,code,name,account_type,parent_id,is_header,is_active,normal_balance,description
uuid-1,1-0000,ASET,ASSET,,true,true,DEBIT,Header: Assets
uuid-2,1-1000,Aset Lancar,ASSET,uuid-1,true,true,DEBIT,Header: Current Assets
uuid-3,1-1100,Kas,ASSET,uuid-2,false,true,DEBIT,Cash on hand
```

Account type values: `ASSET`, `LIABILITY`, `EQUITY`, `REVENUE`, `EXPENSE`.

Conventions:
- Use 5-digit codes: `X-XXXX` (type digit, dash, 4-digit sequence).
- Header accounts (`is_header=true`) cannot be used in transactions.
- Every leaf account must have a `parent_id` pointing to a header.

### 4. Journal Templates (04_journal_templates.csv + 05_journal_template_lines.csv)

Templates define how business events map to journal entries.

```csv
# 04_journal_templates.csv
id,name,transaction_type,category,cash_flow_category,is_system,template_type,description
uuid-t1,Penjualan Tunai,PJ,REVENUE,OPERATING,false,SIMPLE,Cash sales
```

Template types:
- `SIMPLE` -- single amount field, formula is just `amount`.
- `DETAILED` -- multiple formula variables (e.g., `assetCost`, `ppn`, `grossSalary`).

```csv
# 05_journal_template_lines.csv
id,id_template,line_order,line_type,id_account,account_hint,mapping_key,formula,is_required,description
uuid-l1,uuid-t1,1,DEBIT,uuid-kas,,amount,amount,true,Cash received
uuid-l2,uuid-t1,2,CREDIT,uuid-revenue,,amount,amount,true,Sales revenue
```

Line fields:
- `id_account` -- fixed account UUID. Leave null if user-selectable.
- `account_hint` -- used when `id_account` is null. The API caller provides the actual account via `accountSlots` keyed by this hint (e.g., `"BANK"`).
- `formula` -- SpEL expression. Common: `amount`, `amount * 0.11`, `grossSalary`.
- `line_type` -- `DEBIT` or `CREDIT`.
- `line_order` -- determines display order.

### 5. Salary Components (03_salary_components.csv)

Indonesian payroll components:

```csv
id,code,name,component_type,is_taxable,formula,is_active,is_system,line_order
uuid-sc1,GAJI_POKOK,Gaji Pokok,EARNING,true,baseSalary,true,true,1
uuid-sc2,JKK,JKK (0.24%),COMPANY_CONTRIBUTION,false,baseSalary * 0.0024,true,true,2
```

Component types: `EARNING`, `DEDUCTION`, `COMPANY_CONTRIBUTION`.

### 6. Tax Deadlines (12_tax_deadlines.csv)

```csv
id,id_fiscal_period,tax_type,reporting_deadline,payment_deadline,description
uuid-td1,uuid-fp1,PPH_21,2025-02-20,2025-02-10,PPh 21 January 2025
```

### 7. Asset Categories (34_asset_categories.csv)

```csv
id,name,useful_life_years,depreciation_method,fiscal_asset_group
uuid-ac1,Computer Equipment,4,STRAIGHT_LINE,KELOMPOK_1
```

## Testing Seed Packs

Functional tests use `@TestConfiguration` with `@PostConstruct` initializers to load seed data. Each industry has its own test initializer:

```java
@TestConfiguration
@Profile("functional")
public class MyIndustryTestDataInitializer {

    @Autowired
    private DataImportService dataImportService;

    @PostConstruct
    public void init() {
        dataImportService.importFromSeedPack("my-industry");
    }
}
```

Tests reference this initializer via `@Import(MyIndustryTestDataInitializer.class)`.

## Gotchas

- **CSV column count**: when adding columns to a CSV, count commas carefully. Off-by-one errors cause values to land in wrong columns (e.g., `industry` parsed as `established_date`).
- **Transaction cascade**: `TRUNCATE transactions CASCADE` cascades to `production_orders` (FK `id_transaction`). If your test data includes production orders, do not truncate transactions.
- **UUID consistency**: use deterministic UUIDs in seed data (e.g., `e0000000-0000-0000-0000-000000000001`) so templates can reference accounts and other entities by known IDs.

---

Next: [Testing](05-testing.md)
