# Functional Test Architecture Revamp Plan

## Executive Summary

Two-part reorganization:
1. **Production Migrations** - Slim down to minimal bootstrap (admin user only, no seed data)
2. **Functional Tests** - Industry-specific test suites that load seed data, avoiding duplication

Four industry packs:
- Service/PKP (simple) - PT ArtiVisi Intermedia
- Online Seller (medium) - Toko Gadget Murah
- Coffee & Pastry Shop (manufacturing) - Kedai Kopi Nusantara
- Campus (education) - STMIK Tazkia

---

## Part 1: Production Migration Reorganization

### Current State
- V004 loads IT Services COA (77 accounts) + Templates (45) + Salary Components + Tax Deadlines
- This doesn't make sense for Online Seller or Manufacturing businesses
- Forces all new installations to start with IT Services data

### Proposed State

| Migration | Content |
|-----------|---------|
| V001 | Schema + Extensions (unchanged) |
| V002 | COA schema (unchanged) |
| V003 | App schema - all entities (unchanged) |
| V004 | **Minimal bootstrap only** |

**V004 - Minimal Bootstrap (Revised)**
```sql
-- Only what's needed to run the app
1. Admin user (admin/admin with bcrypt hash)
2. Default company settings (empty)
3. Current fiscal year
-- NO COA, NO templates, NO salary components, NO tax deadlines
```

**User Flow:**
1. Install app → V001-V004 runs → app starts with blank slate
2. Login as admin → Navigate to Settings > Import Data
3. Select industry seed ZIP → Import → Ready to use

**Industry Seed Packs (existing structure, unchanged):**
```
industry-seed/
├── it-service/seed-data/       # 75 COA, 37 templates, 17 salary, 8 tax
├── online-seller/seed-data/    # 87 COA, 37 templates, 17 salary, 8 tax
└── coffee-shop/seed-data/      # NEW - to be created
```

---

## Part 2: Functional Test Reorganization

## Current State Analysis

### Problems with Current Architecture

1. **Scattered Test Data** - 14 SQL migration files (V900-V912) with overlapping, unclear dependencies
2. **No Industry Context** - Tests assume generic "IT Services" data but don't model real business scenarios
3. **Hardcoded UUIDs** - Fragile ID references like `a0000000-0000-0000-0000-000000000001`
4. **Mixed Concerns** - V901 contains COA + templates + fiscal periods
5. **No Test Isolation** - All 58 tests share same database state

### Current Test Data Files (14 files, ~1,500 LOC)
```
V900__test_inventory_templates.sql     # Inventory journal templates
V901__report_test_data.sql             # COA, fiscal periods, templates
V902__template_test_data.sql           # Template IDs
V903__formula_test_templates.sql       # Formula-based templates
V904__transaction_test_data.sql        # Transaction records
V905__profitability_test_data.sql      # Client, project, transactions
V906__invoice_test_data.sql            # Invoice data
V907__tax_report_test_data.sql         # Tax compliance data
V908__payroll_test_data.sql            # Basic payroll
V909__payroll_approved_test_data.sql   # Payroll with approvals
V910__draft_test_data.sql              # Draft transactions
V911__inventory_report_test_data.sql   # Inventory movements
V912__security_test_users.sql          # Security test users
cleanup-for-clear-test.sql             # Cleanup script
```

### Current Functional Tests (58 tests)
Grouped by feature area but not by industry/complexity.

---

## Proposed Architecture

### Design Principles

1. **Industry-First Organization** - Tests grouped by industry type
2. **Coherent Test Data** - Each industry has complete, self-contained test scenarios
3. **Minimal Yet Complete** - Smallest dataset that exercises all features
4. **Real Business Scenarios** - Test data tells a believable business story
5. **Clear Dependencies** - Explicit data requirements per test

### Industry Test Suites

#### 1. Service Industry (Simplest) - PKP Company
**Business Model**: IT Consulting company - "PT ArtiVisi Intermedia" (PKP)
- No inventory
- Project-based revenue recognition
- B2B invoicing with PPN 11% (Faktur Pajak)
- PPh 23 withholding by clients (2% of gross)
- PPN Masukan from vendor purchases
- Monthly payroll with PPh 21

**Test Scenarios**:
- Client management (3 clients)
- Project management (2 projects per client)
- Service revenue transactions with PPN Keluaran
- Operating expenses with PPN Masukan
- Invoice generation with Faktur Pajak
- PPh 21 payroll tax reporting
- PPh 23 withholding reconciliation
- PPN reporting (Keluaran - Masukan)
- Financial reports (P&L, Balance Sheet, Cash Flow)

#### 2. Online Seller Industry (Medium Complexity)
**Business Model**: Marketplace seller - "Toko Gadget Murah"
- Inventory tracking (FIFO costing)
- Multi-marketplace sales (Tokopedia, Shopee)
- Marketplace fee deductions
- Cash flow from marketplace withdrawals

**Test Scenarios**:
- Product catalog with inventory
- Purchase transactions (stock in)
- Sales via marketplace (auto-COGS)
- Marketplace fee recognition
- Inventory adjustments (stock opname)
- Inventory reports (stock card, valuation)
- Profitability analysis per product

#### 3. Manufacturing Industry (Most Complex)
**Business Model**: Coffee & Pastry Shop - "Kedai Kopi Nusantara"
- Bill of Materials (BOM) with varying complexity
- Production orders (made-to-order + batch)
- Raw material inventory
- Finished goods inventory
- Multi-level BOM (sub-assemblies)
- Cost accumulation

**Product Tiers (Simple → Complex)**:
1. **Simple BOM (Drinks)**: Kopi Susu, Es Kopi Gula Aren - 3-4 ingredients
2. **Medium BOM (Pastries)**: Croissant, Roti Bakar - 5-6 ingredients
3. **Complex BOM (Cakes)**: Tiramisu, Cake Slice - multi-level with sub-assemblies

