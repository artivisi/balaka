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
| **4** | Reconciliation & Analytics | ⏳ Not Started |
| **5** | Assets & Budget | ⏳ Not Started |
| **6+** | Other Industries, Advanced Features | ⏳ Not Started |

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
- [ ] Period-end dashboard integration (deferred)
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
- [ ] Milestone overdue detection
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
- [ ] Pending amortization entries count widget (deferred)

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

**Phase 3 Deliverable:** ✅ Complete payroll system with tax compliance, role-based access control, and employee self-service.

---

## Phase 4: Analytics & Reconciliation

**Goal:** Transaction tagging, analytics, and bank reconciliation

### 4.1 Transaction Tags
- [ ] Tag type entity (user-defined: "Client", "Channel", "Category")
- [ ] Tag entity (values per type)
- [ ] Tag type CRUD UI
- [ ] Tag CRUD UI
- [ ] Multi-tag per transaction (journal entry)
- [ ] Tag filters in transaction list
- [ ] Tag-based reports (summary by tag)

### 4.2 Trend Analysis
- [ ] Revenue trend chart (12 months)
- [ ] Expense trend by category (12 months)
- [ ] Profit margin trend (12 months)
- [ ] Cash flow trend (12 months)
- [ ] Comparison: current period vs previous period
- [ ] Comparison: current period vs same period last year

### 4.3 Smart Alerts
- [ ] Project cost overrun alert
- [ ] Project margin drop alert
- [ ] Overdue receivables alert
- [ ] Payment collection slowdown alert
- [ ] Expense spike alert
- [ ] Cash low warning
- [ ] Client concentration risk alert
- [ ] Alert threshold settings per type
- [ ] Enable/disable individual alerts
- [ ] Alert delivery: Dashboard notification, Email (optional)
- [ ] Alert history and acknowledgment

### 4.4 Bank Reconciliation
- [ ] Bank parser config entity
- [ ] ConfigurableBankStatementParser class
- [ ] Column name matching with fallback
- [ ] Preload configs (BCA, BNI, BSI, CIMB)
- [ ] Admin UI for parser config
- [ ] Bank reconciliation entity
- [ ] Statement items entity
- [ ] CSV upload and parsing
- [ ] Auto-matching (exact date + amount)
- [ ] Fuzzy matching (±1 day)
- [ ] Manual matching UI
- [ ] Create missing transactions from statement
- [ ] Reconciliation report

**Value analysis (manual reconciliation time per month):**
- 20-30 transactions: 5-10 min (easy, no automation needed)
- 50-100 transactions: 15-30 min (manageable)
- 200-300 transactions: 1-2 hours (tedious, automation helpful)
- 500+ transactions: 3+ hours (automation essential)

**For typical small IT services (30-80 tx/month):** Manual reconciliation takes ~15 min/month.
Bank reconciliation feature becomes valuable at ~150+ transactions/month or with multiple bank accounts.

**Phase 4 Deliverable:** Transaction tagging, trend analysis, smart alerts, and bank reconciliation.

---

## Phase 5: Assets & Budget

**Goal:** Fixed asset tracking and budget management

**Implementation note:** Follow payroll pattern for journal posting:
- Route through Transaction → JournalTemplate → JournalEntry (not direct journal creation)
- Use extended FormulaContext with domain-specific variables (e.g., `assetCost`, `accumulatedDepreciation`, `disposalProceeds`, `gainLoss`)
- Create system templates for: asset purchase, depreciation entry, asset disposal
- Keep asset-specific logic in AssetService, core accounting remains generic

### 5.1 Fixed Asset Register
- [ ] Fixed asset entity
- [ ] Asset categories
- [ ] Asset CRUD UI
- [ ] Purchase recording

### 5.2 Depreciation
- [ ] Straight-line calculation
- [ ] Declining balance calculation
- [ ] Depreciation schedule
- [ ] Monthly depreciation batch job
- [ ] Auto-journal via templates

### 5.3 Asset Disposal
- [ ] Disposal workflow
- [ ] Gain/loss calculation
- [ ] Disposal journal entry

### 5.4 Budget Setup
- [ ] Budget entity
- [ ] Budget per account per period
- [ ] Budget CRUD UI
- [ ] Copy from previous period

### 5.5 Budget Reports
- [ ] Budget vs Actual report
- [ ] Variance analysis
- [ ] Over-budget highlighting
- [ ] PDF/Excel export

**Phase 5 Deliverable:** Asset management and budget tracking.

---

## Phase 6+: Future Enhancements

### Additional Industry Templates
- [ ] Photography COA and journal templates
- [ ] Online Seller COA and journal templates
- [ ] General Freelancer COA and journal templates
- [ ] Industry-specific salary component templates

### Marketplace Reconciliation
- [ ] Marketplace parser config entity
- [ ] ConfigurableMarketplaceParser class
- [ ] Preload configs (Tokopedia, Shopee, Bukalapak, Lazada)
- [ ] Settlement upload and parsing
- [ ] Order matching
- [ ] Fee expense auto-creation
- [ ] Marketplace reconciliation report

### Account Balances (Materialized) - Performance Optimization
- [ ] Account balances entity
- [ ] Balance update on journal entry post/void
- [ ] Period-based aggregation (monthly snapshots)
- [ ] Balance recalculation utility

**When to implement:** Only when report generation exceeds 2 seconds or users report slowness.

**Performance analysis (PostgreSQL with indexes):**
- 10,000 journal lines: <50ms (5 years @ 80 tx/month)
- 50,000 journal lines: ~100ms (5 years @ 400 tx/month)
- 100,000 journal lines: ~200ms (5 years @ 800 tx/month)
- 500,000 journal lines: ~500ms (5 years @ 4,000 tx/month)

**For typical small IT services (100 tx/month):** Would take 40+ years to reach 100,000 lines.
Materialization only needed at ~500+ transactions/month sustained for several years.

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
- [ ] Security review completed
- [x] Data retention policy implemented (docs/07-data-retention-policy.md)
- [x] User documentation ready
- [x] Admin can manage users
- [x] Can export all data (regulatory compliance)
