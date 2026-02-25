# Implementation Plan

## Guiding Principles

1. **Go Live Fast** - MVP with core features only, add incrementally
2. **No Breaking Changes** - Database migrations must be backward compatible
3. **Feature Flags** - New features behind toggles until stable
4. **Data Safety** - Production data must never be corrupted or lost

## Phase Overview

| Phase | Focus | Status |
|-------|-------|--------|
| **0** | Project Setup | ✅ Complete |
| **1** | Core Accounting (MVP) + 4 Industry Seed Packs | ✅ Complete |
| **2** | Tax Compliance + Cash Flow | ✅ Complete |
| **3** | Payroll + RBAC + Self-Service | ✅ Complete |
| **4** | Fixed Assets | ✅ Complete |
| **5** | Inventory & Production | ✅ Complete |
| **6** | Security Hardening | ✅ Complete |
| **7** | API Foundation | ✅ Complete |
| **8** | Bank Reconciliation | ✅ Complete |
| **9** | Analytics & Insights | ✅ Complete |
| **10** | Invoice & Bill Management | ✅ Complete |
| **11** | Recurring Transactions | ✅ Complete |
| **12** | Tax Data Management | 🔧 In Progress |
| **13** | WhatsApp Notifications | ⏳ Not Started |
| **—** | Future Enhancements | As needed |

---

## Phase 0: Project Setup ✅

### 0.1 Development Environment
- [x] Spring Boot 4.0 project structure
- [x] PostgreSQL 18 local setup (Testcontainers for tests)
- [x] Flyway migration setup
- [x] CI/CD pipeline with test execution and coverage (GitHub Actions)

### 0.2 Core Infrastructure
- [x] Spring Security configuration (session-based)
- [x] User authentication (login/logout)
- [x] Base entity classes (audit fields)
- [x] Exception handling (GlobalExceptionHandler)
- [x] Thymeleaf + HTMX base layout

### 0.3 Database Foundation
- [x] V001: users, company_config, audit_logs

---

## Phase 1: Core Accounting (MVP) ✅

### 1.1 Chart of Accounts ✅
- [x] Account entity and repository
- [x] Account types (asset, liability, equity, revenue, expense)
- [x] Hierarchical structure (parent/child)
- [x] Soft delete (base entity with deleted_at, @SQLRestriction filter)
- [x] Account CRUD UI
- [x] Account activation/deactivation

### 1.2 Journal Entries (Manual) ✅
- [x] Journal entry entity (header: date, description, reference, status)
- [x] Journal entry lines entity (account, debit, credit, memo)
- [x] Balance validation (debit = credit) before posting
- [x] Status workflow (draft → posted → void)
- [x] Immutable after posting (no edits, only void)
- [x] Void with reason
- [x] Journal entry CRUD UI
- [x] Journal entry list with filters (date range, status)
- [x] Account validation: cannot edit type if has journal entries
- [x] Account validation: cannot delete if has journal entries
- [x] Account dropdown: exclude inactive accounts

### 1.3 Basic Reports ✅
- [x] Trial Balance report
- [x] General Ledger report (all entries per account)
- [x] Balance Sheet (Laporan Posisi Keuangan)
- [x] Income Statement (Laporan Laba Rugi)
- [x] Date range filtering
- [x] PDF export
- [x] Excel export

### 1.4 Journal Templates (Basic) ✅
- [x] Template entity with versioning
- [x] Template lines entity (account mappings, debit/credit rules)
- [x] Category field (income, expense, payment, receipt, transfer)
- [x] Cash flow category field (operating, investing, financing)
- [x] Template CRUD UI
- [x] Template list with category filter
- [x] Template detail view
- [x] Template execution (generates journal entry)

### 1.5 Transactions ✅
- [x] Transaction entity with type and numbering
- [x] Transaction sequences per type (auto-increment per category)
- [x] Status workflow (draft → posted → void)
- [x] Transaction form UI (driven by template structure)
- [x] Account mapping from template
- [x] Transaction list with filters (date, type, status)
- [x] Transaction detail view
- [x] Post transaction (executes template → creates journal entry)
- [x] Void transaction (creates reversal entries)

### 1.6 Formula Support ✅
- [x] FormulaContext record with transaction data
- [x] FormulaEvaluator service using SpEL
- [x] TemplateExecutionEngine uses FormulaEvaluator
- [x] TransactionService uses FormulaEvaluator
- [x] Formula validation on template save
- [x] Supported patterns: amount, percentage, division, conditional, constants
- [x] FormulaEvaluatorTest - 28 unit tests
- [x] Functional tests for PPN and PPh 23 templates
- [x] Formula help panel in UI

### 1.7 Template Enhancements ✅
- [x] Template tags
- [x] User favorites (per-user, not global)
- [x] Usage tracking (last used, frequency)
- [x] Search functionality
- [x] Recently used list

### 1.7.5 HTMX Partial Rendering Optimization ✅
- [x] Template List: search/filter partial rendering
- [x] Journal List: filters/pagination partial rendering
- [x] Transaction List: filters + inline post/delete

### 1.8 Amortization Schedules ✅
- [x] Amortization schedule entity
- [x] Amortization entries entity (tracks each period)
- [x] Schedule CRUD UI
- [x] Schedule list with filters (type, status)
- [x] Manual schedule creation (user-initiated)
- [x] Auto-post toggle per schedule
- [x] Monthly batch job (generates journal entries)
- [x] Period-end dashboard integration (amortization widget with pending/overdue/due counts)
- [x] Remaining balance display
- [x] Schedule completion handling
- [x] Rounding handling (last period absorbs difference)

### 1.9 Project Tracking ✅
- [x] Project entity (code, name, client_id, status, budget)
- [x] Project CRUD UI
- [x] Project list with filters (status, client)
- [x] Link transactions to project
- [x] Project selection in transaction form
- [x] Project Profitability Report
- [x] Project Income Statement
- [x] Client entity (code, name, contact info, notes)
- [x] Client CRUD UI
- [x] Client list with search
- [x] Client Profitability Report
- [x] Client Revenue Ranking
- [x] Milestone entity and CRUD UI
- [x] Milestone status tracking (pending, in_progress, completed)
- [x] Milestone progress calculation
- [x] Milestone overdue detection (isOverdue method + red badge in UI)
- [x] Payment term entity and CRUD UI
- [x] Invoice entity and generation
- [x] Invoice status tracking (draft, sent, paid, overdue)
- [x] Auto-trigger revenue recognition on milestone completion

### 1.10 Dashboard KPIs ✅
- [x] Revenue (current month, vs previous month %)
- [x] Expenses (current month, vs previous month %)
- [x] Net Profit (current month, vs previous month %)
- [x] Profit Margin % (current month, vs previous month pts)
- [x] Cash Balance (sum of cash/bank accounts)
- [x] Receivables Total (Piutang Usaha balance)
- [x] Payables Total (Hutang Usaha balance)
- [x] DashboardService, DashboardController, HTMX endpoint
- [x] Month selector for historical comparison
- [x] Pending amortization entries count widget (dashboard widget with total/overdue/due this month)