**Test Scenarios**:
- Raw material management (coffee beans, milk, flour, butter, eggs, sugar)
- Simple BOM: Kopi Susu Gula Aren (espresso + milk + palm sugar syrup)
- Medium BOM: Croissant (flour, butter, eggs, yeast, salt, milk)
- Complex BOM: Tiramisu with sub-assemblies (mascarpone cream, ladyfinger base)
- Production order - made to order (drinks)
- Production order - batch production (pastries, cakes)
- Component consumption tracking
- Finished goods receipt
- Cost of Goods Manufactured (COGM)
- Inventory valuation (raw materials + WIP + finished goods)
- Product profitability analysis per category

#### 4. Campus Industry (Education Sector)
**Business Model**: Private University - "STMIK Tazkia"
- Tuition fee revenue (SPP per semester)
- Multi-period revenue recognition (semester-based)
- Student receivables management
- Scholarship deductions
- Payroll for lecturers and staff
- Operating grants and donations

**Revenue Streams**:
- SPP (Sumbangan Pembinaan Pendidikan) - tuition fees
- Uang Pangkal - enrollment fees
- Biaya Praktikum - lab fees
- Wisuda - graduation fees

**Test Scenarios**:
- Student billing per semester
- Payment collection (full, installment, scholarship)
- Revenue recognition per academic period
- Lecturer payroll with honorarium
- Operating expense management
- Scholarship fund allocation
- Financial reports by academic year
- Receivables aging by student/batch

---

## Test Data Organization

### Key Principle: No Duplication

Test migrations load master data (COA, templates, salary, tax) from **industry seed packs** via SQL `COPY` commands. This ensures:
1. Single source of truth for seed data
2. Tests validate the same data users will import
3. No drift between production seeds and test data

### New Directory Structure
```
src/test/resources/db/
├── testmigration/
│   ├── V800__base_test_infrastructure.sql    # Test users (operator, auditor, etc.)
│   │
│   ├── V810__load_service_seed.sql           # COPY from industry-seed/it-service/
│   ├── V811__service_transactions.sql        # Clients, projects, transactions
│   │
│   ├── V820__load_seller_seed.sql            # COPY from industry-seed/online-seller/
│   ├── V821__seller_transactions.sql         # Products, purchases, sales
│   │
│   ├── V830__load_coffee_seed.sql            # COPY from industry-seed/coffee-shop/
│   ├── V831__coffee_production.sql           # BOM, production orders, sales
│   │
│   ├── V840__load_campus_seed.sql            # COPY from industry-seed/campus/
│   └── V841__campus_transactions.sql         # Students, billing, payments
```

### Loading Seed Data in Test Migrations

**V810 - Load IT Service Seed (Example)**
```sql
-- Load COA from industry seed pack
\COPY chart_of_accounts FROM '../../industry-seed/it-service/seed-data/01_chart_of_accounts.csv'
    WITH (FORMAT csv, HEADER true);

-- Load templates
\COPY journal_templates FROM '../../industry-seed/it-service/seed-data/04_journal_templates.csv'
    WITH (FORMAT csv, HEADER true);

-- Load salary components
\COPY salary_components FROM '../../industry-seed/it-service/seed-data/06_salary_components.csv'
    WITH (FORMAT csv, HEADER true);

-- Load tax deadlines
\COPY tax_deadlines FROM '../../industry-seed/it-service/seed-data/07_tax_deadlines.csv'
    WITH (FORMAT csv, HEADER true);
```

**Note:** If PostgreSQL `\COPY` doesn't work in Flyway, use Java-based migration (V810__LoadServiceSeed.java) that reads CSV files programmatically.

### Test Data Design per Industry

#### Service Industry Test Data (V810-V811)

**V810 - Load Seed Data**
- Loads from `industry-seed/it-service/seed-data/`
- 75 COA accounts, 37+ templates, 17 salary components, 8 tax deadlines

**V811 - Test Transactions**
```
Company: PT ArtiVisi Intermedia (IT Consulting)
Fiscal Year: 2024 (Jan-Dec)

Clients:
- CLI001: PT Bank Mandiri
- CLI002: PT Telkom Indonesia
- CLI003: PT Pertamina

Projects:
- PRJ001-004: Various consulting/training projects

Transactions (Jan-Mar 2024):
- Service revenue with PPN + PPh 23 withholding
- Operating expenses with PPN Masukan
- Payroll with PPh 21

Employees:
- EMP001: Budi Santoso (Senior Consultant) - Rp 15,000,000/month
- EMP002: Dewi Lestari (Consultant) - Rp 10,000,000/month
- EMP003: Agus Wijaya (Junior) - Rp 5,000,000/month

Payroll Runs: Jan, Feb, Mar 2024 (APPROVED status)
Tax Completions: Jan, Feb (completed), Mar (pending)
```

#### Online Seller Test Data (V820-V821)

**V820 - Load Seed Data**
- Loads from `industry-seed/online-seller/seed-data/`
- 87 COA accounts (includes marketplace saldo, inventory), 37 templates, 17 salary, 8 tax

**V821 - Test Transactions**
```
Company: Toko Gadget Murah (Electronics Reseller)
Fiscal Year: 2024

Products:
- PRD001: iPhone 15 Pro (FIFO costing)
- PRD002: Samsung Galaxy S24 (FIFO costing)
- PRD003: USB Cable Type-C (Weighted Avg)
- PRD004: Phone Case Universal (Weighted Avg)

Inventory Transactions (Jan-Mar 2024):
- Purchase: 10x iPhone @ Rp 15,000,000
- Purchase: 20x Samsung @ Rp 12,000,000
- Sale via Tokopedia: 5x iPhone @ Rp 17,000,000 (2% fee)
- Sale via Shopee: 8x Samsung @ Rp 14,000,000 (3% fee)
- Adjustment: +5 USB Cable (stock opname)
- Withdraw: Tokopedia to BCA - Rp 16,650,000
- Withdraw: Shopee to BCA - Rp 13,580,000
```

