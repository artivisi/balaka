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
| **1** | Core Accounting (MVP) - IT Services | ✅ Complete |
| **2** | Tax Compliance + Cash Flow | ✅ Complete |
| **3** | Payroll + RBAC + Self-Service | ✅ Complete |
| **4** | Fixed Assets | ✅ Complete |
| **5** | Inventory & Production | ✅ Complete |
| **6** | Security Hardening | 🔄 In Progress |
| **7** | API Foundation | ⏳ Not Started |
| **8** | Online Seller Support | ⏳ Not Started |
| **9** | Bank Reconciliation | ⏳ Not Started |
| **10** | Analytics & Insights | ⏳ Not Started |
| **11+** | Budget, Advanced Features | ⏳ Not Started |

---

## Phase 0: Project Setup ✅

### 0.1 Development Environment
- [x] Spring Boot 4.0 project structure
- [x] PostgreSQL 17 local setup (Testcontainers for tests)
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
- [x] 14 chapters in docs/user-manual/*.md
- [x] ScreenshotCapture.java with 26 page definitions
- [x] UserManualGenerator.java with scrollable TOC
- [x] GitHub Action for auto-deploy to GitHub Pages
- [x] Playwright-based screenshot capture

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

## Phase 6: Security Hardening

**Goal:** Address critical and high-severity security vulnerabilities identified in the security audit to make the application production-ready for hosting client data.

**Standards:** OWASP Top 10 (2021), PCI-DSS v4.0, NIST CSF

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

### 6.6 Data Protection & Data in Use (P2)

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
- [ ] Consent management for data processing
  - [ ] ConsentRecord entity (user, purpose, granted_at, withdrawn_at)
  - [ ] Consent capture on employee onboarding
  - [ ] Consent withdrawal workflow
- [x] Privacy notice display in application (/privacy-policy) - SettingsController /settings/privacy + privacy.html template
- [x] Data subject rights UI for administrators
  - [x] DataSubjectController with ADMIN-only access
  - [x] Data subjects list page (/settings/data-subjects)
  - [x] Data subject detail page with retention status
  - [x] DSAR export page (masked sensitive data)
  - [x] Anonymization confirmation page with reason and audit trail
  - [x] DataSubjectRightsTest (16 Playwright functional tests)
- [ ] Data breach response procedures
  - [ ] DataBreachIncident entity (detected_at, description, affected_count, notified_at)
  - [ ] Breach notification template (72h GDPR / 14 days UU PDP)
  - [ ] Breach response checklist in documentation
- [x] Data retention enforcement
  - [x] Retention status check - DataSubjectService.getRetentionStatus() (10-year retention per UU KUP Art. 28)
  - [ ] Automated purge job for expired data (based on docs/07-data-retention-policy.md)
  - [ ] Pre-deletion notification to admins
- [ ] Records of Processing Activities (ROPA)
  - [ ] Document what PII is collected, why, retention period
  - [ ] Data flow diagram in documentation
- [ ] Data Protection Impact Assessment (DPIA) template for high-risk processing

### 6.9 Automated Security Testing (DevSecOps) (P2)

**Goal:** Implement shift-left security with automated testing in CI/CD pipeline.

**Reference:** OWASP DevSecOps Guideline, NIST SP 800-218 (SSDF)

#### 6.9.1 Static Application Security Testing (SAST)
- [x] CodeQL analysis (GitHub native, Java support)
  - [x] Add `.github/workflows/codeql.yml`
  - [x] Configure security queries for Java
  - [x] Enable SARIF upload to GitHub Security tab
- [ ] Semgrep rules for Spring Security patterns
  - [ ] Custom rules for authentication bypass
  - [ ] SQL injection detection
  - [ ] XSS pattern detection
- [x] SonarCloud integration (https://sonarcloud.io/project/overview?id=artivisi_aplikasi-akunting)
  - [x] Security hotspot analysis
  - [x] Code smell detection
  - [x] Quality gate configured
  - [x] sonar-project.properties configured

#### 6.9.2 Software Composition Analysis (SCA)
- [x] OWASP Dependency-Check
  - [x] Configure fail threshold (CVSS ≥ 7) - `failBuildOnCVSS` in pom.xml
  - [x] NVD API key for faster scans - `nvdApiKey` config
  - [x] CI integration - `.github/workflows/security.yml`
- [ ] Dependency license scanning
  - [ ] Add license-maven-plugin
  - [ ] Block copyleft licenses if needed
- [x] SBOM generation (Software Bill of Materials)
  - [x] CycloneDX maven plugin (v2.9.1) - generates sbom.json and sbom.xml on package
  - [x] Includes compile, provided, runtime scopes
  - [ ] Publish SBOM with releases (GitHub Actions)

#### 6.9.3 Secret Detection
- [x] GitLeaks configuration
  - [x] `.gitleaks.toml` configuration
  - [x] Custom patterns for Indonesian tax IDs, bank accounts
- [ ] TruffleHog in CI pipeline
  - [ ] Scan git history for leaked secrets
  - [ ] Block PR if secrets detected
- [ ] GitHub secret scanning (enable in repo settings)

#### 6.9.4 Dynamic Application Security Testing (DAST)
- [x] Spring-integrated ZAP DAST (Recommended approach)
  - [x] `ZapDastTest.java` - ZAP runs within `@SpringBootTest`
  - [x] ZAP as Testcontainer with `host.testcontainers.internal` networking
  - [x] Baseline scan + authenticated scan
  - [x] Severity thresholds: 0 HIGH, ≤5 MEDIUM allowed
  - [x] Runs on PRs and weekly schedule
- [x] OWASP ZAP baseline scan (Standalone approach)
  - [x] Add `.github/workflows/dast.yml`
  - [x] Builds JAR then starts app before ZAP scan
  - [x] Configure authentication for authenticated scans
- [x] OWASP ZAP full scan (weekly schedule, on-demand)
  - [x] Spider + active scan
  - [x] Custom rules in `.zap/rules.tsv`
- [x] Nuclei vulnerability scanner
  - [x] Custom templates for Spring Boot
  - [x] CVE detection templates

#### 6.9.5 Container Security
- [ ] Trivy container scanning
  - [ ] Scan Docker images before push
  - [ ] OS vulnerability detection
  - [ ] Application dependency scanning
- [ ] Dockerfile best practices (hadolint)
  - [ ] No root user
  - [ ] Minimal base image
  - [ ] Multi-stage builds
- [ ] Container runtime security
  - [ ] Read-only root filesystem
  - [ ] Drop all capabilities
  - [ ] Security context constraints

#### 6.9.6 Infrastructure as Code (IaC) Security
- [ ] Checkov for Pulumi/Terraform
  - [ ] Scan `deploy/pulumi/` directory
  - [ ] Custom policies for Indonesian compliance
- [x] Ansible-lint security rules
  - [x] `.ansible-lint` configuration file
  - [x] no-log-password rule enabled
  - [x] Strict mode enabled

#### 6.9.7 API Security Testing
- [ ] OWASP ZAP API scan with OpenAPI spec
- [ ] Postman/Newman security test collection
  - [ ] Authentication bypass tests
  - [ ] Authorization tests (IDOR)
  - [ ] Input validation tests
  - [ ] Rate limiting verification
- [ ] API fuzzing with RESTler or similar

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
- [ ] Document security patch procedures for PCI-DSS compliance
- [x] Privacy policy page (/privacy) with UU PDP and GDPR compliance

**Phase 6 Deliverable:** Production-ready security posture with encrypted PII, strong authentication, comprehensive audit logging, and compliance with OWASP Top 10, PCI-DSS, and UU PDP/GDPR requirements.

---

## Phase 7: API Foundation

**Goal:** Expose REST API for external integrations, mobile apps, and domain-specific applications

**Strategy document:** `docs/08-multi-industry-expansion-strategy.md`

### 7.1 API Core
- [ ] Transaction entity: add `idempotency_key` column (unique, nullable)
- [ ] ApiKey entity (hashed key, name, permissions, created_at, last_used_at, active)
- [ ] ApiKeyService (generate, validate, revoke)
- [ ] ApiKeyAuthenticationFilter (Bearer token validation)
- [ ] TransactionApiController (`@RestController`, `/api/transactions`)
  - [ ] POST /api/transactions (execute template, create transaction)
  - [ ] GET /api/transactions/{id} (get by ID)
  - [ ] GET /api/transactions?idempotencyKey={key} (check existence)
- [ ] TemplateApiController (`/api/templates`)
  - [ ] GET /api/templates (list templates)
  - [ ] GET /api/templates/{code} (get template details)
- [ ] AccountApiController (`/api/accounts`)
  - [ ] GET /api/accounts (list accounts)
  - [ ] GET /api/accounts/{code} (get account details)
- [ ] API error response format (error code, message, timestamp)
- [ ] OpenAPI/Swagger documentation
- [ ] Integration tests for all API endpoints
- [ ] User manual: API documentation

### 7.2 API Enhancements
- [ ] ReportApiController (`/api/reports`)
  - [ ] GET /api/reports/trial-balance
  - [ ] GET /api/reports/balance-sheet
  - [ ] GET /api/reports/income-statement
- [ ] Pagination support (page, size, sort)
- [ ] Date range filtering for reports
- [ ] Rate limiting (configurable per API key)
- [ ] API audit logging (request/response, latency, errors)
- [ ] API versioning header (Accept-Version or URL prefix)

### 7.3 API Management UI
- [ ] API Keys list page (`/settings/api-keys`)
- [ ] Generate new API key (show once, then hashed)
- [ ] Revoke API key
- [ ] View API key usage statistics
- [ ] Permission scopes (read-only, read-write, admin)

**Phase 7 Deliverable:** REST API enabling external integrations, mobile apps, and domain-specific applications (grant management, POS, etc.).

---

## Phase 8: Online Seller Support

**Goal:** Support online sellers with marketplace reconciliation and seller-specific features

**Target users:** Tokopedia, Shopee, Bukalapak, Lazada sellers

### 8.1 Marketplace Reconciliation
- [ ] Marketplace parser config entity
- [ ] ConfigurableMarketplaceParser class
- [ ] Preload configs (Tokopedia, Shopee, Bukalapak, Lazada)
- [ ] Settlement report upload and parsing
- [ ] Order matching (marketplace order ID ↔ transaction)
- [ ] Fee extraction (platform fee, payment fee, promo subsidy)
- [ ] Auto-create fee expense transactions
- [ ] Reconciliation status tracking (matched, unmatched, discrepancy)
- [ ] Marketplace reconciliation report
- [ ] Functional tests
- [ ] User manual

### 8.2 Shipping Cost Tracking
- [ ] Shipping entity (order_id, courier, cost, status)
- [ ] Link shipping to sales transaction
- [ ] Shipping cost report (by courier, by period)
- [ ] COD handling (cash on delivery reconciliation)

### 8.3 Seller Dashboard
- [ ] GMV (Gross Merchandise Value) per marketplace
- [ ] Platform fees summary
- [ ] Net profit per marketplace
- [ ] Top selling products (requires inventory module)
- [ ] Marketplace comparison chart

**Phase 8 Deliverable:** Marketplace settlement reconciliation, fee tracking, and seller-specific dashboard.

---

## Phase 9: Bank Reconciliation

**Goal:** Automate bank statement matching with recorded transactions

### 9.1 Bank Statement Import
- [ ] Bank parser config entity
- [ ] ConfigurableBankStatementParser class
- [ ] Column name matching with fallback
- [ ] Preload configs (BCA, BNI, Mandiri, BSI, CIMB)
- [ ] Admin UI for parser config
- [ ] CSV/Excel upload and parsing
- [ ] Statement item entity (date, description, amount, balance)

### 9.2 Transaction Matching
- [ ] Bank reconciliation entity (period, status, bank account)
- [ ] Auto-matching rules:
  - [ ] Exact match (date + amount)
  - [ ] Fuzzy match (±1 day, same amount)
  - [ ] Description keyword matching
- [ ] Manual matching UI (drag-and-drop or checkbox)
- [ ] Create missing transactions from unmatched statement items
- [ ] Mark as "bank only" or "book only" for discrepancies

### 9.3 Reconciliation Reports
- [ ] Reconciliation summary (matched, unmatched, discrepancies)
- [ ] Bank reconciliation statement (book balance → bank balance)
- [ ] Outstanding items list
- [ ] PDF/Excel export

**Value analysis (manual reconciliation time per month):**
- 20-30 transactions: 5-10 min (easy, no automation needed)
- 50-100 transactions: 15-30 min (manageable)
- 200-300 transactions: 1-2 hours (tedious, automation helpful)
- 500+ transactions: 3+ hours (automation essential)

**Phase 9 Deliverable:** Bank statement import, auto-matching, manual matching UI, and reconciliation reports.

---

## Phase 10: Analytics & Insights

**Goal:** Provide trend analysis, smart alerts, and flexible transaction tagging

### 10.1 Trend Analysis
- [ ] Revenue trend chart (12 months)
- [ ] Expense trend by category (12 months)
- [ ] Profit margin trend (12 months)
- [ ] Cash flow trend (12 months)
- [ ] Comparison: current period vs previous period
- [ ] Comparison: current period vs same period last year
- [ ] Chart library integration (Chart.js or similar)

### 10.2 Smart Alerts
- [ ] Alert entity (type, threshold, enabled, last_triggered)
- [ ] Alert types:
  - [ ] Cash low warning
  - [ ] Overdue receivables
  - [ ] Expense spike (vs average)
  - [ ] Project cost overrun
  - [ ] Project margin drop
  - [ ] Payment collection slowdown
  - [ ] Client concentration risk
- [ ] Alert threshold settings per type
- [ ] Enable/disable individual alerts
- [ ] Dashboard notification display
- [ ] Email notification (optional)
- [ ] Alert history and acknowledgment

### 10.3 Transaction Tags
- [ ] Tag type entity (user-defined: "Channel", "Campaign", "Category")
- [ ] Tag entity (values per type)
- [ ] Tag type CRUD UI
- [ ] Tag CRUD UI
- [ ] Multi-tag per transaction (journal entry)
- [ ] Tag filters in transaction list
- [ ] Tag-based reports (summary by tag)

**Phase 10 Deliverable:** Trend charts, configurable alerts, and flexible transaction tagging.

---

## Phase 11+: Future Enhancements

### Budget Management
- [ ] Budget entity (account, period, amount)
- [ ] Budget per account per period
- [ ] Budget CRUD UI
- [ ] Copy from previous period
- [ ] Budget vs Actual report
- [ ] Variance analysis
- [ ] Over-budget highlighting
- [ ] PDF/Excel export

### Additional Industry Templates
- [ ] Photography COA and journal templates
- [ ] General Freelancer COA and journal templates
- [ ] Industry-specific salary component templates

### Account Balances (Materialized) - Performance Optimization

**Not implemented.** The system uses real-time calculation by querying journal_entries directly.

**Why not materialized?**
- Current approach: Sum debit/credit from journal_entries for each report
- PostgreSQL performance with indexes is excellent for typical transaction volumes
- Materialization adds complexity: triggers, recalculation logic, cascade updates
- Trade-off: Simple architecture vs. marginal performance gain

**Performance analysis (PostgreSQL with proper indexes):**
- 10,000 journal lines: <50ms (5 years @ 80 tx/month)
- 50,000 journal lines: ~100ms (5 years @ 400 tx/month)
- 100,000 journal lines: ~200ms (5 years @ 800 tx/month)
- 500,000 journal lines: ~500ms (5 years @ 4,000 tx/month)

**For typical small IT services (100 tx/month):** Would take 40+ years to reach 100,000 lines.

**When to reconsider:** Only if report generation consistently exceeds 2 seconds and users report slowness (>500 transactions/month sustained for several years).

**Implementation approach if needed:**
- [ ] Create account_balances table with monthly snapshots
- [ ] Trigger balance update on journal entry post/void
- [ ] Background job for period-based aggregation
- [ ] Balance recalculation utility for data fixes
- [ ] Modify ReportService to query materialized balances instead of journal_entries

### Document Management Enhancements
- [ ] S3-compatible storage backend
- [ ] Image compression on upload
- [ ] PDF optimization
- [ ] ClamAV virus scanning
- [ ] Bulk upload
- [ ] Document access logging

### Advanced Features (As Needed)
- [ ] Multi-currency support
- [ ] API for mobile app
- [ ] Custom report builder
- [ ] Dashboard analytics
- [ ] Automated backups
- [ ] Admin: view soft-deleted records

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
- V001: Security schema (users)
- V002: Security data (admin user)
- V003: App schema (COA, journal entries, templates, transactions, etc.)
- V004: App seed data
- V901-V907: Test data migrations

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