### 1.11 Comprehensive User Manual ✅
- [x] 15 files in docs/user-manual/*.md (12 sections + 4 appendices)
- [x] Industry-specific guides: Service, Trading, Manufacturing, Education
- [x] Playwright functional tests generate screenshots automatically
- [x] Screenshots stored in target/user-manual/screenshots/

### 1.12 Data Import ✅
- [x] COA import from JSON file
- [x] COA import from Excel file (XLSX)
- [x] Validate account structure and codes
- [x] Preview before import
- [x] Clear before import option
- [x] Journal Template import from JSON
- [x] Template validation (lines, formulas, account references)
- [x] DataImportService and DataImportController

### 1.13 Deployment & Operations ✅
- [x] Systemd service configuration
- [x] Production configuration (application-prod.properties)
- [x] Document storage directory setup
- [x] Pulumi infrastructure (deploy/pulumi/)
- [x] Ansible configuration (deploy/ansible/)

**Phase 1 Deliverable:** ✅ Complete - Working accounting system with COA, journal entries, templates, transactions, formula support, amortization, project tracking, dashboard KPIs, user manual, data import, and deployment automation.

---

## Phase 2: Tax Compliance + Cash Flow

### 2.0 Infrastructure ✅
- [x] Local storage directory setup

### 2.1 Transaction Evidence (Document Attachment) ✅
- [x] Document entity (filename, content_type, size, storage_path, checksum)
- [x] Local filesystem storage service
- [x] File upload UI (drag-and-drop)
- [x] Attach document to transaction/journal entry
- [x] View document (inline for images/PDFs)
- [x] Download document
- [x] Delete document (soft delete)
- [x] File type validation (images, PDF)
- [x] File size limit (10MB)

### 2.2 Telegram Receipt Import ✅
- [x] Telegram webhook endpoint
- [x] Bot configuration and registration
- [x] Photo message handler
- [x] Google Cloud Vision OCR integration
- [x] Receipt data extraction (merchant, date, amount)
- [x] DraftTransaction entity
- [x] Draft list/detail/edit UI
- [x] Review and approve workflow
- [x] Reject with reason
- [x] MerchantMapping entity and CRUD UI

### 2.3 Tax Accounts Setup ✅
- [x] Pre-configured tax accounts in COA templates
- [x] PPN Masukan / Keluaran accounts
- [x] PPh 21, 23, 4(2), 25, 29 accounts

### 2.4 PPN Templates ✅
- [x] Penjualan + PPN Keluaran template
- [x] Pembelian + PPN Masukan template
- [x] PPN calculation (11%)

### 2.5 PPh Templates ✅
- [x] PPh 23 withholding templates (2%)
- [x] PPh 4(2) templates (10% for rental)
- [x] Tax payment templates (Setor PPh 21/23/4(2)/PPN/25)

### 2.6 Tax Reports ✅
- [x] PPN Summary Report
- [x] PPN Detail (Keluaran/Masukan)
- [x] PPh 23 Withholding Report
- [x] Coretax Excel export (e-Faktur, Bupot Unifikasi)
- [x] TaxTransactionDetail entity
- [x] Tax identification fields (NPWP, NITKU, NIK) on Client and CompanyConfig
- [x] Tax Object Code enum (PPh 23/4(2) codes)

### 2.7 Fiscal Period Management ✅
- [x] Fiscal periods entity
- [x] Period status (open, month_closed, tax_filed)
- [x] Soft lock on month close
- [x] Hard lock after tax filing
- [x] Period close workflow
- [x] Functional tests (9 Playwright tests)
- [x] User manual (54-kelola-periode-fiskal.md)

### 2.8 Tax Calendar ✅
- [x] Tax deadline configuration
- [x] Dashboard reminders
- [x] Monthly checklist
- [x] User manual (33-kalender-pajak.md)

### 2.9 Backup & Restore Utility ✅
- [x] Backup service (database + documents)
- [x] Coordinated backup (consistent state)
- [x] Backup to local directory
- [x] Restore utility with validation
- [x] Backup scheduling (manual trigger via Ansible)
- [x] Backup manifest (metadata, timestamp, checksums)
- [x] Backup rotation (configurable retention)
- [x] Remote sync (rsync, optional)
- [x] Notifications (webhook, optional)

### 2.10 Cash Flow Statement ✅
- [x] Cash flow report generation
- [x] Group by cash_flow_category from templates
- [x] Operating/Investing/Financing sections
- [x] PDF/Excel export
- [x] User manual (21-laporan-bulanan.md, Skenario 4)

**Phase 2 Deliverable:** Tax-compliant accounting with DJP export formats, document storage, Telegram receipt import, backup/restore, and cash flow reporting.

---

## Phase 3: Payroll

**Goal:** Full payroll with PPh 21 and BPJS

### 3.1 Employee Management ✅
- [x] Employee entity (with PTKP status, employment details, bank account, BPJS numbers)
- [x] Employee CRUD UI (list with search/filters, form, detail view)
- [x] PTKP status configuration (13 Indonesian tax status codes with annual amounts)
- [x] NPWP validation (format and uniqueness)
- [x] Functional tests (10 Playwright tests)
- [x] User manual (60-kelola-karyawan.md)

### 3.2 Salary Components ✅
- [x] SalaryComponent entity (code, name, type, isPercentage, defaultRate/defaultAmount, displayOrder, isTaxable, bpjsCategory)
- [x] SalaryComponentType enum (EARNING, DEDUCTION, COMPANY_CONTRIBUTION)
- [x] Component CRUD UI (list with search/type filter, form, detail view)
- [x] Activate/deactivate workflow (system components protected)
- [x] Preloaded 17 Indonesian salary components (GAPOK, Tunjangan, BPJS rates, PPh 21)
- [x] EmployeeSalaryComponent entity (employee-specific component values)
- [x] Percentage stored as entered (e.g., 4.0 for 4%), Java handles calculation
- [x] Functional tests (11 Playwright tests)
- [x] User manual (61-komponen-gaji.md)

### 3.3 BPJS Calculation ✅
- [x] BPJS Kesehatan rates (4% company + 1% employee, ceiling Rp 12,000,000)
- [x] BPJS Ketenagakerjaan rates (JKK 0.24%-1.74%, JKM 0.3%, JHT 3.7%+2%, JP 2%+1%)
- [x] JKK risk class support (5 classes for different industries)
- [x] JP ceiling (Rp 10,042,300 for 2025)
- [x] Company vs employee portion separation
- [x] BpjsCalculationService with BigDecimal precision
- [x] BpjsCalculationResult record with totals
- [x] BPJS Calculator UI (input salary, select risk class, view breakdown)
- [x] Ceiling warning display for high salaries
- [x] Unit tests (25 tests)
- [x] Functional tests (9 Playwright tests)
- [x] User manual (62-kalkulator-bpjs.md)

### 3.4 PPh 21 Calculation ✅
- [x] Progressive tax rates (5%-35%) per PP 58/2023
- [x] PTKP deduction by status (12 statuses)
- [x] Biaya jabatan (5%, max Rp 500,000/month)
- [x] Monthly vs annual calculation
- [x] Pph21CalculationService with BigDecimal precision
- [x] PPh 21 Calculator UI (input salary, select PTKP, toggle NPWP)
- [x] No NPWP penalty (20% higher tax)
- [x] Effective tax rate display
- [x] Take home pay calculation
- [x] Unit tests (28 tests)
- [x] Functional tests (12 Playwright tests)
- [x] User manual (63-kalkulator-pph21.md)

### 3.5 Payroll Processing ✅
- [x] PayrollRun entity (period, status workflow, totals)
- [x] PayrollDetail entity (per-employee breakdown, BPJS, PPh 21)
- [x] PayrollService (create, calculate, approve, post, cancel)
- [x] Monthly payroll workflow (Draft → Calculated → Approved → Posted)
- [x] Calculate all active employees with BPJS and PPh 21
- [x] Payroll UI (list, form, detail with summary and employee breakdown)
- [x] Post to journal via Transaction/Template system (domain separation)
- [x] Extended FormulaContext with generic variables map
- [x] Payroll journal template (grossSalary, companyBpjs, netPay, totalBpjs, pph21)
- [x] Functional tests (18 Playwright tests)
- [x] User manual (64-payroll-processing.md, updated 92-referensi-template.md)

### 3.6 Payroll Reports ✅
- [x] Payroll summary report (PDF/Excel)
- [x] PPh 21 monthly report (PDF/Excel, for tax filing)
- [x] BPJS report (PDF/Excel, for contribution submission)
- [x] Payslip PDF generation (per employee)
- [x] Bukti Potong PPh 21 PDF (1721-A1, for employee annual tax filing)
- [x] Export dropdown in payroll detail UI
- [x] Bukti Potong page with year filter and employee list
- [x] User manual update (64-payroll-processing.md)

### 3.7 User Management & Role-Based Access Control ✅
- [x] Role enum (ADMIN, OWNER, ACCOUNTANT, STAFF, AUDITOR, EMPLOYEE)
- [x] Permission constants with role-permission mapping
- [x] UserRole junction entity (many-to-many relationship)
- [x] User entity enhancements (roles relationship, addRole, setRoles methods)
- [x] UserDetailsServiceImpl loads roles and builds authorities
- [x] UserService and UserController
- [x] User CRUD UI (list, form, detail, change-password)
- [x] Role assignment UI (multi-select checkboxes)
- [x] @PreAuthorize annotations on controllers
- [x] Menu visibility based on permissions (sec:authorize)
- [x] Functional tests (9 Playwright tests)
- [x] User manual (70-kelola-pengguna.md)

### 3.8 Employee Self-Service ✅
- [x] Link Employee entity to User entity (id_user column)
- [x] SelfServiceController with @PreAuthorize
- [x] My Payslips page (list with year filter, download PDF)
- [x] My Bukti Potong PPh 21 page (download 1721-A1 PDF)
- [x] My Profile page (view and edit limited fields)
- [x] Sidebar menu for self-service (sec:authorize)
- [x] Employee form user dropdown
- [x] Functional tests (7 Playwright tests)
- [x] User manual (71-layanan-mandiri.md)

**Permission model:** Additive permissions (not role switching).
- User can have multiple roles (e.g., ACCOUNTANT + EMPLOYEE)
- Permissions are combined from all assigned roles
- UI shows all accessible features based on combined permissions

**Implementation complexity comparison (why additive, not role switching):**

| Aspect | Additive | Role Switching |
|--------|----------|----------------|
| Security config | Standard Spring Security | Custom service |
| Session state | None | Track active role |
| UI components | None extra | Role switcher dropdown |
| After switch | N/A | Refresh/redirect needed |
| Lines of code | ~50 | ~150-200 |

Additive is ~3x simpler. Role switching only needed for strict audit trails or compliance requirements.

### 3.9 Full Data Export/Import ✅

**Goal:** Complete data portability for semi-production workflow (export → reset DB → import).

#### Implementation
- [x] DataExportService: exports 33 entities with numbered CSV filenames
- [x] DataImportService: parses CSVs in filename order, creates entities
- [x] Map pre-load strategy for O(1) reference lookups
- [x] Passwords preserved (bcrypt hashes imported as-is)
- [x] References resolved via natural keys (new UUIDs generated on import)
- [x] Import controller and UI (`/settings/import`)
- [x] Functional test: `FullDataExportImportTest.java` (round-trip verification)

**Phase 3 Deliverable:** ✅ Complete payroll system with tax compliance, role-based access control, and employee self-service.

---

## Phase 4: Fixed Assets ✅

**Goal:** Fixed asset tracking with depreciation

**Implementation note:** Follow payroll pattern for journal posting:
- Route through Transaction → JournalTemplate → JournalEntry (not direct journal creation)
- Use extended FormulaContext with domain-specific variables (e.g., `assetCost`, `accumulatedDepreciation`, `disposalProceeds`, `gainLoss`)
- Create system templates for: asset purchase, depreciation entry, asset disposal
- Keep asset-specific logic in AssetService, core accounting remains generic

### 4.1 Fixed Asset Register ✅
- [x] Fixed asset entity (FixedAsset with status, depreciation tracking fields)
- [x] Asset categories (AssetCategory with default depreciation settings and account mappings)
- [x] Asset CRUD UI (list, form, detail pages)
- [x] Purchase recording (via asset creation with purchase date and cost)
- [x] Asset search and filtering

### 4.2 Depreciation ✅
- [x] Straight-line calculation
- [x] Declining balance calculation
- [x] Depreciation schedule (DepreciationEntry entity)
- [x] Monthly depreciation batch job (merged into MonthlyJournalScheduler)
- [x] Auto-journal via templates (using account_hint for dynamic account mapping)
- [x] Generate and post depreciation entries
- [x] Prevent duplicate entries for same period

### 4.3 Asset Disposal ✅
- [x] Disposal workflow (sell, write-off, transfer)
- [x] Gain/loss calculation
- [x] Disposal journal entry (via journal template with dynamic accounts)
- [x] Asset status tracking (ACTIVE, FULLY_DEPRECIATED, DISPOSED)

**Phase 4 Deliverable:** Fixed asset management with depreciation and disposal.

### 4.4 Depreciation Report (Tax Attachment) ✅
- [x] Laporan Penyusutan for SPT Tahunan (Lampiran Khusus 1A format)
- [x] Columns: Nama Aset, Tgl Perolehan, Harga Perolehan, Masa Manfaat, Metode, Penyusutan Tahun Ini, Akum. Penyusutan, Nilai Buku
- [x] Filter by year
- [x] Print view
- [x] Functional tests
- [x] PDF/Excel export

### 4.5 Fiscal Year Closing ✅
- [x] Year-end closing service (auto-generate closing entries)
- [x] Close Revenue accounts (4.x) to Laba Berjalan (3.2.02)
- [x] Close Expense accounts (5.x) to Laba Berjalan (3.2.02)
- [x] Close Laba Berjalan to Laba Ditahan (3.2.01)
- [x] Prevent duplicate closing for same year
- [x] Closing entries UI (preview, execute, reverse if needed)
- [x] Functional tests

---

## Phase 5: Inventory & Production ✅

**Goal:** Inventory tracking and simple production costing for home industries and retail

**Target users:** Home industries (cake shops, bakeries), small retail, simple manufacturing

**Implementation note:** Follow payroll pattern for journal posting:
- Route through Transaction → JournalTemplate → JournalEntry (not direct journal creation)
- Use extended FormulaContext with inventory variables (e.g., `quantity`, `unitCost`, `totalCost`, `cogsAmount`)
- Create system templates for: inventory purchase, inventory adjustment, sales with COGS, production transfer
- Keep inventory-specific logic in InventoryService, core accounting remains generic

### 5.1 Product Master ✅
- [x] Product entity (code, name, unit, category, costing_method)
- [x] ProductCategory entity (code, name, parent)
- [x] CostingMethod enum (FIFO, WEIGHTED_AVERAGE)
- [x] Product CRUD UI (list with search/category filter, form, detail)
- [x] Category CRUD UI
- [x] Link to inventory accounts (raw material, finished goods)
- [x] Functional tests (22 Playwright tests)
- [x] User manual (75-kelola-produk.md)

### 5.2 Inventory Transactions ✅
- [x] InventoryTransaction entity (product, quantity, unit_cost, type, reference)
- [x] InventoryTransactionType enum (PURCHASE, SALE, ADJUSTMENT_IN/OUT, PRODUCTION_IN/OUT, TRANSFER_IN/OUT)
- [x] InventoryBalance entity (product, quantity, total_cost, average_cost)
- [x] InventoryFifoLayer entity (for FIFO cost tracking)
- [x] InventoryService (record transaction, update balance, calculate cost)
- [x] FIFO cost calculation
- [x] Weighted average cost calculation
- [x] Purchase recording UI (product, date, quantity, unit cost)
- [x] Sale recording UI (product, date, quantity → auto-calculate COGS)
- [x] Adjustment UI (stock opname corrections)
- [x] Stock list with low stock alerts
- [x] Stock card per product (transaction history, FIFO layers)
- [x] Transaction list with filters
- [x] Functional tests (13 Playwright tests)
- [x] Auto-journal generation via templates (3 Playwright tests)
- [x] User manual (76-transaksi-inventori.md)

### 5.3 Inventory Reports ✅
- [x] Stock balance report (current quantity and value per product)
- [x] Stock movement report (in/out per period)
- [x] Inventory valuation report (FIFO layers or weighted average)
- [x] Low stock alert (configurable threshold per product)
- [x] PDF/Excel export
- [x] Functional tests (22 tests)
- [x] User manual (77-kartu-stok.md)

### 5.4 Simple Production (BOM) ✅
- [x] BillOfMaterial entity (finished product, components with quantities)
- [x] BillOfMaterialLine entity (component, quantity, notes)
- [x] BOM CRUD UI (list, form, detail pages)
- [x] ProductionOrder entity (BOM, quantity, status, cost tracking)
- [x] ProductionOrderStatus enum (DRAFT, IN_PROGRESS, COMPLETED, CANCELLED)
- [x] Production workflow:
  - [x] Create order from BOM
  - [x] Start production (status → IN_PROGRESS)
  - [x] Complete production → deduct components, add finished goods
  - [x] Cost accumulation (weighted average from components)
- [x] Production order UI (list, form, detail with workflow actions)
- [x] Cost calculation on completion (total cost, unit cost)
- [x] Inventory integration (PRODUCTION_IN/PRODUCTION_OUT transaction types)
- [x] Coffee shop industry seed pack (17 CSV files: COA, products, BOMs, production orders, inventory)
- [x] DataImportService support for manufacturing data (products, categories, BOMs, production orders, transactions)
- [x] Test data initializer (CoffeeTestDataInitializer)
- [x] Functional tests (44 Playwright tests: MfgBomTest, MfgProductionTest, MfgCostingTest, MfgMaterialsTest, MfgCsvDrivenTest, MfgTransactionExecutionTest)
- [x] Test pattern: All tests using data-testid locators (zero text/CSS/positional locators)
- [x] User manual (80-produksi-bom.md)

### 5.5 Integration with Sales ✅
- [x] Link Transaction to InventoryTransaction (already exists in InventoryTransaction.transaction field)
- [x] Auto-COGS on sales transaction posting (InventoryService.createJournalEntry)
- [x] Sales template with COGS variables (`cogsAmount`, `revenueAmount`) in V004 seed data
- [x] Margin calculation per sale (transaction detail shows revenue, margin amount, margin %)
- [x] Product profitability report (inventory/reports/profitability)
- [x] Functional tests (10 Playwright tests: SalesIntegrationTest)
- [x] User manual (79-analisis-profitabilitas-produk.md)

**Phase 5 Deliverable:** Inventory management with FIFO/weighted average costing, simple BOM-based production, automatic COGS calculation, coffee shop industry seed pack with complete manufacturing data, and 44 manufacturing functional tests using robust data-testid locators.

---

## Phase 6: Security Hardening ✅

**Goal:** Address critical and high-severity security vulnerabilities identified in the security audit to make the application production-ready for hosting client data.

**Standards:** OWASP Top 10 (2021), NIST CSF

### 6.1 Critical Fixes (P0) ✅
- [x] Remove hardcoded database credentials from compose.yml
- [x] Implement password complexity requirements (12+ chars, uppercase, lowercase, number, special)
- [x] Add security headers to SecurityConfig (CSP, HSTS, X-Frame-Options, X-Content-Type-Options)
- [x] Fix DOM-based XSS in templates/form.html (replace innerHTML with textContent/DOMPurify)
- [x] Fix SQL injection in DataImportService (whitelist validation for table names)

### 6.2 Data at Rest Encryption (P1) ✅

**Goal:** Protect sensitive data stored in database, files, and backups.

- [x] EncryptedStringConverter JPA attribute converter (AES-256-GCM)
- [x] Key management integration (environment variable or external KMS)
- [x] Encrypt PII fields:
  - [x] Employee.bankAccountNumber
  - [x] Employee.npwp (Tax ID)
  - [x] Employee.nikKtp (National ID)
  - [x] Employee.bpjsKesehatanNumber
  - [x] Employee.bpjsKetenagakerjaanNumber
  - [x] CompanyBankAccount.accountNumber
- [x] Data migration for existing records (encrypt on startup or batch job) - handled transparently via converter
- [x] Document storage encryption (encrypt files before saving to disk)
  - [x] FileEncryptionService with AES-256-GCM
  - [x] DocumentStorageService integration (encrypt on store, decrypt on load)
  - [x] Magic header (ENCF) for encrypted file detection
  - [x] Backward compatibility with unencrypted files
- [x] Database connection with SSL
  - [x] Development: compose.yml with PostgreSQL SSL enabled
  - [x] Production: Ansible role enables SSL in postgresql.conf
  - [x] Application: sslmode=require (both dev and prod)
- [x] Functional tests for encryption/decryption
  - [x] FileEncryptionServiceTest (25 unit tests)
  - [x] DocumentEncryptionTest (8 Playwright functional tests)

### 6.2.5 Data in Transit Protection (P1) ✅

**Goal:** Encrypt all data transmitted over networks.

- [x] TLS 1.2/1.3 configuration via Nginx reverse proxy (Ansible)
  - [x] Let's Encrypt certificate via certbot (`setup-ssl.yml`, `nginx/tasks/main.yml`)
  - [x] TLS 1.2 and 1.3 only (`ssl_protocols TLSv1.2 TLSv1.3`)
  - [x] Modern cipher suites (ECDHE, AES-GCM, CHACHA20-POLY1305)
  - [x] Auto-renewal via cron job
- [x] PostgreSQL SSL connection
  - [x] Configure `sslmode=require` in JDBC URL (Ansible template)
  - [x] Enable SSL in PostgreSQL server (Ansible role)
- [x] HSTS header (Strict-Transport-Security)
  - [x] `max-age=63072000` (2 years) in nginx-site-ssl.conf.j2
- [x] HTTP to HTTPS redirect (nginx reverse proxy)
  - [x] `return 301 https://$server_name$request_uri`
- [x] Secure cookie flags (`secure`, `httpOnly`, `sameSite=strict`)
- [x] Backup transfer encryption
  - [x] AES-256 GPG encryption before upload (backup-b2.sh.j2, backup-gdrive.sh.j2)
  - [x] HTTPS transfer via rclone to B2/GDrive
- [x] External API connections use TLS
  - [x] Telegram API: `api.telegram.org` (HTTPS enforced by Telegram)
  - [x] Google Cloud Vision: Google client library uses HTTPS

### 6.3 Authentication Hardening (P1) ✅
- [x] Implement account lockout after 5 failed login attempts (30-minute lockout)
- [x] Add failed login attempt logging with IP address
- [x] Implement rate limiting on /login endpoint (RateLimitFilter with Bucket4j-style algorithm)
- [x] Configure session timeout (15 minutes)
- [x] Add session cookie security flags (secure, httpOnly, sameSite=strict)
- [x] Enforce Telegram webhook authentication (fail if secret not configured)
- [x] Remove password hashes from DataExportService exports

### 6.4 Input Validation & Output Encoding (P1) ✅
- [x] Add magic byte validation for file uploads (not just Content-Type)
- [x] Implement RFC 6266 encoding for Content-Disposition headers
- [x] Improve ZIP slip validation (normalize paths, check resolved path) - already in DataImportService
- [x] Add @Pattern validation for sensitive fields (NPWP, NIK format) - already in Employee entity
- [x] Sanitize user input in log statements (prevent log injection)
- [x] Generic error messages to clients (no stack traces, no path exposure) - GlobalExceptionHandler configured
- [x] Custom 403 error page (`templates/error/403.html`) with `#access-denied-page` ID for testing
- [x] AccessDeniedHandler for proper 403 responses (browser → HTML page, API → JSON)
- [x] MvcExceptionHandler for controller-level AccessDeniedException handling
- [x] NoResourceFoundException handler (404 with debug-level logging)

### 6.5 Comprehensive Audit Logging (P2) ✅
- [x] AuditEventType enum (LOGIN_SUCCESS, LOGIN_FAILURE, LOGOUT, USER_CREATE, USER_UPDATE, etc.)
- [x] SecurityAuditLog entity (eventType, username, ipAddress, userAgent, details, timestamp)
- [x] Log all authentication events (login, logout, failed attempts)
- [x] Log user management operations (create, update, delete, role changes) - UserController
- [x] Log sensitive data access (payroll exports, tax reports, backups) - DataExportService
- [x] Log document operations (upload, download, delete) - DocumentController
- [x] Log settings modifications
- [x] Mask sensitive fields in audit log details (passwords, bank accounts)
- [x] Security audit log viewer UI (/settings/audit-logs)
- [x] Audit log retention policy (2 years) - logrotate config in Ansible

### 6.6 Data Protection & Data in Use (P2) ✅

**Goal:** Protect data during processing and in application memory.

**Data Masking:**
- [x] Implement data masking for sensitive fields in views (show last 4 digits)
  - Created DataMaskingUtil.java with maskNik, maskNpwp, maskPhone, maskBankAccount, maskBpjs, maskEmail
  - Created ThymeleafConfig.java with custom #mask dialect for templates
  - Applied masking in employees/detail.html and users/detail.html
- [x] ~~Add @SecureField annotation for role-based field visibility~~ - Won't implement: Thymeleaf sec:authorize already provides role-based field visibility in templates
- [x] ~~Mask sensitive fields in API responses~~ - Won't implement: No REST API - MVC app uses Thymeleaf templates with sec:authorize and #mask dialect

**Backup Security:**
- [x] Encrypt backup exports - Handled by Ansible infrastructure (GPG + AES256 for B2/GDrive)
- [x] Implement backup file integrity verification (SHA-256 checksum) - backup.sh/restore.sh
- [x] Add confirmation dialog for destructive operations (data import truncate) - import/index.html onclick confirm

**Data in Use (Memory Protection):**
- [x] Disable heap dumps in production (`-XX:+DisableAttachMechanism`) - systemd service
- [x] Configure JVM with ZGC for better memory management (`-XX:+UseZGC`) - systemd service
- [x] Prevent core dumps (`LimitCORE=0`) - systemd service
- [x] Restrict filesystem access (`ProtectSystem=strict`, `PrivateTmp=true`) - systemd service
- [x] Drop capabilities (`NoNewPrivileges=true`, `CapabilityBoundingSet=`) - systemd service
- [x] Review in-memory caching - No sensitive data cached (only IP/username for rate limiting)
- ~Secure temporary file handling (wipe byte arrays)~ - Won't implement: Low ROI, requires root access to exploit, JVM hardening already blocks debugger attach
- ~Clear ByteArrayOutputStream after export~ - Won't implement: Low ROI, JIT may optimize away the wipe, export data is already encrypted at rest
- ~Use char[] instead of String for passwords~ - Won't implement: Spring Security uses String internally, would require extensive refactoring with minimal benefit

### 6.7 API Security (P2) ✅
- [x] Implement rate limiting on all /api/** endpoints - RateLimitFilter covers all paths
- [x] Add API request logging (endpoint, user, latency, status) - nginx api_log format
- [x] Configure CORS policy (explicit allowed origins) - SecurityConfig denies cross-origin by default
- [x] API error responses without sensitive information - GlobalExceptionHandler
- [x] Telegram webhook uses header-based authentication (`X-Telegram-Bot-Api-Secret-Token`)

### 6.8 GDPR/UU PDP Compliance (P2) ✅

**Note:** UU PDP (Law No. 27/2022) is Indonesian data protection law, similar to GDPR.

- [x] True data deletion capability (not just soft delete) for data subject requests - DataSubjectService.deleteUser()
- [x] Data Subject Access Request (DSAR) workflow
  - [x] Employee self-service: export all personal data (JSON/PDF) - DataSubjectService.exportPersonalData()
  - [x] Admin: process deletion requests with audit trail - DataSubjectService.anonymizeEmployee() with audit logging
- N/A ~~Consent management~~ — app only stores employee data entered by the company, not user-submitted PII
- [x] Privacy notice display in application (/privacy-policy) - SettingsController /settings/privacy + privacy.html template
- [x] Data subject rights UI for administrators
  - [x] DataSubjectController with ADMIN-only access
  - [x] Data subjects list page (/settings/data-subjects)
  - [x] Data subject detail page with retention status
  - [x] DSAR export page (masked sensitive data)
  - [x] Anonymization confirmation page with reason and audit trail
  - [x] DataSubjectRightsTest (16 Playwright functional tests)
- N/A ~~Data breach response procedures~~ — minimal PII scope, existing DSAR and anonymization covers obligations
- [x] Data retention enforcement
  - [x] Retention status check - DataSubjectService.getRetentionStatus() (10-year retention per UU KUP Art. 28)
  - N/A ~~Automated purge job~~ — 10-year tax retention means no data expires in foreseeable future
  - N/A ~~Pre-deletion notification~~
- N/A ~~Records of Processing Activities (ROPA)~~ — minimal PII scope, not a high-volume data processor
- N/A ~~Data Protection Impact Assessment (DPIA)~~ — no high-risk processing of personal data

### 6.9 Automated Security Testing (DevSecOps) (P2)

**Goal:** Implement shift-left security with automated testing in CI/CD pipeline.

**Reference:** OWASP DevSecOps Guideline, NIST SP 800-218 (SSDF)

#### 6.9.1 Static Application Security Testing (SAST)
- [x] CodeQL analysis (GitHub native, Java support)
  - [x] Add `.github/workflows/codeql.yml`
  - [x] Configure security queries for Java
  - [x] Enable SARIF upload to GitHub Security tab
- [x] Semgrep rules for Spring Security patterns
  - [x] Custom rules for authentication bypass (`.semgrep/spring-security-auth-bypass.yml` - 6 rules)
  - [x] SQL injection detection (`.semgrep/sql-injection.yml` - 4 rules)
  - [x] XSS pattern detection (`.semgrep/xss-prevention.yml` - 4 rules)
  - [x] CI integration - added to `.github/workflows/codeql.yml`
  - [x] Documentation - `.semgrep/README.md`
- [x] SonarCloud integration (https://sonarcloud.io/project/overview?id=artivisi_aplikasi-akunting)
  - [x] Security hotspot analysis
  - [x] Code smell detection
  - [x] Quality gate configured
  - [x] sonar-project.properties configured
- [x] SpotBugs with FindSecBugs (Java 25 support since Oct 2024) - **COMPLETE**
  - [x] Updated to SpotBugs 4.9.8.2 with BCEL 6.11.0 and ASM 9.8
  - [x] FindSecBugs 1.13.0 for security-specific detectors
  - [x] CI integration - `.github/workflows/security.yml`
  - [x] Max effort, Medium threshold configured in pom.xml
  - [x] **Security audit completed (Dec 2025)**
    - Initial scan: 164 issues (33 real vulnerabilities + 140 false positives)
    - **Critical security fixes (17 issues):**
      - CRLF_INJECTION_LOGS (12) - Triple-layer defense via LogSanitizer + Logback pattern
      - PATH_TRAVERSAL_IN (2) - Path validation with startsWith() checks
      - SPEL_INJECTION (2) - Disabled SpEL evaluation in unsafe contexts
      - URLCONNECTION_SSRF_FD (1) - URL validation for external requests
    - **Code quality fixes (16 issues):**
      - DM_DEFAULT_ENCODING (3) - Explicit UTF-8 charset in encryption services
      - VA_FORMAT_STRING_USES_NEWLINE (5) - Platform-independent %n format
      - DLS_DEAD_LOCAL_STORE (2) - Removed unused variables
      - SF_SWITCH_NO_DEFAULT (2) - Added default cases with logging
      - URF_UNREAD_FIELD (1) - Removed unused field
      - NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE (3) - Null checks with exceptions
    - **Refactoring (2 issues):**
      - ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD (2) - Migrated to instance fields (modern pattern)
    - **False positives documented (140 issues):**
      - EI_EXPOSE_REP/EI_EXPOSE_REP2 - Spring DI constructor injection (excluded via spotbugs-exclude.xml)
    - **Final result: 0 issues** ✅
    - Documentation: `spotbugs-exclude.xml` with comprehensive justifications
    - Commits: 8 security/quality commits (5445c97, 9cc8405, 960c2eb, 84a1418, a6da755, 8eff62d, b62d065, etc.)

#### 6.9.2 Software Composition Analysis (SCA)
- [x] OWASP Dependency-Check
  - [x] Configure fail threshold (CVSS ≥ 7) - `failBuildOnCVSS` in pom.xml
  - [x] NVD API key for faster scans - `nvdApiKey` config
  - [x] CI integration - `.github/workflows/security.yml`
- N/A ~~Dependency license scanning~~ — all dependencies are Apache/MIT/BSD licensed, verified manually
- [x] SBOM generation (Software Bill of Materials)
  - [x] CycloneDX maven plugin (v2.9.1) - generates sbom.json and sbom.xml on package
  - [x] Includes compile, provided, runtime scopes
  - [x] Publish SBOM with releases (GitHub Actions) - added to `.github/workflows/release.yml`

#### 6.9.3 Secret Detection
- [x] GitLeaks configuration
  - [x] `.gitleaks.toml` configuration
  - [x] Custom patterns for Indonesian tax IDs, bank accounts
- [x] TruffleHog in CI pipeline
  - [x] Scan git history for leaked secrets
  - [x] Block PR if secrets detected (`continue-on-error: false` in `.github/workflows/codeql.yml`)
- N/A ~~GitHub secret scanning~~ — GitLeaks and TruffleHog already cover this in CI

#### 6.9.4 Dynamic Application Security Testing (DAST)
- [x] Comprehensive ZAP DAST (`ZapDastTest.java`)
  - [x] ZAP as Testcontainer with `host.testcontainers.internal` networking
  - [x] 4 test scenarios: baseline, authenticated, API, privilege escalation
  - [x] Passive scanning (header analysis, information disclosure)
  - [x] Active scanning (SQLi, XSS, CSRF, path traversal, command injection)
  - [x] **Graybox endpoint enumeration** (replaced spider with explicit controller mappings)
    - 22 list pages, 15 form endpoints, 37 parameterized endpoints
    - 21 API endpoints, 16 report pages (111 total endpoints)
  - [x] Multi-role testing (admin, accountant roles)
  - [x] Severity thresholds: 0 HIGH, 0 MEDIUM (strict)
  - [x] Quick mode (`-Ddast.quick=true`) for CI, full mode for weekly
  - [x] False positive filtering for known limitations (CSP on Tomcat-rejected URLs)
- [x] CSP Header improvements
  - [x] CspNonceFilter writes headers directly (not relying on Spring Security)
  - [x] Filter runs for ALL dispatcher types (REQUEST, ERROR, FORWARD, INCLUDE, ASYNC)
  - [x] CustomErrorController ensures CSP on /error endpoint
  - [x] Known limitation: Tomcat blocks malformed URLs before servlet filter chain
- [x] GitHub Actions integration
  - [x] `.github/workflows/dast.yml` - weekly schedule
  - [x] HTML reports uploaded as artifacts

#### 6.9.5 Container Security
- N/A ~~Container security~~ — app runs as systemd service on bare metal, no Docker images

#### 6.9.6 Infrastructure as Code (IaC) Security
- N/A ~~Checkov for Pulumi/Terraform~~ — infrastructure managed via Ansible, not IaC scanning target
- [x] Ansible-lint security rules
  - [x] `.ansible-lint` configuration file
  - [x] no-log-password rule enabled
  - [x] Strict mode enabled

#### 6.9.7 API Security Testing
- [x] OWASP ZAP API scan (`ZapDastTest.shouldPassApiSecurityScan`)
  - [x] Tests 6 API endpoints (`/api/recent`, `/api/search`, `/api/trial-balance`, etc.)
  - [x] Passive + active scanning on `/api/*` context
  - [x] Authenticated API testing
- N/A ~~OpenAPI spec-based scanning~~ — ZAP graybox enumeration already covers all 111 endpoints
- N/A ~~Postman/Newman security tests~~ — covered by Playwright SecurityRegressionTest (auth, IDOR, input validation, rate limiting)
- N/A ~~API fuzzing~~ — low value for internal business app with authenticated endpoints; ZAP active scanning covers injection vectors

#### 6.9.8 Security Regression Tests
- [x] Playwright security test suite (`SecurityRegressionTest.java`)
  - [x] Authentication flow tests
  - [x] Session management tests
  - [x] CSRF token validation
  - [x] XSS payload injection tests
  - [x] SQL injection attempts (blocked)
- [x] JUnit security unit tests
  - [x] Password complexity validation (`PasswordValidatorTest.java` - 18 tests)
  - [x] Account lockout logic (`LoginAttemptServiceTest.java` - 21 tests)
  - [x] Data masking (`DataMaskingUtilTest.java` - 25 tests)
  - [x] Input sanitization for logs (`LogSanitizerTest.java` - 30 tests)
  - [x] Encryption/decryption (`FileEncryptionServiceTest.java` - 25 tests)

#### 6.9.9 Automated Penetration Tests (from checklist)

**Goal:** Automate tests from `docs/09-penetration-testing-checklist.md` via Playwright/JUnit/ZAP.

**1. Authentication Testing (Playwright)**
- [x] Valid/invalid credentials login (`SecurityRegressionTest.shouldRejectInvalidCredentials`)
- [x] SQL injection in login - ZAP passive scan covers this
- [x] XSS in username (`SecurityRegressionTest.XssPreventionTests`)
- [x] Account lockout after 5 attempts (`SecurityRegressionTest.shouldProtectAgainstBruteForce`)
- [x] Session invalidated on logout (`SecurityRegressionTest.shouldInvalidateSessionOnLogout`)
- [x] Session fixation protection (`SecurityRegressionTest.shouldRegenerateSessionAfterLogin`)
- [x] Session cookie flags verification (HttpOnly, SameSite) - `SecurityRegressionTest.shouldUseSecureSessionCookies`
- [x] Session timeout after 15 min idle (`application.properties`: `server.servlet.session.timeout=15m`)

**2. Authorization Testing (Playwright)**
- [x] RBAC restrictions (`SecurityRegressionTest.AuthorizationTests`)
  - [x] Admin access to user management - PASS
  - [x] Staff accessing /users → 403 (FIXED - AccessDeniedHandler)
  - [x] Employee accessing /payroll → 403 (FIXED - AccessDeniedHandler)
  - [x] Auditor accessing /transactions/new → 403 (FIXED - AccessDeniedHandler)
  - [x] Staff seeing POST button → hidden (FIXED - sec:authorize in template)
  - [x] Employee accessing /dashboard → 403 (FIXED - AccessDeniedHandler)
- [x] IDOR tests (`SecurityRegressionTest.AuthorizationTests`)
  - [x] Employee accessing other employee's profile → 403 (FIXED - ownership check)
  - [x] Employee accessing other user's payslips via URL param → userId ignored (secure by design)
  - [x] Staff accessing transaction edit page → 403 (FIXED - @PreAuthorize)

**3. Input Validation (Playwright + ZAP)**
- [x] SQL injection in search (`SecurityRegressionTest.shouldRejectSqlInjection`)
- [x] XSS payloads escaped (`SecurityRegressionTest.XssPreventionTests` - 7 payloads)
- [x] File upload: magic byte validation (`FileValidationServiceTest.java` - 26 tests)
  - [x] Reject .exe disguised as .pdf
  - [x] Reject PHP/HTML disguised as image
  - [x] Validate PDF, JPEG, PNG, GIF, XLSX, DOCX signatures
- [x] File upload: reject path traversal filenames (`SecurityRegressionTest.shouldRejectPathTraversalFilename`)
- [x] File upload: reject executable files (`SecurityRegressionTest.shouldRejectExecutableFile`)
- [x] File upload: reject content-type spoofing (`SecurityRegressionTest.shouldRejectContentTypeSpoofing`)
- [x] File upload: reject empty filename (`SecurityRegressionTest.shouldRejectEmptyFilename`)
- [x] File upload: accept valid PDF (`SecurityRegressionTest.shouldAcceptValidPdfUpload`)
- [x] File upload: reject oversized files (>10MB) (`SecurityRegressionTest.shouldRejectOversizedFile`)
- [x] Template injection in user input (`SecurityRegressionTest.shouldEscapeTemplateInjection` - 10 SSTI payloads)

**4. Security Headers (ZAP + Playwright)**
- [x] ZAP passive scan checks all headers (`ZapDastTest`)
- [x] Playwright header assertions (`SecurityRegressionTest.SecurityHeaderTests`)
  - [x] Assert X-Content-Type-Options = nosniff
  - [x] Assert X-Frame-Options = DENY or SAMEORIGIN
  - [x] Assert Content-Security-Policy present with default-src
  - [x] Assert Referrer-Policy present
  - [x] Assert Strict-Transport-Security present (`nginx-site-ssl.conf.j2:31`, `SecurityConfig.java:80` - HTTPS only)

**5. CSRF Protection (Playwright)**
- [x] CSRF token present in forms (`SecurityRegressionTest.shouldIncludeCsrfTokenInForms`)
- [x] CSRF header for HTMX (`SecurityRegressionTest.shouldConfigureCsrfForHtmx`)
- [x] POST without CSRF token → 403 (`SecurityRegressionTest.shouldRejectPostWithoutCsrfToken`)

**6. Data Protection (JUnit + Playwright)**
- [x] PII masking utility (`DataMaskingUtilTest.java` - 25 tests)
- [x] File encryption (`FileEncryptionServiceTest.java` - 25 tests)
- [x] PII masked in page source (`SecurityRegressionTest.shouldMaskBankAccountInEmployeeList`, `shouldMaskNpwpNikInEmployeeDetail`)
- [x] Sensitive data not in URL parameters (`SecurityRegressionTest.shouldNotHaveSensitiveDataInUrl`)
- [x] Sensitive data not in error messages (`SecurityRegressionTest.shouldNotExposeStackTraces`, `shouldNotExposeDatabaseQueryInError`, `shouldNotExposeFilePathsInError`)

**7. Business Logic (Playwright)**
- [x] Modify posted journal entry → rejected (`SecurityRegressionTest.shouldNotAllowEditingPostedTransaction`)
- [x] Delete posted transaction → only void allowed (`SecurityRegressionTest.shouldNotDisplayDeleteButtonForPostedTransaction`)
- [x] Modify voided transactions → rejected (`SecurityRegressionTest.shouldNotAllowEditingVoidedTransaction`, `shouldNotAllowVoidingVoidedTransaction`)
- [x] Negative amounts validation (`SecurityRegressionTest.shouldRejectNegativeAmounts`)
- [x] Journal entry balance validation (`SecurityRegressionTest.shouldRejectUnbalancedJournalEntry`)

**8. Error Handling (Playwright)**
- [x] No stack traces in error pages (`SecurityRegressionTest.shouldNotExposeStackTraces`)
- [x] Generic error messages (`SecurityRegressionTest.shouldShowGenericErrorMessages`)
- [x] Database error doesn't expose query (`SecurityRegressionTest.shouldNotExposeDatabaseQueryInError`)
- [x] Path traversal error doesn't show paths (`SecurityRegressionTest.shouldNotExposeFilePathsInError`)

**9. Rate Limiting (Playwright)**
- [x] Rapid login attempts → rate limited (`SecurityRegressionTest.shouldRateLimitRapidLoginAttempts`)
- [x] Bulk requests → rate limited (`SecurityRegressionTest.shouldRateLimitBulkApiRequests`)

**10. Audit Logging (JUnit + Playwright)**
- [x] Log sanitization (`LogSanitizerTest.java` - 30 tests)
- [x] Failed logins logged with IP (`SecurityRegressionTest.shouldLogFailedLoginWithIp`, `SecurityAuditService`)
- [x] Successful logins logged (`SecurityRegressionTest.shouldLogSuccessfulLogin`, `SecurityAuditService`)
- [x] Data exports logged (`SecurityRegressionTest.shouldLogDataExportOperations`)

### 6.10 Security Documentation & Policies (P3)
- [x] Password complexity validation (PasswordValidator with 12+ chars, upper/lower/digit/special)
- [x] Account lockout (LoginAttemptService with 5 attempts, 30 min lockout)
- [x] Functional tests for password change (UserManagementTest)
- [x] Functional tests for brute force protection (SecurityRegressionTest.shouldProtectAgainstBruteForce)
- [x] Functional tests for field-level encryption (FileEncryptionServiceTest, DocumentEncryptionTest)
- [x] Functional tests for security headers (SecurityRegressionTest.SecurityHeaderTests)
- [x] GDPR/UU PDP data subject rights (DataSubjectService: export, anonymize, delete, retention check)
- [x] Integration tests for data subject rights (DataSubjectServiceTest: 11 tests covering Art. 15 & 17)
- [x] Penetration testing checklist (manual verification)
  - [x] docs/09-penetration-testing-checklist.md - comprehensive manual testing guide
  - [x] Authentication, authorization, input validation sections
  - [x] OWASP-aligned test cases with expected results
- [x] Update user manual with security best practices
  - [x] docs/user-manual/70-kelola-pengguna.md (password complexity, lockout, audit log)
  - [x] docs/user-manual/80-kebijakan-data.md (data subject rights, encryption)
  - [x] docs/user-manual/82-keamanan.md (comprehensive security documentation)
- [x] Create SECURITY.md with vulnerability reporting process
  - [x] Vulnerability disclosure policy with response timeframes
  - [x] Severity classification (CVSS-based)
  - [x] Safe harbor statement for security researchers
  - [x] Summary of security controls implemented
- N/A ~~PCI-DSS security patch procedures~~ — app does bookkeeping, does not store/process/transmit cardholder data
- [x] Privacy policy page (/privacy) with UU PDP and GDPR compliance

**Phase 6 Deliverable:** ✅ Complete — Production-ready security posture with encrypted PII, strong authentication, comprehensive audit logging, and compliance with OWASP Top 10 and UU PDP requirements.

---

## Phase 7: API Foundation ✅

**Goal:** Expose REST API for external integrations, mobile apps, and domain-specific applications

**Note:** Most API infrastructure was built organically during Phases 6/9/Analysis. This phase tracks what was already done and the remaining items.

### 7.1 API Core ✅

**Authentication (OAuth 2.0 Device Authorization, RFC 8628):**
- [x] DeviceCode entity (user_code, device_code, 15-min expiry, status workflow)
- [x] DeviceToken entity (hashed token, scopes, 30-day expiry, revocation support)
- [x] DeviceAuthService (generate codes, validate tokens, revoke)
- [x] BearerTokenAuthenticationFilter (Bearer token validation, scope extraction)
- [x] DeviceAuthApiController (`POST /api/device/code`, `POST /api/device/token`)
- [x] DeviceAuthorizationController (browser-based user authorization page)

**API Controllers:**
- [x] DraftTransactionApiController (`/api/drafts`) — from-receipt, from-text, approve, reject
- [x] TransactionApiController (`/api/transactions`) — create, post, bulk-post
- [x] TemplateApiController (`/api/templates`) — CRUD
- [x] FinancialAnalysisApiController (`/api/analysis`) — reports, snapshots, trial-balance, balance-sheet, income-statement, cash-flow, tax-summary, receivables, payables, accounts, drafts, transactions (paginated)
- [x] BankReconciliationApiController (`/api/bank-reconciliation`) — full recon workflow
- [x] DataImportApiController (`/api/data-import`) — bulk ZIP import

**Infrastructure:**
- [x] ApiExceptionHandler — structured JSON error responses (error code, message, timestamp, fieldErrors)
- [x] SecurityAuditService — API call audit logging (async)
- [x] 7 OAuth scopes: `drafts:create`, `drafts:approve`, `drafts:read`, `analysis:read`, `analysis:write`, `transactions:post`, `data:import`
- [x] `@PreAuthorize("hasAuthority('SCOPE_...')")` on all API endpoints
- [x] Static API capabilities descriptor (`/api/capabilities.json`)
- N/A ~~Idempotency key on Transaction~~ — low ROI, extensive schema/logic changes for unlikely duplicate scenario
- N/A ~~Separate ApiKey entity~~ — DeviceToken already serves this purpose with scopes and revocation
- N/A ~~Separate AccountApiController~~ — accounts exposed via `/api/analysis/accounts` and `/api/drafts/accounts`
- N/A ~~Separate ReportApiController~~ — reports exposed via `/api/analysis/*` (trial-balance, balance-sheet, income-statement, cash-flow, tax-summary)
- N/A ~~OpenAPI/Swagger~~ — `/api/capabilities.json` provides machine-readable API discovery for AI consumers
- N/A ~~Per-key rate limiting~~ — global `RateLimitFilter` already covers all endpoints; per-key throttling unnecessary for single-company app
- N/A ~~API versioning~~ — premature for single-deployment app; can add later if needed

### 7.2 API Pagination ✅
- [x] `GET /api/analysis/transactions` — paginated (page, size params, returns Page<T>)
- [x] `GET /api/analysis/reports` — paginated (page, size params, default 0/20)
- [x] `GET /api/analysis/drafts` — paginated (page, size params, default 0/20)

### 7.3 Device Token Management UI ✅
- [x] Device token list page (`/settings/devices`) — view active tokens with device name, last used, created date
- [x] Revoke device token (with confirmation)
- [x] Revoke all tokens for current user
- [x] Token usage info (last_used_at, last_used_ip)
- [x] Sidebar link (Perangkat API) in Master Data group
- [x] 5 Playwright functional tests

**Phase 7 Deliverable:** REST API with OAuth 2.0 device auth, 7 API controllers, structured error handling, and device token management UI.

---

## Phase 8: Bank Reconciliation ✅

**Goal:** Automate bank statement matching with recorded transactions

### 8.1 Bank Statement Import
- [x] Bank parser config entity
- [x] ConfigurableBankStatementParser class
- [x] Column name matching with fallback
- [x] Preload configs (BCA, BNI, Mandiri, BSI, CIMB)
- [x] Admin UI for parser config
- [x] CSV/Excel upload and parsing
- [x] Statement item entity (date, description, amount, balance)

### 8.2 Transaction Matching
- [x] Bank reconciliation entity (period, status, bank account)
- [x] Auto-matching rules:
  - [x] Exact match (date + amount)
  - [x] Fuzzy match (±1 day, same amount)
  - [x] Description keyword matching
- [x] Manual matching UI (drag-and-drop or checkbox)
- [x] Create missing transactions from unmatched statement items
- [x] Mark as "bank only" or "book only" for discrepancies

### 8.3 Reconciliation Reports
- [x] Reconciliation summary (matched, unmatched, discrepancies)
- [x] Bank reconciliation statement (book balance → bank balance)
- [x] Outstanding items list
- [x] PDF/Excel export

**Value analysis (manual reconciliation time per month):**
- 20-30 transactions: 5-10 min (easy, no automation needed)
- 50-100 transactions: 15-30 min (manageable)
- 200-300 transactions: 1-2 hours (tedious, automation helpful)
- 500+ transactions: 3+ hours (automation essential)

**Phase 8 Deliverable:** Bank statement import, auto-matching, manual matching UI, and reconciliation reports.

---

## Phase 9: Analytics & Insights

**Goal:** Provide smart alerts and flexible transaction tagging

### 9.1 Trend Analysis — N/A
- N/A ~~Revenue trend chart (12 months)~~ — covered by AI analysis via `/api/analysis/snapshot` and published reports
- N/A ~~Expense trend by category (12 months)~~ — covered by AI analysis via `/api/analysis/income-statement`
- N/A ~~Profit margin trend (12 months)~~ — covered by AI analysis (KPI metrics with period-over-period change)
- N/A ~~Cash flow trend (12 months)~~ — covered by AI analysis via `/api/analysis/cash-flow`
- N/A ~~Comparison: current period vs previous period~~ — AI snapshot already includes `*Change` fields
- N/A ~~Comparison: current period vs same period last year~~ — AI can compute from existing endpoints
- N/A ~~Chart library integration (Chart.js or similar)~~ — static charts low ROI vs AI-generated contextual analysis

### 9.2 Smart Alerts ✅
- [x] AlertRule entity (type, threshold, enabled, last_triggered, description)
- [x] AlertEvent entity (rule, severity, message, details, acknowledged_at/by)
- [x] Alert types:
  - [x] Cash low warning (CASH_LOW)
  - [x] Overdue receivables (RECEIVABLE_OVERDUE)
  - [x] Expense spike vs average (EXPENSE_SPIKE)
  - [x] Project cost overrun (PROJECT_COST_OVERRUN)
  - [x] Project margin drop (PROJECT_MARGIN_DROP)
  - [x] Payment collection slowdown (COLLECTION_SLOWDOWN)
  - [x] Client concentration risk (CLIENT_CONCENTRATION)
- [x] Alert threshold settings per type
- [x] Enable/disable individual alerts
- [x] Dashboard notification widget (HTMX, severity counts, top 5 recent)
- N/A ~~Email notification~~ — dashboard widget and active alerts page sufficient for single-company app
- [x] Alert history and acknowledgment
- [x] Daily evaluation scheduler (8am, configurable via `app.alerts.schedule`)
- [x] 24h dedup (skip if unacknowledged event for same rule exists within 24h)
- [x] V009 migration (schema + 7 seed rules)
- [x] 3 permissions: ALERT_VIEW, ALERT_CONFIG, ALERT_ACKNOWLEDGE
- [x] 7 Playwright functional tests (5 config + 2 widget)
- [x] User manual (15-peringatan.md) with 4 automated screenshots

### 9.3 Transaction Tags ✅
- [x] Tag type entity (user-defined: "Channel", "Campaign", "Category")
- [x] Tag entity (values per type)
- [x] Tag type CRUD UI
- [x] Tag CRUD UI
- [x] Multi-tag per transaction (journal entry)
- [x] Tag filters in transaction list
- [x] Tag-based reports (summary by tag)

**Phase 9 Deliverable:** ✅ Complete — Configurable smart alerts with 7 evaluator types, daily evaluation, dashboard widget, and flexible transaction tagging.

---

## Phase 10: Invoice & Bill Management ✅

**Goal:** Standalone invoice/bill cycle with line items, payment tracking, and aging reports

**Note:** The existing Invoice entity is project-milestone-bound. This phase adds a general-purpose invoice/bill system usable by all industries, and extends aging/statement reports on top of it.

### 10.1 Outbound Invoice (Faktur Penjualan)
- [x] Extend Invoice entity: optional project (currently required), add line items
- [x] InvoiceLine entity (description, quantity, unit_price, tax, amount)
- [x] Invoice numbering (auto-increment per year, configurable prefix)
- [x] Invoice CRUD UI (create from scratch or from transaction)
- [x] Invoice PDF generation (printable, sendable to client)
- [x] Invoice status workflow: DRAFT → SENT → PARTIAL → PAID / OVERDUE / CANCELLED
- [x] Mark as sent (records sent_at)
- [x] Functional tests
- [x] User manual

### 10.2 Vendor Bill (Faktur Pembelian)
- [x] Bill entity (vendor/supplier, bill_number, bill_date, due_date, amount, status)
- [x] BillLine entity (description, quantity, unit_price, tax, amount)
- [x] Vendor entity (dedicated table — separate from Client, different data needs: default expense account, tax withholding)
- [x] Bill CRUD UI
- [x] Bill status workflow: DRAFT → APPROVED → PARTIAL → PAID / OVERDUE / CANCELLED
- [x] Link bill to purchase transaction
- [x] Functional tests
- [x] User manual

### 10.3 Vendor Bill API
- [x] `POST /api/bills` — create bill from AI-parsed data (vendor, lines, dates, amounts)
- [x] `POST /api/bills/{id}/approve` — approve and post journal entry
- [x] `GET /api/bills` — list bills (paginated, filterable by status/vendor/date)
- [x] `GET /api/bills/{id}` — bill detail
- [x] OAuth scope: `bills:create`, `bills:approve`, `bills:read`
- [x] Input: structured JSON (vendor name, bill number, date, due date, line items with description/quantity/unit_price/tax)
- [x] Vendor matching: match by name against existing vendors, create new if not found
- [x] Account mapping: use configurable default expense accounts per vendor or category
- [x] Functional tests

### 10.4 Payment Tracking
- [x] Record payment against specific invoice (full or partial)
- [x] Record payment against specific bill (full or partial)
- [x] Auto-create receipt/payment journal entry on payment recording
- [x] Payment history per invoice/bill
- [x] Outstanding balance calculation (invoice amount - sum of payments)
- [x] Auto-update status (PARTIAL when partially paid, PAID when fully paid)
- [x] Functional tests

### 10.5 Aging Reports
- [x] Receivables aging report (current, 30d, 60d, 90d, >90d buckets) based on invoice due dates
- [x] Payables aging report (same buckets) based on bill due dates
- [x] Aging by client/vendor
- [x] As-of date selection
- [x] PDF/Excel export
- [x] Functional tests
- [x] User manual

### 10.6 Customer/Vendor Statements
- [x] Per-client statement (outstanding invoices, payments received, balance)
- [x] Per-vendor statement (outstanding bills, payments made, balance)
- [x] Date range filter
- [x] Printable PDF (suitable for sending to client as payment reminder)
- [x] Functional tests
- [x] User manual

---

## Phase 11: Recurring Transactions ✅

**Goal:** Auto-posting scheduled transactions for predictable recurring expenses/revenues

### 11.1 Recurring Schedule
- [x] RecurringTransaction entity (template, frequency, start/end date, next_run, status)
- [x] Frequency options: daily, weekly, monthly, quarterly, yearly
- [x] Recurring transaction CRUD UI
- [x] Preview next N occurrences
- [x] Pause/resume schedule

### 11.2 Auto-Posting
- [x] Scheduler job (daily, creates and posts transactions on due date)
- [x] Skip weekends/holidays option
- [x] Notification on auto-post (dashboard widget)
- [x] Error handling (log failures, retry next day)
- [x] Functional tests
- [x] User manual

---

## Phase 12: Tax Data Management

**Goal:** Complete the tax data entry pipeline so all tax-related data (faktur pajak, bukti potong, client NPWP, fiscal periods) can be managed from within the app, eliminating reliance on external PDFs and Coretax as data sources.

**Context:** Phase 2 built the tax reporting and export infrastructure (TaxTransactionDetail entity, CoretaxExportService, tax calendar, tax reports). However, the data *input* path was never built — there is no UI or API to populate `tax_transaction_details`, no client management UI, and no fiscal period management UI. For FY2025, all tax data had to be cross-referenced manually from Coretax PDFs.

### Revenue Tax Workflow — FP Code 04 (Regular Client)

Applies to: Client-A, Client-B, BCA, Client-C, and other non-BUMN clients.

ArtiVisi collects PPN from client and remits it to state. Client withholds PPh 23.

**Example: Harga Jual 100,000,000**
- DPP = 100,000,000 × 11/12 = 91,666,667
- PPN = DPP × 12% = 11,000,000
- PPh 23 = 2% × DPP = 1,833,333
- Client pays = 100,000,000 + 11,000,000 - 1,833,333 = 109,166,667

**Step 1: Issue proforma invoice** (outside app)
- Send proforma/tagihan to client after work delivery
- No accounting entry in app

**Step 2: Client pays** (days to months later)
- Bank receives 109,166,667

**Step 3: Record in app** (on payment date)
- Post transaction using "Pendapatan Jasa + PPN + PPh 23" template:
  - DR Bank 109,166,667
  - DR Kredit Pajak PPh 23 1,833,333
  - CR Pendapatan 100,000,000
  - CR Hutang PPN 11,000,000
- Phase 12.4 auto-creates TaxTransactionDetail (taxType, DPP, PPN)

**Step 4: Issue Faktur Pajak in Coretax** (same day or next day)
- Create FP in Coretax → get FP number
- Issue real invoice + FP to client

**Step 5: Attach tax documents via API** (Claude Code)
- Parse FP PDF → post structured data to `POST /api/transactions/{id}/tax-details`
- Upload FP PDF to `POST /api/transactions/{id}/documents`
- Bupot arrives later (weeks/months) → update tax detail with bupot number + upload bupot PDF

**Step 6: Monthly PPN payment** (by 15th of next month)
- Post "Setor PPN" transaction: DR Hutang PPN / CR Bank
- Export e-Faktur Excel from app → file SPT Masa PPN in Coretax
- Mark tax calendar as completed

**Step 7: Annual**
- Accumulated Kredit Pajak PPh 23 → offset against PPh Badan in SPT Tahunan

### Revenue Tax Workflow — FP Code 03 (BUMN Client)

Applies to: BNI, Client-E, and other BUMN/government entities.

Key difference: **BUMN is pemungut PPN** — they withhold PPN and remit it directly to the state. ArtiVisi does NOT receive or pay PPN for these invoices. Client also withholds PPh 23.

**Example: Harga Jual 100,000,000**
- DPP = 100,000,000 × 11/12 = 91,666,667
- PPN = DPP × 12% = 11,000,000 (withheld by BUMN, not paid to ArtiVisi)
- PPh 23 = 2% × DPP = 1,833,333
- Client pays = 100,000,000 - 1,833,333 = 98,166,667 (no PPN in payment)

**Step 1-2: Same** (proforma invoice outside app, wait for payment)

**Step 3: Record in app** (on payment date)
- Post transaction using "Pendapatan Jasa BUMN (FP 03)" template:
  - DR Bank 98,166,667
  - DR Kredit Pajak PPh 23 1,833,333
  - CR Pendapatan 100,000,000
- No Hutang PPN entry — BUMN handles PPN remittance

**Step 4-5: Same** (issue FP in Coretax, Claude Code attaches FP/bupot data + PDFs)

**Step 6: Monthly PPN filing** (by 20th of next month)
- FP 03 invoices reported in SPT Masa PPN as "dipungut pemungut" (not payable by ArtiVisi)
- No PPN payment needed for these invoices

**Step 7: Same** (annual kredit pajak PPh 23 offset)

### 12.1 Fix PPN Template Formula ✅
- [x] Current formula `amount × 11/111` is incorrect for 2025 PPN (DPP Nilai Lain regime)
- [x] Correct formula: `DPP = amount × 11/12`, `PPN = DPP × 12%` (equivalent to `amount × 11%`)
- [x] Update all PPN-related journal templates (Pendapatan Jasa + PPN, Penjualan Barang + PPN, Beban dengan PPN)
- [x] Update seed data in industry seed packs (it-service, online-seller, artivisi)
- [x] New template: Pendapatan Jasa BUMN (FP 03) — PPN dipungut pembeli, PPh 23 dipotong
- [x] FormulaEvaluatorTest updated (amount * 1.11, amount * 1.09)
- [x] V903 test templates updated
- [x] Formula help UI updated (quick examples, syntax table, scenario walkthroughs)
- [x] Document the DPP Nilai Lain formula in user manual (04-perpajakan.md, 12-lampiran-template.md)
- [x] Production database updated via SQL script

### 12.2 Tax Transaction Detail Entry (Web UI) ✅
- [x] Tax detail form on transaction detail page (HTMX fragment inline)
- [x] Fields: fakturNumber, fakturDate, transactionCode (01/02/03/04/07/08), dpp, ppn, ppnbm
- [x] Fields: bupotNumber, taxObjectCode, grossAmount, taxRate, taxAmount
- [x] Fields: counterpartyNpwp, counterpartyNitku, counterpartyName, counterpartyAddress, counterpartyIdType
- [x] Auto-populate counterparty fields from Client when linked via Project
- [x] Auto-populate DPP/PPN from journal entries (tax account code detection)
- [x] Validation: fakturNumber uniqueness, NPWP format, required fields per taxType
- [x] List view: show tax indicator on transaction list
- [x] Bulk entry page: batch-attach tax details to transactions missing them
- [x] Permission: TAX_EXPORT (reused existing)
- [x] 10 Playwright functional tests

### 12.3 Tax Transaction Detail API ✅
- [x] `POST /api/transactions/{id}/tax-details` — attach tax detail to existing transaction
- [x] `PUT /api/transactions/{id}/tax-details/{detailId}` — update tax detail
- [x] `GET /api/transactions/{id}/tax-details` — list tax details for transaction
- [x] `GET /api/transactions/{id}/tax-details/{detailId}` — get single tax detail
- [x] `DELETE /api/transactions/{id}/tax-details/{detailId}` — remove tax detail
- [x] Bulk endpoint: `POST /api/tax-details/bulk` — attach details to multiple transactions
- [x] `POST /api/transactions/{id}/documents` — upload document attachment (multipart file upload)
- [x] `GET /api/transactions/{id}/documents` — list attached documents
- [x] `GET /api/documents/{docId}` — download document
- [x] `DELETE /api/documents/{docId}` — delete document
- [x] OAuth scope: `transactions:post` (reuse existing)
- [x] 10 Playwright functional tests
- [x] Update capabilities.json
- [x] DTOs: TaxDetailRequest, TaxDetailResponse, DocumentResponse
- [x] EntityNotFoundException handler in ApiExceptionHandler (returns 404 instead of 500)

### 12.4 Auto-populate Tax Details from Templates ✅
- [x] When posting a transaction via PPN template → auto-create TaxTransactionDetail with taxType=PPN_KELUARAN or PPN_MASUKAN
- [x] When posting via PPh 23 template → auto-create TaxTransactionDetail with taxType=PPH_23
- [x] When posting via PPh 4(2) template → auto-create TaxTransactionDetail with taxType=PPH_42
- [x] Auto-fill DPP, PPN/taxAmount from computed journal amounts
- [x] Auto-fill counterparty from linked Client/Vendor (if transaction has project/invoice reference)
- [x] Leave fakturNumber/bupotNumber blank (user fills in after receiving from Coretax)
- [x] Functional tests (5 tests: PPN auto-populate, PPh 23 auto-populate, non-tax skip, counterparty from client, no duplicates)

### 12.5 Client Management UI
- [x] Client list page with search and filters (active/inactive)
- [x] Client form: code, name, npwp, nitku, nik, idType, contactPerson, email, phone, address
- [x] Client detail page with linked projects, invoices, and tax transaction summary
- [x] NPWP format validation (XX.XXX.XXX.X-XXX.XXX)
- [x] Import clients from CSV
- [x] Permission: CLIENT_VIEW / CLIENT_CREATE / CLIENT_EDIT / CLIENT_DELETE
- [x] Sidebar link in Proyek group
- [x] Functional tests (18 tests)
- [ ] User manual update

### 12.6 Fiscal Period Management UI
- [x] Fiscal period list page (12 months per year, with status badges)
- [x] Open/close period workflow (OPEN → MONTH_CLOSED → TAX_FILED)
- [x] FiscalPeriodService: create periods for a year, update status, enforce locking
- [x] Block transaction posting in closed periods
- [x] Block period close if unposted drafts exist
- [x] Year selector
- [x] Permission: SETTINGS_VIEW / SETTINGS_EDIT
- [x] Functional tests
- [ ] User manual update

### 12.7 Tax Report Enhancements
- [x] PPN detail report: per-faktur breakdown (FP number, client, DPP, PPN, date) from tax_transaction_details
- [x] PPh 23 detail report: per-bupot breakdown (bupot number, client, gross, rate, tax) from tax_transaction_details
- [x] Cross-check report: PPN from faktur vs buku besar account balances (highlight mismatches)
- [x] Rekonsiliasi Fiskal worksheet: commercial P&L → koreksi fiskal → taxable income
- [x] PPh Badan calculator: PKP × rate (with Pasal 31E logic), kredit pajak offset (PPh 23 + PPh 25)
- [x] FiscalAdjustment entity with CRUD on rekonsiliasi fiskal page
- [x] PDF/Excel export for all 4 new reports (8 export methods)
- [x] Functional tests (21 tests)

### 12.8 Tax Calendar Data & Deadline Updates
- [ ] Update seed data: payment deadlines from 10th → 15th (per PMK 81/2024)
- [ ] Update PPN reporting deadline reference
- [ ] Migration to update existing tax_deadlines rows in production
- [ ] User manual update for section 04-perpajakan.md

### 12.9 Retrofit 2025 Data

After Phase 12 features are deployed, clean up and repost all 2025 transactions with correct PPN formula and complete tax details. Direct database deletion (not void) to avoid audit trail clutter.

**Pre-requisites:**
- Phase 12.1 (PPN formula fix) deployed
- Phase 12.3 (tax detail API + document API) deployed
- Phase 12.5 (client management) deployed
- Full production database backup

**Step 1: Populate client master data**
- [ ] Create clients from FP counterparty data via UI or API: Client-A (0402405385016000), BNI (0010016061093000), Client-B (0840399323012000), Client-E (0010611903051000), Client-C (0406194035043000), BCA (0013084496091000), Client-F
- [ ] Include NPWP, NITKU, addresses extracted from FP PDFs

**Step 2: Clean up existing transactions**
- [ ] Backup production database
- [ ] Delete all voided transactions (existing miscategorized voids — cleanup)
- [ ] Delete 22 revenue transactions posted with wrong PPN formula (`11/111`)
- [ ] Delete cascades to: journal_entries, journal_entry_lines, tax_transaction_details, transaction_documents
- [ ] Verify account balances are zeroed out for affected accounts

**Step 3: Repost with correct data** (Claude Code batch via API)
- [ ] Repost all 2025 revenue transactions using corrected PPN templates
- [ ] FP 04 invoices: use "Pendapatan Jasa + PPN + PPh 23" template (DR Bank, DR Kredit PPh 23, CR Pendapatan, CR Hutang PPN)
- [ ] FP 03 invoices (BUMN): use "Pendapatan Jasa BUMN" template (DR Bank, DR Kredit PPh 23, CR Pendapatan — no Hutang PPN)
- [ ] Repost all non-revenue transactions (expenses, tax payments, transfers, etc.) — these are unaffected by PPN formula but reposted for clean journal numbering

**Step 4: Attach tax details** (Claude Code batch via API)
- [ ] Parse 18 FP PDFs from `faktur-pajak/` folder
- [ ] Match each FP to the corresponding new transaction (by amount, date, client)
- [ ] Post structured FP data to `POST /api/transactions/{id}/tax-details`
- [ ] Upload FP PDFs to `POST /api/transactions/{id}/documents`
- [ ] Parse and attach bupot PDFs (once collected from clients)

**Step 5: Verify**
- [ ] Balance sheet matches expected balances
- [ ] Income statement totals unchanged (revenue same, PPN split corrected)
- [ ] Hutang PPN ledger matches sum of FP PPN amounts
- [ ] Kredit Pajak PPh 23 ledger matches sum of bupot amounts
- [ ] Coretax e-Faktur export produces correct data
- [ ] Tax summary report reconciles

**Phase 12 Deliverable:** Complete tax data management — entry UI/API for tax transaction details, client management, fiscal periods, corrected PPN formula, auto-population from templates, enhanced tax reports including rekonsiliasi fiskal, and retrofitted 2025 data.

---

## Phase 13: WhatsApp Notifications

**Goal:** Send invoice reminders and alert notifications via WhatsApp

### 13.1 WhatsApp Integration
- [ ] WhatsApp Business API provider integration (Wablas/Fonnte or official API)
- [ ] Provider configuration (API key, sender number)
- [ ] Message template management
- [ ] Send test message

### 13.2 Invoice Reminders
- [ ] Auto-send reminder for overdue invoices
- [ ] Configurable reminder schedule (e.g., 7d before due, on due date, 7d after)
- [ ] Per-client phone number (from Client entity)
- [ ] Opt-out per client

### 13.3 Alert Notifications
- [ ] Send smart alert events via WhatsApp to configured recipients
- [ ] Configurable: which alert types trigger WhatsApp notification
- [ ] Recipient list management
- [ ] Functional tests
- [ ] User manual

---

## Future Enhancements (As Needed)

Items below are not planned phases. They are implemented only when a concrete client need arises.

### Infrastructure
- [ ] S3-compatible document storage backend
- [ ] ClamAV virus scanning for uploads
- [ ] Multi-currency support
- [ ] Materialized account balances (only if report queries exceed 2s — current performance analysis shows this is decades away for typical usage)

### Custom Projects (Per Client Request)
- [ ] PJAP integration (e-Faktur, e-Bupot)
- [ ] Digital signature (PSrE)
- [ ] E-Meterai integration
- [ ] Payment gateway integration

---

## Database Migration Strategy

### Rules
1. **Always add, never remove** - Mark columns as deprecated, don't delete
2. **Nullable first** - New columns must be nullable or have defaults
3. **Backfill separately** - Data migration in separate step from schema change
4. **Test rollback** - Every migration must have tested rollback script

### Migration Files
- V001: Security (users, audit_logs, admin seed)
- V002: Core schema (COA, templates, transactions, clients, projects, invoices, vendors, bills, employees, payroll, assets, inventory, documents, tax)
- V003: Feature schema (device auth, bank recon, analysis reports, tags, alerts, payments, recurring transactions)
- V004: Seed data (transaction sequences, bank parser configs, alert rules)
- V900-V912: Test data migrations

---

## Testing Strategy

### Per Phase
- Unit tests for business logic
- Integration tests for database operations
- Functional tests (Playwright) for critical paths
- Migration tests on sample data

### Critical Paths to Test
1. Transaction creation and posting
2. Journal entry balance validation
3. Report generation
4. User authentication
5. Period locking enforcement

---

## Go-Live Criteria

### MVP (Phase 1) ✅
- [x] All Phase 1 features completed
- [x] No critical bugs
- [x] Performance acceptable (< 2s page load)
- [x] Backup/restore tested
- [x] Production environment ready
- [x] Monitoring in place
- [x] Support process defined

### Production Readiness
- [x] Security review completed (OWASP Dependency-Check, SpotBugs/FindSecBugs)
- [x] Data retention policy implemented (docs/07-data-retention-policy.md)
- [x] User documentation ready
- [x] Admin can manage users
- [x] Can export all data (regulatory compliance)