#### Manufacturing Test Data (V830-V831)

**V830 - Load Seed Data**
- Loads from `industry-seed/coffee-shop/seed-data/` (NEW - to be created)
- COA with raw materials, WIP, finished goods, sub-assembly accounts
- Templates for production, COGS, sales

**V831 - Test Transactions**
```
Company: Kedai Kopi Nusantara (Coffee & Pastry Shop)
Fiscal Year: 2024

Raw Materials:
- Biji Kopi Arabica, Susu Segar, Gula Aren, Es Batu
- Tepung, Butter, Telur, Ragi, Garam
- Mascarpone, Ladyfinger, Whipping Cream, Cocoa

Sub-Assemblies:
- SUB001: Sirup Gula Aren (500ml bottle)
- SUB002: Mascarpone Cream Mix (batch)

Finished Goods:
- Drinks: Kopi Susu Gula Aren, Es Kopi Susu, Americano
- Pastries: Croissant, Roti Bakar Coklat
- Cakes: Tiramisu Slice, Cheesecake Slice
```

BOM Examples (defined in seed data):
- Simple: Kopi Susu Gula Aren (4 ingredients) → Rp 8,575/cup, sell Rp 28,000
- Medium: Croissant (6 ingredients) → Rp 6,276/piece, sell Rp 25,000
- Complex: Tiramisu with Mascarpone Mix sub-assembly → Rp 13,315/slice, sell Rp 45,000

Production Orders:
- PO001: Produce Sirup Gula Aren sub-assembly (batch of 5 bottles)
- PO002: Produce 48 Croissant (batch production)
- PO003: Produce Mascarpone Cream Mix (sub-assembly batch)
- PO004: Produce 10 Tiramisu Slices (uses sub-assembly)

Sales:
- 85x Kopi Susu, 70x Es Kopi, 40x Croissant, 25x Roti Bakar, 8x Tiramisu
- Total Revenue: Rp 5,940,000 | COGS: Rp 1,873,435 | Margin: 68.5%
```

#### Campus Test Data (V840-V841)

**V840 - Load Seed Data**
- Loads from `industry-seed/campus/seed-data/` (NEW - to be created)
- COA with education-specific accounts (SPP receivables, scholarship funds, etc.)
- Templates for tuition billing, payment collection, payroll

**V841 - Test Transactions**
```
Institution: STMIK Tazkia
Academic Year: 2024/2025

Programs:
- S1 Teknik Informatika (TI)
- S1 Sistem Informasi (SI)
- D3 Manajemen Informatika (MI)

Students (sample):
- STD001: Ahmad Fauzi (TI, Semester 3) - SPP Rp 7,500,000
- STD002: Siti Aminah (SI, Semester 1) - SPP Rp 7,500,000 + Uang Pangkal Rp 5,000,000
- STD003: Budi Hartono (MI, Semester 5) - SPP Rp 6,000,000, Beasiswa 50%

Transactions (Semester Ganjil 2024):
- Billing: Generate SPP for all active students
- Payment: STD001 bayar lunas Rp 7,500,000
- Payment: STD002 bayar cicilan 1 Rp 4,000,000
- Payment: STD003 bayar setelah potongan beasiswa Rp 3,000,000
- Payroll: Gaji dosen + honorarium mengajar
- Expense: Biaya operasional kampus

Reports:
- Receivables aging per angkatan
- Revenue per program studi
- Scholarship fund utilization
```

---

## CSV-Driven Transaction Tests

Tests read transaction sequences from CSV files and execute real UI interactions. This validates the dynamic form implementation and ensures the app works as users would experience it.

### CSV Format

```csv
sequence,date,template,inputs,description,reference,project,notes
1,2024-01-05,Pendapatan Jasa + PPN,amount:50000000,Konsultasi PT Mandiri Jan,INV-001,,
2,2024-01-10,Penjualan Tokopedia,var_grossSales:17000000|var_adminFee:340000,iPhone via Tokopedia,TOPED-001,,
3,2024-01-12,Penjualan Persediaan,var_revenueAmount:17000000|var_cogsAmount:15000000,Sale with COGS,,,
```

**Column Definitions:**

| Column | Description |
|--------|-------------|
| `sequence` | Execution order (1, 2, 3...) |
| `date` | Transaction date (yyyy-MM-dd) |
| `template` | Template name (exact match) |
| `inputs` | Pipe-separated field:value pairs |
| `description` | Transaction description |
| `reference` | Reference number (optional) |
| `project` | Project code (optional) |
| `notes` | Additional notes (optional) |

**Input Field Types:**

| Prefix | Usage | Example |
|--------|-------|---------|
| `amount` | SIMPLE template amount | `amount:50000000` |
| `var_<name>` | DETAILED template variables | `var_grossSales:17000000` |
| `accountMapping[<lineId>]` | Dynamic account selection | `accountMapping[uuid]:acc-uuid` |

### Test Directory Structure

```
src/test/resources/testdata/
├── service/
│   ├── transactions.csv          # Transaction sequence for service industry
│   ├── expected-pnl.csv          # Expected P&L values for report verification
│   └── expected-balance.csv      # Expected Balance Sheet values
├── seller/
│   ├── transactions.csv          # Marketplace transactions
│   └── expected-inventory.csv    # Expected inventory levels
├── coffee/
│   ├── transactions.csv          # Production + sales transactions
│   └── production-orders.csv     # BOM/production specific sequences
└── campus/
    ├── transactions.csv          # Tuition billing and payments
    ├── expected-receivables.csv  # Expected receivables per student/batch
    └── expected-revenue.csv      # Expected revenue per program
```

### Test Execution Code

```java
@TestFactory
Stream<DynamicTest> executeTransactionSequence() {
    List<TransactionRow> transactions = CsvLoader.load("service/transactions.csv");

    return transactions.stream()
        .sorted(Comparator.comparing(TransactionRow::sequence))
        .map(tx -> DynamicTest.dynamicTest(
            "Seq " + tx.sequence() + ": " + tx.template(),
            () -> executeTransaction(tx)
        ));
}

void executeTransaction(TransactionRow tx) {
    // Navigate with template
    String templateId = templateRepository.findByName(tx.template()).getId();
    page.navigate(baseUrl + "/transactions/new?templateId=" + templateId);

    // Enter date
    page.locator("#transactionDate").fill(tx.date());

    // Parse and enter inputs
    for (var entry : parseInputs(tx.inputs()).entrySet()) {
        String fieldId = entry.getKey();
        String value = entry.getValue();

        if (fieldId.startsWith("accountMapping")) {
            page.locator("select[name='" + fieldId + "']").selectOption(value);
        } else if (fieldId.equals("amount")) {
            page.locator("#amount").fill(value);
        } else {
            // DETAILED template variables (var_grossSales, etc.)
            page.locator("#" + fieldId).fill(value);
        }
    }

    // Enter description and other fields
    page.locator("#description").fill(tx.description());
    if (tx.reference() != null) page.locator("#referenceNumber").fill(tx.reference());
    if (tx.project() != null) page.locator("#project").selectOption(tx.project());

    // Click save & post
    page.locator("#btn-simpan-posting").click();

    // Wait for success redirect
    page.waitForURL("**/transactions/*");
}
```

### Benefits

1. **Real UI interaction** - validates dynamic forms, Alpine.js bindings, HTMX updates
2. **Ordered execution** - CSV sequence controls order, no @Order annotation needed
3. **Readable test data** - business logic visible in spreadsheet format (readonly)
4. **Template-agnostic** - same test code handles SIMPLE and DETAILED templates

### Screenshot Capture for User Manual

Tests capture screenshots at key steps to update user manual documentation.

```java
void executeTransaction(TransactionRow tx) {
    // ... fill form ...

    // Capture screenshot before submit
    if (tx.captureScreenshot()) {
        String filename = String.format("manual/%s/%02d-%s.png",
            industry, tx.sequence(), slugify(tx.description()));
        page.screenshot(new Page.ScreenshotOptions().setPath(Paths.get(filename)));
    }

    page.locator("#btn-simpan-posting").click();
    page.waitForURL("**/transactions/*");

    // Capture result screenshot
    if (tx.captureScreenshot()) {
        String filename = String.format("manual/%s/%02d-%s-result.png",
            industry, tx.sequence(), slugify(tx.description()));
        page.screenshot(new Page.ScreenshotOptions().setPath(Paths.get(filename)));
    }
}
```

**Screenshot Output:**
```
docs/user-manual/screenshots/
├── service/
│   ├── 01-konsultasi-jan-form.png
│   ├── 01-konsultasi-jan-result.png
│   ├── 02-bayar-gaji-form.png
│   └── ...
├── seller/
│   ├── 01-pembelian-iphone-form.png
│   └── ...
├── coffee/
│   └── ...
└── campus/
    └── ...
```

**CSV Column Addition:**
```csv
sequence,date,template,inputs,description,reference,project,notes,screenshot
1,2024-01-05,Pendapatan Jasa + PPN,amount:50000000,Konsultasi Jan,INV-001,,,true
2,2024-01-10,Bayar Gaji,amount:30000000,Gaji Jan,,,,false
```

---

## Test Class Organization

### New Test Package Structure
```
src/test/java/com/artivisi/accountingfinance/functional/
├── base/
│   └── PlaywrightTestBase.java          # Existing base class
│
├── service/                              # Service Industry Tests
│   ├── ServiceAccountingTest.java       # COA, templates, transactions
│   ├── ServiceClientProjectTest.java    # Clients, projects, milestones
│   ├── ServicePayrollTest.java          # Payroll processing
│   ├── ServiceTaxComplianceTest.java    # PPh 21/23, tax reports
│   └── ServiceReportsTest.java          # P&L, Balance Sheet, Cash Flow
│
├── seller/                               # Online Seller Tests
│   ├── SellerInventoryTest.java         # Products, purchases, stock
│   ├── SellerSalesTest.java             # Sales with auto-COGS
│   ├── SellerMarketplaceTest.java       # Marketplace-specific flows
│   └── SellerReportsTest.java           # Inventory reports, profitability
│
├── manufacturing/                        # Manufacturing Tests
│   ├── MfgMaterialsTest.java            # Raw material management
│   ├── MfgBomTest.java                  # BOM creation and management
│   ├── MfgProductionTest.java           # Production order execution
│   └── MfgCostingTest.java              # COGM, inventory valuation
│
├── campus/                               # Campus/Education Tests
│   ├── CampusBillingTest.java           # Student billing per semester
│   ├── CampusPaymentTest.java           # Payment collection, installments
│   ├── CampusScholarshipTest.java       # Scholarship allocation
│   └── CampusReportsTest.java           # Receivables, revenue per program
│
├── common/                               # Cross-industry Tests
│   ├── SecurityTest.java                # Login, RBAC, audit
│   ├── UserManagementTest.java          # User CRUD
│   └── SettingsTest.java                # App configuration
│
└── page/                                 # Page Objects (existing 84 files)
```

### Test Class Mapping

| Old Test File | New Location | Industry |
|--------------|--------------|----------|
| TransactionTest.java | service/ServiceAccountingTest.java | Service |
| ClientTest.java | service/ServiceClientProjectTest.java | Service |
| ProjectTest.java | service/ServiceClientProjectTest.java | Service |
| PayrollTest.java | service/ServicePayrollTest.java | Service |
| TaxReportTest.java | service/ServiceTaxComplianceTest.java | Service |
| IncomeStatementTest.java | service/ServiceReportsTest.java | Service |
| BalanceSheetTest.java | service/ServiceReportsTest.java | Service |
| CashFlowReportTest.java | service/ServiceReportsTest.java | Service |
| ProductTest.java | seller/SellerInventoryTest.java | Seller |
| InventoryTransactionTest.java | seller/SellerInventoryTest.java | Seller |
| SalesIntegrationTest.java | seller/SellerSalesTest.java | Seller |
| InventoryReportTest.java | seller/SellerReportsTest.java | Seller |
| BomProductionTest.java | manufacturing/MfgBomTest.java + MfgProductionTest.java | Manufacturing |
| UserManagementTest.java | common/UserManagementTest.java | Common |
| SettingsTest.java | common/SettingsTest.java | Common |

---

## Implementation Phases

### Phase 0: Slim Down Production Migrations
1. Backup current V004 content
2. Rewrite V004 to minimal bootstrap (admin user + fiscal year only)
3. Move current COA/templates to IT Services seed pack (if not already there)
4. Verify app starts with blank slate
5. Verify seed import still works

**Deliverables**:
- Minimal V004 (~20 lines)
- App runs with empty COA/templates

### Phase 1: Service Industry Tests
1. Create V810 (load IT Service seed via COPY or Java migration)
2. Create V811 (test transactions: clients, projects, payroll, PKP taxes)
3. Create V800 (base test users: operator, auditor, etc.)
4. Consolidate existing service-related tests into `service/` package
5. Delete old V901-V907 after migration

**Deliverables**:
- 5 test files in `service/` package
- 3 migration files (V800, V810, V811)

### Phase 2: Online Seller Tests
1. Create V820 (load Online Seller seed)
2. Create V821 (test transactions: products, inventory, marketplace sales)
3. Create seller test classes
4. Test FIFO costing and auto-COGS

**Deliverables**:
- 4 test files in `seller/` package
- 2 migration files (V820, V821)

### Phase 3: Manufacturing Tests
1. Create `industry-seed/coffee-shop/seed-data/` (NEW)
2. Create V830 (load Coffee Shop seed)
3. Create V831 (test transactions: BOM, production, sales)
4. Create manufacturing test classes

**Deliverables**:
- 4 test files in `manufacturing/` package
- 2 migration files (V830, V831)
- New coffee-shop seed pack

### Phase 4: Campus Tests
1. Create `industry-seed/campus/seed-data/` (NEW)
2. Create V840 (load Campus seed)
3. Create V841 (test transactions: billing, payments, scholarships)
4. Create campus test classes

**Deliverables**:
- 4 test files in `campus/` package
- 2 migration files (V840, V841)
- New campus seed pack

### Phase 5: Cleanup & Documentation
1. Delete old V900-V912 files
2. Update common tests to use base infrastructure (V800)
3. Update CLAUDE.md with new test structure

---

## ID Convention

Hierarchical structure using **numeric hex prefixes** for valid PostgreSQL UUIDs.

**IMPORTANT**: PostgreSQL UUIDs only accept hex characters (0-9, a-f). Letters like 's', 'c', 'u' are invalid.

**Industry Prefixes (first 2 digits):**
```
51 = Service (IT Services / PKP)
52 = Seller (E-commerce / Online Seller)
53 = Coffee (Manufacturing)
54 = Campus (University/Education)
00 = Base/Common (used by V800)
```

**Entity Type (digits 3-4):**
```
10 = Client
20 = Project
21 = Project Milestone
30 = Employee
31 = Employee Salary Component
40 = Transaction
50 = Fiscal Period
60 = User
61 = User Role
70 = Journal Entry
71 = Journal Entry Line
80 = Payroll Run
81 = Payroll Detail
90 = Tax Completion
```

**Format**: `{industry}{entity}{seq}-0000-0000-0000-000000000001`

**Examples:**
```
51100001-0000-0000-0000-000000000001  → Service Client #1 (PT Bank Mandiri)
51200001-0000-0000-0000-000000000001  → Service Project #1
51300001-0000-0000-0000-000000000001  → Service Employee #1 (Budi Santoso)
51400001-0000-0000-0000-000000000001  → Service Transaction #1
51700001-0000-0000-0000-000000000001  → Service Journal Entry #1

52100001-0000-0000-0000-000000000001  → Seller Client #1
52400001-0000-0000-0000-000000000001  → Seller Transaction #1

53100001-0000-0000-0000-000000000001  → Coffee Supplier #1
53400001-0000-0000-0000-000000000001  → Coffee Transaction #1

54100001-0000-0000-0000-000000000001  → Campus Student #1
54400001-0000-0000-0000-000000000001  → Campus Transaction #1 (SPP payment)
```

---

## Page Object Changes

Some page objects will need industry-specific variants, especially:
- **TransactionFormPage** - varies by template (service invoice vs marketplace sale vs production)
- **TemplateFormPage** - different fields for SIMPLE vs DETAILED templates
- **ProductionOrderPage** - only used in manufacturing tests

Approach: Create base page object with common functionality, extend for industry-specific behavior if needed.

---

## Test Execution

```bash
# Run all tests
./mvnw test

# Run specific industry tests
./mvnw test -Dtest="com.artivisi.accountingfinance.functional.service.*"
./mvnw test -Dtest="com.artivisi.accountingfinance.functional.seller.*"
./mvnw test -Dtest="com.artivisi.accountingfinance.functional.manufacturing.*"
./mvnw test -Dtest="com.artivisi.accountingfinance.functional.campus.*"
```

---

## Decisions

| Question | Decision |
|----------|----------|
| Spring profiles? | No. Single tenant app, one DB per company. |
| Test data factories? | No. SQL migrations with realistic seed data suffice. Add factories later if needed. |
| Load seed data method? | PostgreSQL `COPY` command. Committed to Postgres, no DB portability needed. |
| Page object reorganization? | Yes, for template/transaction forms that vary by industry. |

---

## Success Criteria

1. **No Duplication**: Test data loads from industry seed packs (single source of truth)
2. **Minimal V004**: Production migrations contain only admin user + fiscal year
3. **Data Coherence**: Each industry's test data tells a complete, believable business story
4. **Clear Ownership**: Each test class knows exactly which seed pack it depends on
5. **Coverage**: All existing functionality still tested

---

## Test Data Verification Requirements

**IMPORTANT**: All functional tests MUST verify actual data values, not just page visibility.

### Verification Categories

| Category | What to Verify | Example |
|----------|---------------|---------|
| **Financial Amounts** | Report totals match calculated values | `verifyTotalRevenue("359.700.000")` |
| **Row Counts** | Entity counts match seed data | `verifyClientCount(3)` |
| **Journal Entries** | Account codes and amounts from CSV | `verifyJournalEntryAccountCode(0, "1.1.01")` |

### Required Patterns

**Transaction Execution Tests:**
```java
// After saveAndPost(), verify journal entries
transactionDetailPage.verifyJournalEntriesVisible()
    .verifyJournalEntryCount(2)
    .verifyJournalEntryAccountCode(0, tx.expectedDebitAccount())
    .verifyJournalEntryDebit(0, tx.expectedAmount())
    .verifyJournalEntryAccountCode(1, tx.expectedCreditAccount())
    .verifyJournalEntryCredit(1, tx.expectedAmount())
    .verifyJournalBalanced();
```

**Report Tests:**
```java
// Verify calculated amounts, not just visibility
incomeStatementPage.navigate("2024-01-01", "2024-02-28")
    .verifyTotalRevenue("359.700.000")
    .verifyTotalExpense("8.880.000")
    .verifyNetIncome("350.820.000");
```

**List Tests:**
```java
// Verify row counts from seed data
clientListPage.navigate()
    .verifyClientCount(3);  // Not just .not().hasCount(0)
```

### CSV Format for Expected Values

Include expected journal entry data in transaction CSV:
```csv
sequence,date,templateName,inputs,description,...,expectedDebitAccount,expectedCreditAccount,expectedAmount
1,2024-01-01,Setoran Modal,amount:500000000,Setoran Modal,...,1.1.01,3.1.01,500.000.000
```

---

## Test Independence and CSV-Driven Architecture

**CRITICAL PRINCIPLE**: Report tests MUST NOT depend on transaction execution tests. Both must load from the SAME CSV files to ensure data consistency.

### Correct Pattern: CSV-Driven Test Data

All tests load transaction data from CSV files. This ensures:
1. **Data Consistency** - Same data used for UI execution and report verification
2. **Test Independence** - Report tests don't need transaction tests to run first
3. **Maintainability** - Test data changes only need to be made in one place

### Implementation Pattern

#### Transaction Execution Tests (via UI)

Execute transactions through Playwright UI interactions. Uses `@TestFactory` for dynamic tests from CSV.

**Service Industry Example:**
```java
@TestFactory
@Order(1)
@DisplayName("Execute transactions from CSV")
Stream<DynamicTest> executeTransactionsFromCsv() {
    // Load from CSV
    List<TransactionRow> transactions = CsvLoader.loadTransactions("service/transactions.csv");

    return transactions.stream()
        .sorted(Comparator.comparing(TransactionRow::sequence))
        .map(tx -> DynamicTest.dynamicTest(
            "Tx " + tx.sequence() + ": " + tx.description(),
            () -> executeTransaction(tx)  // Execute via Playwright
        ));
}

private void executeTransaction(TransactionRow tx) {
    loginAsAdmin();

    // Find template by name from CSV
    UUID templateId = templateRepository.findByTemplateName(tx.templateName()).get().getId();

    // Fill form via Playwright
    transactionFormPage.navigateWithTemplate(templateId)
        .fillDate(tx.date())
        .fillInputs(tx.inputs())
        .fillDescription(tx.description())
        .fillReferenceNumber(tx.reference())
        .saveAndPost();

    // Verify journal entries match CSV expectations
    transactionDetailPage.verifyJournalEntryAccountCode(0, tx.expectedDebitAccount())
        .verifyJournalEntryDebit(0, tx.expectedAmount())
        .verifyJournalEntryAccountCode(1, tx.expectedCreditAccount())
        .verifyJournalEntryCredit(1, tx.expectedAmount());
}
```

**Seller Inventory Example:**
```java
@TestFactory
@Order(4)
@DisplayName("Execute inventory transactions from CSV")
Stream<DynamicTest> executeInventoryTransactionsFromCsv() {
    // Load from CSV
    List<InventoryTransactionRow> transactions =
        CsvLoader.loadInventoryTransactions("seller/transactions.csv");

    return transactions.stream()
        .map(tx -> DynamicTest.dynamicTest(
            "Tx " + tx.sequence() + ": " + tx.transactionType() + " " + tx.productCode(),
            () -> executeInventoryTransaction(tx)  // Execute via Playwright
        ));
}

private void executeInventoryTransaction(InventoryTransactionRow tx) {
    loginAsAdmin();

    // Get product from repository
    Product product = productRepository.findByCode(tx.productCode()).orElseThrow();

    // Navigate to correct form
    navigateTo(getFormUrl(tx.transactionType()));

    // Fill form via Playwright
    page.locator("#productId").selectOption(product.getId().toString());
    page.locator("#transactionDate").fill(tx.date());
    page.locator("#quantity").fill(String.valueOf(tx.quantity()));

    if (tx.transactionType().equals("PURCHASE")) {
        page.locator("#unitCost").fill(String.valueOf(tx.unitCost()));
    }

    page.locator("#btn-submit").click();
    page.waitForURL("**/inventory/transactions");
}
```

#### Report Tests (Programmatic Data Loading)

Load SAME CSV and create transactions programmatically using services/repositories. Uses `@BeforeAll` and `@TestInstance(PER_CLASS)`.

**Service Industry Example:**
```java
@DisplayName("Service Industry - Financial Reports")
@Import(ServiceTestDataInitializer.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ServiceReportsTest extends PlaywrightTestBase {

    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private JournalEntryRepository journalEntryRepository;
    @Autowired
    private ChartOfAccountRepository accountRepository;
    @Autowired
    private JournalTemplateRepository templateRepository;

    @BeforeAll
    public void setupTestTransactions() {
        // Load SAME CSV as transaction execution test
        List<TransactionRow> transactions = CsvLoader.loadTransactions("service/transactions.csv");

        // Create transactions programmatically using CSV data
        for (TransactionRow txRow : transactions) {
            // Get template by name from CSV
            JournalTemplate template = templateRepository.findByName(txRow.templateName())
                .orElseThrow(() -> new RuntimeException("Template not found: " + txRow.templateName()));

            // Parse amount from inputs
            BigDecimal amount = parseAmount(txRow.inputs());

            // Create transaction entity
            Transaction tx = createTransaction(template, LocalDate.parse(txRow.date()),
                txRow.description(), txRow.reference(), amount);

            // Get accounts from CSV expected values
            ChartOfAccount debitAccount = accountRepository.findByAccountCode(txRow.expectedDebitAccount())
                .orElseThrow(() -> new RuntimeException("Account not found: " + txRow.expectedDebitAccount()));
            ChartOfAccount creditAccount = accountRepository.findByAccountCode(txRow.expectedCreditAccount())
                .orElseThrow(() -> new RuntimeException("Account not found: " + txRow.expectedCreditAccount()));

            // Create journal entries
            createJournalEntry(tx, debitAccount, amount, BigDecimal.ZERO);
            createJournalEntry(tx, creditAccount, BigDecimal.ZERO, amount);
        }
    }

    private BigDecimal parseAmount(String inputs) {
        // Parse "amount:500000000" format
        String[] parts = inputs.split(":");
        if (parts.length == 2 && parts[0].equals("amount")) {
            return new BigDecimal(parts[1]);
        }
        throw new RuntimeException("Cannot parse amount from inputs: " + inputs);
    }

    private Transaction createTransaction(JournalTemplate template, LocalDate date,
                                         String description, String reference, BigDecimal amount) {
        Transaction tx = new Transaction();
        tx.setTransactionDate(date);
        tx.setDescription(description);
        tx.setReferenceNumber(reference);
        tx.setAmount(amount);
        tx.setJournalTemplate(template);
        tx.setStatus(TransactionStatus.POSTED);
        tx.setPostedAt(LocalDateTime.now());
        tx.setPostedBy("admin");
        tx.setCreatedBy("admin");
        return transactionRepository.save(tx);
    }

    private void createJournalEntry(Transaction transaction, ChartOfAccount account,
                                   BigDecimal debit, BigDecimal credit) {
        JournalEntry entry = new JournalEntry();
        entry.setTransaction(transaction);
        entry.setAccount(account);
        entry.setDebitAmount(debit);
        entry.setCreditAmount(credit);
        entry.setPostedAt(transaction.getPostedAt());
        entry.setCreatedBy("admin");
        journalEntryRepository.save(entry);
    }

    @Test
    @DisplayName("Should display Income Statement with correct calculations")
    void shouldDisplayIncomeStatement() {
        loginAsAdmin();
        initPageObjects();

        // Verify report shows data from CSV
        incomeStatementPage.navigate("2024-01-01", "2024-02-28")
            .verifyTotalRevenue("359.700.000")  // Sum from CSV
            .verifyTotalExpense("8.880.000")
            .verifyNetIncome("350.820.000");

        takeManualScreenshot("service/report-income-statement");
    }
}
```

**Seller Inventory Example:**
```java
@DisplayName("Online Seller - Reports")
@Import(SellerTestDataInitializer.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class SellerReportsTest extends PlaywrightTestBase {

    @Autowired
    private InventoryService inventoryService;
    @Autowired
    private ProductRepository productRepository;

    @BeforeAll
    public void setupTestInventoryTransactions() {
        // Load SAME CSV as transaction execution test
        List<InventoryTransactionRow> transactions =
            CsvLoader.loadInventoryTransactions("seller/transactions.csv");

        // Create inventory transactions programmatically using CSV data
        for (InventoryTransactionRow txRow : transactions) {
            // Get product by code from CSV
            Product product = productRepository.findByCode(txRow.productCode())
                .orElseThrow(() -> new RuntimeException("Product not found: " + txRow.productCode()));

            LocalDate date = LocalDate.parse(txRow.date());
            BigDecimal quantity = BigDecimal.valueOf(txRow.quantity());

            // Create transaction using InventoryService based on type from CSV
            switch (txRow.transactionType()) {
                case "PURCHASE" ->
                    inventoryService.recordPurchase(product.getId(), date, quantity,
                        txRow.unitCost(), txRow.reference(), txRow.notes());
                case "SALE" ->
                    inventoryService.recordSale(product.getId(), date, quantity,
                        txRow.unitPrice(), txRow.reference(), txRow.notes());
                case "ADJUSTMENT_IN" ->
                    inventoryService.recordAdjustmentIn(product.getId(), date, quantity,
                        txRow.unitCost(), txRow.reference(), txRow.notes());
                case "ADJUSTMENT_OUT" ->
                    inventoryService.recordAdjustmentOut(product.getId(), date, quantity,
                        txRow.reference(), txRow.notes());
                default ->
                    throw new RuntimeException("Unknown transaction type: " + txRow.transactionType());
            }
        }
    }

    @Test
    @DisplayName("Should display inventory stock balance report with 4 products")
    void shouldDisplayStockBalanceReport() {
        loginAsAdmin();
        initPageObjects();

        // Verify report shows stock calculated from CSV transactions
        // CSV: 4 purchases + 4 sales + 1 adjustment = 4 products with stock
        inventoryReportPage.navigateStockBalance()
            .verifyPageTitle("Saldo Stok")
            .verifyReportTableVisible()
            .verifyProductCount(4);  // Calculated from CSV

        takeManualScreenshot("seller/report-stock-balance");
    }
}
```

### Key Differences Between Test Types

| Aspect | Transaction Execution Tests | Report Tests |
|--------|---------------------------|-------------|
| **Purpose** | Validate UI interactions | Validate report calculations |
| **Execution** | Via Playwright (page.locator, click, fill) | Via services/repositories |
| **Data Source** | Load CSV, execute via UI | Load SAME CSV, create programmatically |
| **Dependencies** | None | None (independent!) |
| **Test Method** | `@TestFactory` with dynamic tests | Regular `@Test` methods |
| **Setup** | None needed | `@BeforeAll` creates test data |
| **Lifecycle** | Per-method (default) | `@TestInstance(PER_CLASS)` for shared context |

### Template Requirements for Tests

All HTML templates used by tests MUST have test IDs for reliable locators:

**Form Elements:**
```html
<input type="text" id="transactionDate" ...>
<input type="text" id="description" ...>
<input type="text" id="referenceNumber" ...>
<button type="submit" id="btn-submit" ...>
```

**Report Elements:**
```html
<h1 layout:fragment="page-title" th:id="page-title">Report Title</h1>
<table th:id="report-table" class="...">
  <tbody>
    <tr th:each="item : ${items}">...</tr>
  </tbody>
</table>

<!-- Summary values -->
<div th:id="total-revenue">Rp 359.700.000</div>
<div th:id="total-expense">Rp 8.880.000</div>
```

### CSV File Locations

```
src/test/resources/testdata/
├── service/
│   └── transactions.csv          # Used by ServiceTransactionExecutionTest + ServiceReportsTest
├── seller/
│   └── transactions.csv          # Used by SellerTransactionExecutionTest + SellerReportsTest
├── manufacturing/
│   └── transactions.csv          # (Future: CoffeeTransactionExecutionTest + CoffeeReportsTest)
└── campus/
    └── transactions.csv          # (Future: CampusTransactionExecutionTest + CampusReportsTest)
```

### Benefits of This Architecture

1. **Single Source of Truth** - CSV is the definitive test data
2. **Consistency** - UI execution and reports use identical data
3. **Independence** - Tests can run in any order
4. **Maintainability** - Change CSV once, affects both test types
5. **Readability** - CSV format is human-readable
6. **Verification** - Expected values documented in CSV columns

### Anti-Patterns to Avoid

❌ **DO NOT** hardcode transaction data in test classes:
```java
// BAD - hardcoded values, not CSV-driven
@BeforeAll
public void setupTestTransactions() {
    Transaction tx1 = createTransaction(template, LocalDate.of(2024, 1, 1),
        "Setoran Modal Awal 2024", "CAP-2024-001", BigDecimal.valueOf(500000000));
    createJournalEntry(tx1, cash, BigDecimal.valueOf(500000000), BigDecimal.ZERO);
    createJournalEntry(tx1, capital, BigDecimal.ZERO, BigDecimal.valueOf(500000000));
    // ... more hardcoded transactions
}
```

❌ **DO NOT** make report tests depend on transaction execution tests:
```java
// BAD - test execution order dependency
@DisplayName("Online Seller - Reports")
public class SellerReportsTest extends PlaywrightTestBase {
    // Expected Data (after SellerTransactionExecutionTest runs):  ❌ WRONG!
    // This creates test coupling - reports fail if transaction tests don't run first
}
```

❌ **DO NOT** only verify page title or visibility:
```java
// BAD - doesn't verify actual data
incomeStatementPage.navigate().verifyPageTitle();
```

❌ **DO NOT** use generic non-zero checks:
```java
// BAD - doesn't verify expected count
assertThat(page.locator("#client-table tbody tr")).not().hasCount(0);
```

✅ **DO** load same CSV for both execution and report tests:
```java
// GOOD - both tests use same CSV, ensures consistency
// ServiceTransactionExecutionTest.java
List<TransactionRow> transactions = CsvLoader.loadTransactions("service/transactions.csv");
executeTransaction(tx);  // Via Playwright

// ServiceReportsTest.java
List<TransactionRow> transactions = CsvLoader.loadTransactions("service/transactions.csv");
createTransaction(tx);  // Via repository/service
```

✅ **DO** verify specific expected values:
```java
// GOOD - verifies actual data matches test data
incomeStatementPage.verifyNetIncome("350.820.000");
clientListPage.verifyClientCount(3);
```

### Acceptable Page-Load-Only Tests

Some tests may only verify page loads when:
- No seed data exists for that feature (e.g., payroll runs)
- Testing UI controls existence (e.g., filter dropdowns, export buttons)
- Testing navigation flows (e.g., detail page loads after click)
