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
| **2** | Tax Compliance + Cash Flow | 🚧 In Progress (2.0-2.7, 2.9 done) |
| **3** | Payroll | ⏳ Not Started |
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
```sql
-- V001: Core tables
users
company_config
audit_logs
```

**Deliverable:** ✅ Running app with login, dashboard

**Deferred:**
- Local storage directory setup → Phase 2 (for document attachment)
- Soft delete → Phase 1.1 (with COA)

---

## Phase 1: Core Accounting (MVP) ✅

**Goal:** Minimal viable accounting system to go live

**Scope:** IT Services industry only (primary use case for initial deployment)

### Implementation Order Rationale

The features are ordered to maximize code reuse and enable incremental validation:

```
1.1 COA ✅ → 1.2 Journal Entries ✅ → 1.3 Reports ✅ → 1.4 Templates ✅ → 1.6 Formula ✅ → 1.5 Transactions ✅
                    │                      │               │                │              │
                    │                      │               │                │              │
                    └── Core service ──────┴── Validates ──┴── Generates ───┴── Unified ───┘
                        reused by all         the engine      journal entries   formula eval

                    │                                                                       │
                    ├──────────────────────────────────────────→ 1.8 Amortization Schedules
                    │                                                (auto-generates adjustments)
                    │
                    └──────────────────────────────────────────→ 1.9 Project Tracking
                                                                     (profitability analysis)
```

- **Journal Entries first:** Core double-entry engine. Users who understand accounting can use immediately.
- **Reports second:** Validates journal entries work correctly. Trial Balance = ultimate double-entry test.
- **Templates third:** Recipes that generate journal entries. Reuses JournalEntryService.
- **Formula Support before Transactions:** Unifies formula evaluation. Prevents preview ≠ post bugs.
- **Transactions last:** User-friendly abstraction. Uses unified FormulaEvaluator via TemplateExecutionEngine.
- **Amortization Schedules:** Automates period-end adjustments. Reuses JournalEntryService directly.
- **Project Tracking:** Tags transactions by project. Reuses AccountBalanceCalculator for profitability.

---

### 1.1 Chart of Accounts ✅
- [x] Account entity and repository
- [x] Account types (asset, liability, equity, revenue, expense)
- [x] Hierarchical structure (parent/child)
- [x] Soft delete (base entity with deleted_at, @SQLRestriction filter)
- [x] Account CRUD UI
- [x] Account activation/deactivation

**Note:** COA seed data removed from migrations. Users import COA via 1.12 Data Import feature.

```sql
-- V002: Chart of accounts (schema only, no seed data)
chart_of_accounts
```

---

### 1.2 Journal Entries (Manual) ✅

**Purpose:** Core double-entry bookkeeping engine. Accountants can record entries directly.

**Dependencies:** COA (1.1)

**Reused by:** Reports (1.3), Templates (1.4), Transactions (1.5)

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

```sql
-- V003: Journal entries
journal_entries (id, entry_date, description, reference_number, status, posted_at, voided_at, void_reason, ...)
journal_entry_lines (id, journal_entry_id, account_id, debit, credit, description, ...)
```

**Key Service Methods (reused later):**
```java
JournalEntryService {
    create(JournalEntryRequest) → JournalEntry
    post(UUID id) → JournalEntry
    void(UUID id, String reason) → JournalEntry
    validateBalance(List<Line>) → void  // throws if debit ≠ credit
}
```

---

### 1.3 Basic Reports ✅

**Purpose:** Validate journal entries, provide financial output. Trial Balance is the ultimate test of double-entry correctness.

**Dependencies:** Journal Entries (1.2)

**Reused by:** Account balance display, validation checks

- [x] Trial Balance report
- [x] General Ledger report (all entries per account)
- [x] Balance Sheet (Laporan Posisi Keuangan)
- [x] Income Statement (Laporan Laba Rugi)
- [x] Date range filtering
- [x] PDF export
- [x] Excel export

**Key Service Methods (reused later):**
```java
AccountBalanceCalculator {
    calculateTrialBalance(LocalDate asOf) → List<AccountBalance>
    calculateAccountBalance(UUID accountId, DateRange) → Balance
    getAccountTransactions(UUID accountId, DateRange) → List<JournalEntryLine>
}
```

**Note:** Balances calculated on-the-fly from journal_entry_lines. Materialized balances (1.7) deferred until performance requires it.

---

### 1.4 Journal Templates (Basic) ✅

**Purpose:** Predefined recipes for common transactions. Generates journal entries automatically.

**Dependencies:** COA (1.1), JournalEntryService (1.2)

**Reused by:** Transactions (1.5)

- [x] Template entity with versioning
- [x] Template lines entity (account mappings, debit/credit rules)
- [x] Category field (income, expense, payment, receipt, transfer)
- [x] Cash flow category field (operating, investing, financing)
- [x] Template CRUD UI
- [x] Template list with category filter
- [x] Template detail view
- [x] Template execution (generates journal entry)

**Note:** Template seed data removed from migrations. Users import templates via 1.12 Data Import feature.

```sql
-- V003: Journal templates (schema only, no seed data)
journal_templates (id, name, code, category, cash_flow_category, version, is_system, ...)
journal_template_lines (id, template_id, account_id, debit_formula, credit_formula, description, ...)
```

**Key Service Methods (reused later):**
```java
TemplateExecutionEngine {
    execute(JournalTemplate, TemplateContext) → JournalEntry  // calls journalEntryService.create()
    validate(JournalTemplate) → List<ValidationError>
}
```

**Note:** Tags, favorites, usage tracking deferred to 1.8

---

### 1.5 Transactions ✅

**Purpose:** User-friendly abstraction over templates. Non-accountants select a template, fill in amounts.

**Dependencies:** Templates (1.4), JournalEntryService (1.2)

- [x] Transaction entity with type and numbering
- [x] Transaction sequences per type (auto-increment per category)
- [x] Status workflow (draft → posted → void)
- [x] Transaction form UI (driven by template structure)
- [x] Account mapping from template
- [x] Transaction list with filters (date, type, status)
- [x] Transaction detail view
- [x] Post transaction (executes template → creates journal entry)
- [x] Void transaction (creates reversal entries)

```sql
-- V005: Transactions
transactions (id, template_id, journal_entry_id, transaction_number, status, ...)
transaction_sequences (id, category, current_value, prefix, ...)
transaction_values (id, transaction_id, field_name, value, ...)
```

**Flow:**
```
User fills transaction form
    → TransactionService.post()
        → TemplateExecutionEngine.execute()
            → JournalEntryService.create()
                → JournalEntryService.post()
```

---

### 1.6 Formula Support ✅

**Purpose:** Enable percentage calculations in templates (e.g., 11% PPN, 2% PPh 23).

**Dependencies:** Templates (1.4)

**Reference:** Decision #13 in `docs/99-decisions-and-questions.md`

#### Implementation

**1. Unified Formula Evaluation (per Decision #13)**
- [x] Create `FormulaContext` record with transaction data (`dto/FormulaContext.java`)
- [x] Create unified `FormulaEvaluator` service using SpEL (`service/FormulaEvaluator.java`)
- [x] Use `SimpleEvaluationContext.forReadOnlyDataBinding()`
- [x] Update `TemplateExecutionEngine` to use FormulaEvaluator
- [x] Update `TransactionService` to use FormulaEvaluator

**2. Formula Validation**
- [x] Validate formula syntax on template save
- [x] Return clear error messages for invalid formulas
- [x] Test formula against sample data before saving

**3. Supported Formula Patterns**
- [x] Simple: `amount` (pass-through)
- [x] Percentage: `amount * 0.11` (PPN 11%)
- [x] Division: `amount / 1.11` (extract DPP from gross)
- [x] Conditional: `amount > 2000000 ? amount * 0.02 : 0` (PPh 23 threshold)
- [x] Constants: `1000000` (fixed amount)

**4. Test Templates with Formulas**
- [x] Add test template: "Penjualan dengan PPN" (3 lines with formula)
- [x] Add test template: "PPh 23 Jasa" (conditional withholding)
- [x] Seed via test migration (`V903__formula_test_templates.sql`)

**5. Unit Tests**
- [x] `FormulaEvaluatorTest` - 28 tests for all formula patterns
- [x] Test invalid formula handling
- [x] Test edge cases (zero, negative, large numbers)

**6. Functional Tests**
- [x] Execute PPN template, verify calculated amounts
- [x] Execute PPh 23 template with amount > threshold
- [x] Execute PPh 23 template with amount < threshold

**7. In-App Documentation**
- [x] Formula help panel (`templates/fragments/formula-help.html`)
- [x] Live formula preview ("Coba Formula") on template form
- [x] Scenario examples in Indonesian

---

### 1.7 Template Enhancements ✅

**Purpose:** Improve template discoverability and user experience.

**Dependencies:** Templates (1.4)

- [x] Template tags
- [x] User favorites (per-user, not global)
- [x] Usage tracking (last used, frequency)
- [x] Search functionality
- [x] Recently used list

**Implementation:** Tables added to V003 migration (journal_template_tags, user_template_preferences). HTMX endpoints return Thymeleaf fragments for dynamic updates.

---

### 1.7.5 HTMX Partial Rendering Optimization

**Purpose:** Optimize existing pages to use HTMX partial rendering for better UX.

**Dependencies:** All list pages (1.2, 1.4, 1.5, 1.7)

**Reference:** `TODO-HTMX-OPTIMIZATION.md`

#### Pages to Optimize
| Page | Current Issue | HTMX Target | Status |
|------|---------------|-------------|--------|
| Template List | Full reload on search/filter | Swap grid only | ✅ Done |
| Journal List | Full reload on account/date/search | Swap ledger section | ✅ Done |
| Transaction List | Full reload + reload after actions | Swap table + inline row updates | ✅ Done |

**Note:** Dashboard KPIs moved to section 1.10 - will be built with HTMX from the start.

#### Pattern
1. Extract content area to Thymeleaf fragment
2. Add HTMX attributes (hx-get, hx-target, hx-swap)
3. Controller detects HX-Request header, returns fragment
4. Use hx-push-url for bookmarkable URLs

#### Implementation
- [x] Template List: search/filter partial rendering
- [x] Journal List: filters/pagination partial rendering
- [x] Transaction List: filters + inline post/delete

---

### 1.8 Amortization Schedules ✅

**Purpose:** Automate recurring period-end adjustments for prepaid expenses, unearned revenue, and intangible assets.

**Dependencies:** COA (1.1), JournalEntryService (1.2)

**Note:** Fixed asset depreciation handled separately in Phase 5 (requires fiscal regulation consultation).

#### Schedule Types
| Type | Indonesian | Example |
|------|------------|---------|
| `prepaid_expense` | Beban Dibayar Dimuka | Insurance, rent, software licenses |
| `unearned_revenue` | Pendapatan Diterima Dimuka | Advance payments, retainer fees |
| `intangible_asset` | Aset Tak Berwujud | Website, software development |
| `accrued_revenue` | Pendapatan Akrual | Monthly retainer billed quarterly |

#### Features
- [x] Amortization schedule entity
- [x] Amortization entries entity (tracks each period)
- [x] Schedule CRUD UI
- [x] Schedule list with filters (type, status)
- [x] Manual schedule creation (user-initiated, no auto-detection)
- [x] Auto-post toggle per schedule (user chooses during creation)
- [x] Monthly batch job (generates journal entries)
- [ ] Period-end dashboard integration (deferred to 1.10)
- [x] Remaining balance display
- [x] Schedule completion handling
- [x] Rounding handling (last period absorbs difference)

```sql
-- V007: Amortization schedules
amortization_schedules (id, schedule_type, code, name, description,
    source_transaction_id, source_account_id, target_account_id,
    total_amount, period_amount, start_date, end_date, frequency, total_periods,
    completed_periods, amortized_amount, remaining_amount,
    auto_post, post_day, status, created_by, created_at, updated_at)

amortization_entries (id, schedule_id, period_number, period_start, period_end,
    amount, journal_entry_id, status, generated_at, posted_at)
```

#### Journal Patterns
| Type | Debit | Credit |
|------|-------|--------|
| Prepaid Expense | Beban (target) | Dibayar Dimuka (source) |
| Unearned Revenue | Diterima Dimuka (source) | Pendapatan (target) |
| Intangible Asset | Beban Amortisasi (target) | Akum. Amortisasi (source) |
| Accrued Revenue | Piutang Pendapatan (source) | Pendapatan (target) |

#### Workflow
1. User creates schedule: name, type, accounts, amount, start/end date, frequency, auto-post toggle
2. System calculates: period_amount = total_amount ÷ total_periods
3. Monthly batch job creates journal entries (draft or posted based on auto_post setting)
4. User reviews and posts drafts (if auto_post = false)
5. System marks schedule completed when all periods done

#### UI with HTMX
- Schedule list: HTMX filters (type, status) - swap table without full reload
- Schedule detail: HTMX for entry status updates (post draft entries inline)
- Period-end dashboard: HTMX to load pending schedules section

#### COA Additions (in V002 seed data)
```
Assets:
1.1.05  Asuransi Dibayar Dimuka
1.1.06  Sewa Dibayar Dimuka
1.1.07  Langganan Dibayar Dimuka
1.1.08  Piutang Pendapatan
1.3     Aset Tak Berwujud (header)
1.3.01  Website & Software
1.3.02  Akum. Amortisasi Aset Tak Berwujud

Liabilities:
2.1.04  Pendapatan Diterima Dimuka

Expenses:
5.1.08  Beban Asuransi
5.1.09  Beban Amortisasi
```

---

### 1.9 Project Tracking ✅

**Purpose:** Track profitability per project/job for service businesses.

**Dependencies:** COA (1.1), JournalEntryService (1.2), Transactions (1.5)

**Note:** Decision #7 - Critical for IT Services and Photographers. Simple tagging approach, not full project management.

#### Project Features
- [x] Project entity (code, name, client_id, status, budget)
- [x] Project CRUD UI
- [x] Project list with filters (status, client)
- [x] Link transactions to project (project_id on journal entries and transactions)
- [x] Project selection in transaction form
- [x] Project Profitability Report
- [x] Project Income Statement (revenue - costs per project)

#### Client Features
- [x] Client entity (code, name, contact info, notes)
- [x] Client CRUD UI
- [x] Client list with search
- [x] Link projects to client
- [x] Client Profitability Report (aggregate of all client projects)
- [x] Client Revenue Ranking (top clients by revenue)

#### Project Milestones
- [x] Milestone entity (name, target date, completion %, status)
- [x] Milestone CRUD UI (inline in project detail)
- [x] Milestone status tracking (pending, in_progress, completed)
- [x] Milestone progress calculation (weighted by completion %)
- [ ] Milestone overdue detection

#### Payment Terms & Invoices
- [x] Payment term entity (name, %, trigger, linked milestone)
- [x] Payment term CRUD UI (inline in project detail)
- [x] Invoice entity (basic: number, date, amount, status)
- [x] Invoice generation from payment term
- [x] Invoice status tracking (draft, sent, paid, overdue)
- [x] Link invoice to payment term
- [x] Auto-trigger revenue recognition on milestone completion

#### UI with HTMX
- Project list: HTMX filters (status, client) - swap table without full reload
- Client list: HTMX search - swap table on typing
- Project detail: Inline milestone updates (mark complete via HTMX)
- Project detail: Inline payment term/invoice status updates
- Invoice list: HTMX filters (status, client, project)
- Transaction form: Project dropdown loaded via HTMX based on client selection

```sql
-- V008: Clients, Projects, Milestones, Payment Terms, Invoices
clients (id, code, name, contact_person, email, phone, address, notes,
    created_at, updated_at)

projects (id, code, name, client_id, description, status,
    contract_value, budget_amount, start_date, end_date,
    created_at, updated_at)

project_milestones (id, project_id, sequence, name, description,
    completion_percent, target_date, actual_date, status,
    created_at, updated_at)
    -- status: 'pending', 'in_progress', 'completed'

project_payment_terms (id, project_id, sequence, name,
    percentage, amount, due_trigger, milestone_id, due_date,
    invoice_id, amortization_schedule_id,
    created_at, updated_at)
    -- due_trigger: 'on_signing', 'on_milestone', 'on_completion', 'fixed_date'

invoices (id, invoice_number, client_id, project_id, payment_term_id,
    invoice_date, due_date, amount, status,
    sent_at, paid_at, journal_entry_id,
    created_at, updated_at)
    -- status: 'draft', 'sent', 'paid', 'overdue', 'cancelled'

-- Add to journal_entries
ALTER TABLE journal_entries ADD COLUMN project_id UUID REFERENCES projects(id);
```

#### Project Status
| Status | Description |
|--------|-------------|
| `active` | Currently in progress |
| `completed` | Finished, still visible in reports |
| `archived` | Hidden from dropdowns, visible in historical reports |

#### Profitability Report
```
Project: Website Redesign - PT ABC
Period: Jan - Mar 2025

Revenue:
  Pendapatan Jasa Development    Rp 50,000,000
  ─────────────────────────────────────────────
  Total Revenue                  Rp 50,000,000

Direct Costs:
  Beban Server & Cloud           Rp    500,000
  Beban Software & Lisensi       Rp    300,000
  ─────────────────────────────────────────────
  Total Direct Costs             Rp    800,000

Gross Profit                     Rp 49,200,000  (98.4%)
```

#### Client Profitability Report
```
Client: PT ABC
Period: 2025

Projects:
  Website Redesign     Rp 50,000,000 revenue   Rp 49,200,000 profit (98.4%)
  Mobile App Dev       Rp 80,000,000 revenue   Rp 65,000,000 profit (81.3%)
  Maintenance Q1-Q4    Rp 24,000,000 revenue   Rp 22,000,000 profit (91.7%)
  ───────────────────────────────────────────────────────────────────────────
  Total               Rp 154,000,000 revenue  Rp 136,200,000 profit (88.4%)

Ranking: #1 of 12 clients (28% of total revenue)
```

#### Cost Overrun Detection (with Milestones)
```
Project: Mobile App - PT XYZ
Contract: Rp 80,000,000    Budget: Rp 50,000,000

Milestone Progress:
  ✓ Design (20%)        - Completed
  ◐ Development (50%)   - 80% done → contributes 40%
  ○ Testing (20%)       - Pending
  ○ Deployment (10%)    - Pending
  ─────────────────────────────────────────────────
  Total Progress: 60%

Cost Analysis:
  Budget:        Rp 50,000,000
  Spent:         Rp 42,000,000 (84%)
  Progress:      60%

  ⚠️ OVERRUN RISK: 60% complete but 84% budget spent
  Projected Final Cost: Rp 70,000,000 (140% of budget)
  Projected Loss:       Rp 20,000,000
```

#### Payment Terms & Revenue Recognition
```
Project: Website Redesign - PT ABC
Contract: Rp 50,000,000

Payment Terms:                                    Revenue Recognition:
┌────────────────────────────────────────────────────────────────────────┐
│ Term          %    Amount         Trigger        Invoice    Revenue   │
├────────────────────────────────────────────────────────────────────────┤
│ Down Payment  30%  Rp 15,000,000  On signing     ✓ Paid     Deferred  │
│ Design Done   30%  Rp 15,000,000  Milestone 1    ✓ Sent     ✓ Recognized│
│ Dev Complete  30%  Rp 15,000,000  Milestone 3    ○ Pending  Deferred  │
│ Go Live       10%  Rp  5,000,000  Completion     ○ Pending  Deferred  │
└────────────────────────────────────────────────────────────────────────┘

Unearned Revenue (Liability):  Rp 35,000,000  (DP + Dev + GoLive)
Recognized Revenue:            Rp 15,000,000  (Design milestone)
```

#### Integration: Milestone → Revenue Recognition
When milestone is marked complete:
1. System finds linked payment term
2. Auto-creates/updates amortization entry for that term
3. Journal: Dr. Pendapatan Diterima Dimuka / Cr. Pendapatan Jasa
4. Updates project profitability in real-time

**Note:** Overhead allocation (rent, utilities) not included - too complex for MVP. Users can manually add project-tagged expenses for full costing.

---

### 1.10 Dashboard KPIs

**Purpose:** Provide at-a-glance business health metrics on the main dashboard.

**Dependencies:** Reports (1.3), AccountBalanceCalculator

#### KPI Features
- [x] Revenue (current month, vs previous month %)
- [x] Expenses (current month, vs previous month %)
- [x] Net Profit (current month, vs previous month %)
- [x] Profit Margin % (current month, vs previous month pts)
- [x] Cash Balance (sum of cash/bank accounts)
- [x] Receivables Total (Piutang Usaha balance)
- [x] Payables Total (Hutang Usaha balance)

#### UI Mockup
```
┌─────────────────────────────────────────────────────────────┐
│  November 2025                                               │
├─────────────────────────────────────────────────────────────┤
│  Revenue        Rp 85,000,000    ▲ 12% vs Oct               │
│  Expenses       Rp 35,000,000    ▼ 5% vs Oct                │
│  Net Profit     Rp 50,000,000    ▲ 25% vs Oct               │
│  Profit Margin  58.8%            ▲ 6pts vs Oct              │
│                                                              │
│  Cash           Rp 120,000,000                              │
│  Receivables    Rp 25,000,000                               │
│  Payables       Rp 10,000,000                               │
└─────────────────────────────────────────────────────────────┘
```

#### UI with HTMX
- Load KPI cards via HTMX on page load (`hx-trigger="load"`)
- Date range selector with HTMX to refresh KPIs
- Loading indicator (`hx-indicator`) while calculating
- Fragment: `fragments/dashboard-kpis.html`

#### Implementation
- [x] Create DashboardService with KPI calculation methods
- [x] Create DashboardController with HTMX endpoint
- [x] Create dashboard-kpis.html fragment
- [x] Update dashboard.html to load KPIs via HTMX
- [x] Add month selector for historical comparison
- [ ] Pending amortization entries count widget (deferred to Phase 2)
- [ ] Link to pending amortization entries list (deferred to Phase 2)

---

### 1.11 Comprehensive User Manual ✅

**Purpose:** Complete user documentation with automated screenshot capture and GitHub Pages publishing.

**Dependencies:** All Phase 1 features complete

#### Infrastructure

| Component | Location | Status |
|-----------|----------|--------|
| Markdown content | `docs/user-manual/*.md` | 14 chapters |
| Screenshot capture | `ScreenshotCapture.java` | 26 page definitions |
| HTML generator | `UserManualGenerator.java` | 14 sections, scrollable TOC |
| GitHub Action | `.github/workflows/publish-manual.yml` | Auto-deploy to GitHub Pages |
| Screenshot test | `ScreenshotCaptureTest.java` | Playwright-based verification |

**Workflow:**
1. Push to main/claude/** triggers workflow
2. Start application with PostgreSQL
3. Playwright captures screenshots of defined pages
4. Flexmark converts markdown → HTML with embedded screenshots
5. Deploy to GitHub Pages

#### Implementation Tasks

##### Phase A: Update Existing Infrastructure
- [x] Update `ScreenshotCapture.java` - 26 PageDefinition records with seed data UUIDs
- [x] Update `UserManualGenerator.java` - 14 Section records, fixed navbar scrolling

##### Phase A.1: Update Existing Chapters (1-7)
- [x] Update `01-pendahuluan.md` - Add Amortization, Projects, Clients, Invoices to feature list
- [x] Update `03-dashboard.md` - Rewrite for 8 KPI cards, month selector, HTMX loading
- [x] Update `05-template-jurnal.md` - Add Tags, Search, Favorites, Conditional formulas
- [x] Update `06-transaksi.md` - Add Project linking, filter by project

##### Phase B: New Documentation Content
- [x] Write `08-laporan-keuangan.md` - Reports overview, filters, export
- [x] Write `09-amortisasi.md` - Schedule creation, entry management, batch posting
- [x] Write `10-klien.md` - CRUD operations, deactivation
- [x] Write `11-proyek.md` - Milestones, progress calculation, status workflow
- [x] Write `12-invoice.md` - Generation from payment terms, payment flow
- [x] Write `13-laporan-profitabilitas.md` - Metrics, cost overrun detection
- [x] Write `14-glosarium.md` - Accounting terms reference

##### Phase C: Test Data for Screenshots
- [x] Use existing test migrations for screenshot data (V904, V905)
- [x] Pages have meaningful content from seed data

##### Phase D: Enhancements (Deferred to Phase 2)
- [ ] In-app help link (menu item linking to GitHub Pages)
- [ ] Contextual help tooltips on complex form fields
- [ ] Search functionality (client-side JS on generated HTML)
- [ ] PDF export option (print-friendly CSS already exists)

---

### 1.12 Data Import ✅

**Purpose:** Import COA and Journal Templates from JSON/Excel files. Replaces hardcoded seed data in migrations.

**Dependencies:** COA (1.1), Templates (1.4)

**Rationale:** Every company has different COA needs. Seed data in migrations creates coupling between code and business data. Import feature allows:
- Fresh start with custom COA
- Industry-specific templates (IT Services, Retail, Manufacturing)
- Easy migration from other systems
- No code changes needed for customization

#### Features

##### COA Import
- [x] Import from JSON file
- [x] Import from Excel file (XLSX)
- [x] Validate account structure (parent references, account types)
- [x] Validate account codes (uniqueness, format)
- [x] Preview before import (show what will be created)
- [x] Error handling with line-by-line feedback
- [x] **Clear before import option** - delete all existing accounts before importing

##### Journal Template Import
- [x] Import from JSON file
- [x] Validate template structure (lines, formulas)
- [x] Validate account references (must exist in COA)
- [x] Preview before import
- [x] Error handling with detailed feedback
- [x] **Clear before import option** - delete all existing templates before importing

##### Clear Data (Pre-Import)
- [x] Clear all COA accounts (with cascade validation)
- [x] Block clear if journal entries exist (data integrity)
- [x] Clear all journal templates (with cascade to template lines)
- [x] Block clear if transactions reference templates
- [x] Checkbox confirmation before clear

#### Importable Templates

Pre-built templates available for download (not in migrations):

```
/templates/
├── coa/
│   ├── sak-emkm-it-services.json      ← Your company's COA
│   ├── sak-emkm-retail.json
│   └── sak-emkm-manufacturing.json
└── journal-templates/
    ├── it-services.json                ← Your company's templates
    ├── retail.json
    └── manufacturing.json
```

#### JSON Format: COA

```json
{
  "name": "SAK EMKM - IT Services",
  "version": "1.0",
  "accounts": [
    {
      "code": "1",
      "name": "ASET",
      "type": "ASSET",
      "normalBalance": "DEBIT",
      "isHeader": true,
      "isPermanent": true
    },
    {
      "code": "1.1",
      "name": "Aset Lancar",
      "type": "ASSET",
      "normalBalance": "DEBIT",
      "parentCode": "1",
      "isHeader": true,
      "isPermanent": true
    },
    {
      "code": "1.1.01",
      "name": "Kas",
      "type": "ASSET",
      "normalBalance": "DEBIT",
      "parentCode": "1.1",
      "isHeader": false,
      "isPermanent": true
    }
  ]
}
```

#### JSON Format: Journal Templates

```json
{
  "name": "IT Services Templates",
  "version": "1.0",
  "templates": [
    {
      "name": "Pendapatan Jasa Konsultasi",
      "category": "INCOME",
      "cashFlowCategory": "OPERATING",
      "description": "Template untuk mencatat pendapatan jasa konsultasi",
      "lines": [
        {
          "accountCode": "1.1.02",
          "position": "DEBIT",
          "formula": "amount"
        },
        {
          "accountCode": "4.1.02",
          "position": "CREDIT",
          "formula": "amount"
        }
      ]
    }
  ]
}
```

#### UI with HTMX
- [x] File upload
- [x] Real-time validation feedback (HTMX preview)
- [x] Success/error summary

#### Implementation
- [x] Create DataImportService (unified for COA and Templates)
- [x] Create DataImportController with HTMX endpoints
- [x] Create import UI pages
- [x] Add import menu item
- [x] Sample file downloads

---

### 1.13 Deployment & Operations

**Purpose:** Production deployment with systemd, filesystem storage, and backup/restore capabilities.

**Dependencies:** All Phase 1 features

#### Deployment Architecture

```
┌─────────────────────────────────────────────────────────┐
│                      VPS Server                         │
├─────────────────────────────────────────────────────────┤
│  systemd                                                │
│  ├── accounting.service (Spring Boot app)               │
│  └── postgresql.service (database)                      │
│                                                         │
│  /opt/accounting/                                       │
│  ├── accounting-finance.jar                             │
│  ├── application-prod.properties                        │
│  ├── documents/           ← filesystem storage          │
│  │   ├── invoices/                                      │
│  │   ├── receipts/                                      │
│  │   └── attachments/                                   │
│  ├── backup/              ← backup files                │
│  └── logs/                ← application logs            │
└─────────────────────────────────────────────────────────┘
```

#### Systemd Service

```ini
# /etc/systemd/system/accounting.service
[Unit]
Description=Accounting Finance Application
After=postgresql.service
Requires=postgresql.service

[Service]
Type=simple
User=accounting
WorkingDirectory=/opt/accounting
ExecStart=/usr/bin/java -jar accounting-finance.jar --spring.profiles.active=prod
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

#### Production Configuration

```properties
# application-prod.properties
spring.datasource.url=jdbc:postgresql://localhost:5432/accountingdb
spring.datasource.username=${DB_USER}
spring.datasource.password=${DB_PASSWORD}
server.port=10000

# Document storage
app.storage.type=filesystem
app.storage.path=/opt/accounting/documents

# Logging
logging.file.path=/opt/accounting/logs
logging.level.root=INFO
```

#### Backup & Restore

##### Backup Features
- [ ] Database backup (pg_dump)
- [ ] Document folder backup (tar/rsync)
- [ ] Combined backup script
- [ ] Backup manifest (timestamp, file list, checksums)
- [ ] Backup rotation (keep last N backups)
- [ ] Backup to external location (rsync to remote)
- [ ] Backup scheduling (cron)
- [ ] Backup notification (success/failure)

##### Restore Features
- [ ] Restore from backup file
- [ ] Validate backup integrity (checksums)
- [ ] Restore database (pg_restore)
- [ ] Restore documents
- [ ] Point-in-time recovery (if WAL enabled)
- [ ] Restore confirmation prompt

##### Backup Script

```bash
#!/bin/bash
# /opt/accounting/scripts/backup.sh

BACKUP_DIR=/opt/accounting/backup
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
BACKUP_NAME="accounting_${TIMESTAMP}"

# Create backup directory
mkdir -p ${BACKUP_DIR}/${BACKUP_NAME}

# Database backup
pg_dump -U accounting accountingdb > ${BACKUP_DIR}/${BACKUP_NAME}/database.sql

# Documents backup
tar -czf ${BACKUP_DIR}/${BACKUP_NAME}/documents.tar.gz -C /opt/accounting documents/

# Create manifest
cat > ${BACKUP_DIR}/${BACKUP_NAME}/manifest.json << EOF
{
  "timestamp": "${TIMESTAMP}",
  "database": "database.sql",
  "documents": "documents.tar.gz",
  "checksums": {
    "database": "$(sha256sum ${BACKUP_DIR}/${BACKUP_NAME}/database.sql | cut -d' ' -f1)",
    "documents": "$(sha256sum ${BACKUP_DIR}/${BACKUP_NAME}/documents.tar.gz | cut -d' ' -f1)"
  }
}
EOF

# Create final archive
tar -czf ${BACKUP_DIR}/${BACKUP_NAME}.tar.gz -C ${BACKUP_DIR} ${BACKUP_NAME}
rm -rf ${BACKUP_DIR}/${BACKUP_NAME}

# Rotate old backups (keep last 7)
ls -t ${BACKUP_DIR}/*.tar.gz | tail -n +8 | xargs -r rm

echo "Backup completed: ${BACKUP_DIR}/${BACKUP_NAME}.tar.gz"
```

##### Restore Script

```bash
#!/bin/bash
# /opt/accounting/scripts/restore.sh

BACKUP_FILE=$1
RESTORE_DIR=/tmp/accounting_restore

if [ -z "$BACKUP_FILE" ]; then
  echo "Usage: restore.sh <backup_file.tar.gz>"
  exit 1
fi

# Extract backup
mkdir -p ${RESTORE_DIR}
tar -xzf ${BACKUP_FILE} -C ${RESTORE_DIR}
BACKUP_NAME=$(ls ${RESTORE_DIR})

# Validate checksums
echo "Validating backup integrity..."
# ... checksum validation ...

# Stop application
sudo systemctl stop accounting

# Restore database
echo "Restoring database..."
psql -U accounting accountingdb < ${RESTORE_DIR}/${BACKUP_NAME}/database.sql

# Restore documents
echo "Restoring documents..."
rm -rf /opt/accounting/documents/*
tar -xzf ${RESTORE_DIR}/${BACKUP_NAME}/documents.tar.gz -C /opt/accounting/

# Cleanup
rm -rf ${RESTORE_DIR}

# Start application
sudo systemctl start accounting

echo "Restore completed"
```

##### Cron Schedule

```cron
# Daily backup at 2 AM
0 2 * * * /opt/accounting/scripts/backup.sh >> /opt/accounting/logs/backup.log 2>&1

# Weekly sync to remote (optional)
0 3 * * 0 rsync -avz /opt/accounting/backup/ user@remote:/backups/accounting/
```

#### Deployment Checklist

##### Pre-Deployment
- [ ] VPS provisioned (Ubuntu 22.04+ recommended)
- [ ] Java 25 installed
- [ ] PostgreSQL 17 installed and configured
- [ ] Firewall configured (only 80/443 open)
- [ ] SSL certificate obtained (Let's Encrypt)
- [ ] Nginx reverse proxy configured (optional)
- [ ] accounting user created

##### Deployment Steps
- [ ] Create /opt/accounting directory structure
- [ ] Copy jar and configuration
- [ ] Create systemd service file
- [ ] Configure PostgreSQL database
- [ ] Run Flyway migrations
- [ ] Start and enable service
- [ ] Verify application health
- [ ] Configure backup cron

##### Post-Deployment
- [ ] Import COA via 1.12
- [ ] Import Journal Templates via 1.12
- [ ] Create initial users
- [ ] Test backup/restore
- [ ] Document admin procedures

#### Data Migration (From Excel)

##### Migration Strategy
- Opening balance from Jan 1, 2025 Balance Sheet
- Manual transaction entry for Jan-Nov 2025 (~220 transactions at ~20/month)
- Staff familiarization during migration
- Verify Trial Balance against Excel before go-live

##### Migration Timeline

| Phase | Period | Activity | Owner |
|-------|--------|----------|-------|
| **Preparation** | Dec 1-7, 2025 | Deploy app, import COA & templates | Admin |
| **Staff Training** | Dec 8-14, 2025 | Train staff on transaction entry | Admin + Staff |
| **Data Entry** | Dec 15-31, 2025 | Enter opening balance + historical transactions | Staff |
| **Verification** | Jan 1-3, 2026 | Compare Trial Balance, fix discrepancies | Admin |
| **Go Live** | Jan 4, 2026 | Start using app for 2026 transactions | All |

##### Step 1: Opening Balance Entry (Jan 1, 2025)
- [ ] Prepare Balance Sheet figures from Excel
- [ ] Create single journal entry with all opening balances:
  ```
  Dr. Assets (Kas, Bank, Piutang, etc.)
  Cr. Contra Assets (Akum. Penyusutan)
  Cr. Liabilities (Hutang, Kewajiban Escrow)
  Cr. Equity (Modal Disetor, Laba Ditahan)
  ```
- [ ] Verify: Total Debit = Total Credit
- [ ] Post opening balance entry

##### Step 2: Enter Historical Transactions (~220 total)
- [ ] Enter Jan 2025 transactions (~20)
- [ ] Enter Feb 2025 transactions (~20)
- [ ] Enter Mar 2025 transactions (~20)
- [ ] Enter Apr 2025 transactions (~20)
- [ ] Enter May 2025 transactions (~20)
- [ ] Enter Jun 2025 transactions (~20)
- [ ] Enter Jul 2025 transactions (~20)
- [ ] Enter Aug 2025 transactions (~20)
- [ ] Enter Sep 2025 transactions (~20)
- [ ] Enter Oct 2025 transactions (~20)
- [ ] Enter Nov 2025 transactions (~20)

**Tip:** With journal templates configured, ~220 transactions can be entered in 1-2 days of focused work. Use this as opportunity to test templates and train staff.

##### Step 3: Verification
- [ ] Generate Trial Balance from app
- [ ] Compare with Excel totals per account
- [ ] Investigate and fix discrepancies
- [ ] Final sign-off: Trial Balance matches

##### Step 4: Go Live (Target: Jan 4, 2026)
- [ ] Switch to app for new transactions
- [ ] Archive Excel files (read-only backup)
- [ ] Document cutover date

---

**Deliverable:** Working accounting system - can record journal entries manually or via templates, generate reports, automate period-end adjustments, track project/client profitability with milestones and payment terms

**Note:** Document attachment deferred to Phase 2. Store receipts in external folder during MVP.

### MVP Checklist for Go Live
- [x] Can create manual journal entries (for accountants)
- [x] Can create transactions using templates (for business users)
- [x] Templates support formula calculations (percentages, conditionals)
- [x] Trial Balance balances (validates double-entry correctness)
- [x] Can generate Balance Sheet and Income Statement
- [x] Can export reports to PDF/Excel
- [x] Dashboard shows KPIs (revenue, expenses, profit, cash, receivables, payables)
- [x] Can set up amortization schedules for prepaid/unearned items
- [x] Period-end adjustments auto-generated from schedules
- [x] Can create and manage clients
- [x] Can create projects with milestones and payment terms
- [x] Can generate invoices from payment terms
- [x] Milestone completion triggers revenue recognition
- [x] Cost overrun detection (% spent vs % complete)
- [x] Can generate Project Profitability Report
- [x] Can generate Client Profitability Report
- [x] Basic user management
- [ ] Database backup via pg_dump (no documents yet)
- [ ] Production deployment tested

### Code Reuse Summary

| Component | Created In | Reused By |
|-----------|------------|-----------|
| JournalEntryService | 1.2 | 1.3, 1.4, 1.5, 1.8, 1.9 |
| AccountBalanceCalculator | 1.3 | 1.4 (validation), 1.5 (display), 1.8 (remaining balance), 1.9 (profitability) |
| TemplateExecutionEngine | 1.4 | 1.5 |
| FormulaEvaluator | 1.6 | 1.4 (template execution), 1.5 (transaction posting) |
| ChartOfAccountRepository | 1.1 | All subsequent features |
| AmortizationScheduleService | 1.8 | Period-end dashboard |
| ProjectService | 1.9 | Transaction form, Profitability reports |

---

## Phase 2: Tax Compliance

**Goal:** Indonesian tax features (PPN, PPh) + document attachment

### 2.0 Infrastructure (Deferred from Phase 0) ✅
- [x] Local storage directory setup

### 2.1 Transaction Evidence (Document Attachment) ✅

**Purpose:** Attach receipts, invoices, and supporting documents to transactions.

**Dependencies:** Transactions (1.5)

**Reused by:** Telegram Receipt Import (2.2)

#### Features
- [x] Document entity (id, filename, content_type, size, storage_path, checksum)
- [x] Local filesystem storage service
- [x] Storage directory configuration (application.yml)
- [x] File upload UI (drag-and-drop, single file)
- [x] Attach document to transaction (transaction_id FK)
- [x] Attach document to journal entry (journal_entry_id FK)
- [x] View document (inline for images/PDFs)
- [x] Download document
- [ ] Thumbnail generation (images only, lazy) - deferred
- [x] Delete document (soft delete, keep file for audit)
- [x] File type validation (images, PDF only for MVP)
- [x] File size limit (10MB default)

```sql
-- V009: Documents
documents (id, filename, original_filename, content_type, file_size,
    storage_path, checksum_sha256,
    transaction_id, journal_entry_id, invoice_id,
    uploaded_by, uploaded_at, deleted_at)

CREATE INDEX idx_documents_transaction ON documents(transaction_id);
CREATE INDEX idx_documents_journal_entry ON documents(journal_entry_id);
```

#### Storage Structure
```
/data/documents/
  ├── 2025/
  │   ├── 01/
  │   │   ├── {uuid}.jpg
  │   │   ├── {uuid}.pdf
  │   │   └── {uuid}_thumb.jpg
  │   └── 02/
  └── 2026/
```

#### UI Integration
- Transaction form: document upload section
- Transaction detail: document preview/download
- Journal entry detail: linked documents list

---

### 2.2 Telegram Receipt Import ✅

**Purpose:** Capture receipts via Telegram bot, auto-extract transaction data using OCR, create draft transactions for review.

**Dependencies:** Document Attachment (2.1), Transactions (1.5), Templates (1.4)

**External Services:** Google Cloud Vision API (free tier: 1,000 units/month)

#### Architecture (Webhook Mode)
```
┌─────────────┐         ┌────────────────────────────────┐    ┌──────────────┐
│  Telegram   │  HTTPS  │     Your App (Spring Boot)     │    │ Google Cloud │
│  Servers    │────────►│  POST /api/telegram/webhook    │───►│ Vision API   │
└─────────────┘  push   └────────────────────────────────┘    └──────────────┘
                                      │                              │
                                      ▼                              ▼
                              ┌───────────────┐              ┌──────────────┐
                              │ Document      │              │ Receipt      │
                              │ Storage       │              │ Parser       │
                              └───────────────┘              └──────────────┘
                                      │                              │
                                      └──────────┬───────────────────┘
                                                 ▼
                                      ┌───────────────────┐
                                      │ Draft Transaction │
                                      │ (pending review)  │
                                      └───────────────────┘
```

**Why Webhook over Long Polling:**
- Instant delivery (no polling delay)
- Lower resource usage (no persistent connection)
- Telegram auto-retries failed deliveries
- Better scalability for production

#### Features

##### Telegram Bot Integration (Webhook Mode)
- [x] TelegramBots library dependency (org.telegram:telegrambots-springboot-webhook-starter)
- [x] Bot configuration (token, username, webhook URL in application.yml)
- [x] Webhook endpoint: POST /api/telegram/webhook
- [x] Webhook registration on application startup
- [x] Secret token validation (X-Telegram-Bot-Api-Secret-Token header)
- [x] User registration flow (link Telegram user to app user)
- [x] Photo message handler
- [x] Text command handler (/start, /status, /help)
- [ ] Rate limiting (max 10 receipts/hour per user) - deferred to future enhancement

##### Receipt OCR (Google Cloud Vision)
- [x] Google Cloud Vision client dependency
- [x] Vision API configuration (credentials JSON)
- [x] Document text detection (DOCUMENT_TEXT_DETECTION)
- [x] Receipt data extraction service
- [x] Fallback to raw text if parsing fails

##### Google Cloud Vision Setup (One-time Admin Setup)

**Step 1: Create Google Cloud Project**
1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create new project: `akunting-receipt-ocr`
3. Note the Project ID (e.g., `akunting-receipt-ocr-12345`)

**Step 2: Enable Vision API**
1. Go to **APIs & Services** → **Library**
2. Search for "Cloud Vision API"
3. Click **Enable**

**Step 3: Create Service Account**
1. Go to **APIs & Services** → **Credentials**
2. Click **Create Credentials** → **Service Account**
3. Name: `receipt-ocr-service`
4. Role: **Cloud Vision API User** (or basic **Viewer** for read-only)
5. Click **Done**

**Step 4: Generate JSON Key**
1. Click on the created service account
2. Go to **Keys** tab
3. **Add Key** → **Create new key** → **JSON**
4. Download the JSON file (e.g., `akunting-receipt-ocr-credentials.json`)
5. Store securely (never commit to git!)

**Step 5: Configure Application**
```bash
# Option A: Environment variable (recommended for production)
export GOOGLE_APPLICATION_CREDENTIALS=/path/to/akunting-receipt-ocr-credentials.json

# Option B: application.yml (for development)
google:
  cloud:
    vision:
      credentials-path: ${GOOGLE_CLOUD_CREDENTIALS_PATH}
```

**Step 6: Verify Setup**
```bash
# Test with gcloud CLI (optional)
gcloud auth activate-service-account --key-file=credentials.json
gcloud ml vision detect-text ./test-receipt.jpg
```

**Pricing & Free Tier:**
| Feature | Free Tier | Price After |
|---------|-----------|-------------|
| DOCUMENT_TEXT_DETECTION | 1,000 units/month | $1.50 per 1,000 |
| TEXT_DETECTION | 1,000 units/month | $1.50 per 1,000 |

**Note:** DOCUMENT_TEXT_DETECTION is better for receipts (handles multi-column layouts)

**Maven Dependency:**
```xml
<dependency>
    <groupId>com.google.cloud</groupId>
    <artifactId>google-cloud-vision</artifactId>
    <version>3.31.0</version>
</dependency>
```

**Spring Boot Configuration:**
```yaml
# application.yml
google:
  cloud:
    vision:
      enabled: true
      credentials-path: ${GOOGLE_APPLICATION_CREDENTIALS:}
```

##### Receipt Parsing
- [x] Extract merchant name (usually top of receipt)
- [x] Extract transaction date/time
- [x] Extract total amount (look for "TOTAL", "GRAND TOTAL", etc.)
- [x] Extract payment method (if visible)
- [ ] Extract items list (optional, for detailed matching) - deferred
- [x] Indonesian receipt format handling (Rp, IDR)
- [x] Confidence scoring per field

##### Merchant-to-Template Mapping
- [x] MerchantMapping entity (pattern → template)
- [x] Fuzzy merchant name matching (exact, contains, regex)
- [ ] Auto-learn from user selections - deferred
- [x] Mapping CRUD UI
- [ ] Bulk import common merchants - deferred

##### Draft Transaction Workflow
- [x] DraftTransaction entity (pending external transactions)
- [x] Draft list UI with filters (status, date, source)
- [x] Draft detail/edit UI
- [x] Review and approve workflow
- [x] Reject with reason
- [ ] Batch approve selected drafts - deferred
- [ ] Auto-approve rules (high confidence + known merchant) - deferred

```sql
-- V010: Telegram Receipt Import
telegram_user_links (id, user_id, telegram_user_id, telegram_username,
    linked_at, is_active)

draft_transactions (id, source, source_reference, telegram_message_id,
    -- Extracted data
    merchant_name, transaction_date, amount, currency, raw_ocr_text,
    -- Parsed fields confidence (0-1)
    merchant_confidence, date_confidence, amount_confidence,
    -- Mapping
    suggested_template_id, overall_confidence,
    -- Document
    document_id,
    -- Status
    status, rejection_reason,
    created_by, created_at, processed_by, processed_at)
    -- status: 'pending', 'approved', 'rejected', 'auto_approved'

merchant_mappings (id, merchant_pattern, match_type, template_id,
    default_description, match_count, last_used_at,
    created_by, created_at, updated_at)
    -- match_type: 'exact', 'contains', 'regex'

CREATE INDEX idx_draft_transactions_status ON draft_transactions(status);
CREATE INDEX idx_draft_transactions_user ON draft_transactions(created_by);
CREATE INDEX idx_merchant_mappings_pattern ON merchant_mappings(merchant_pattern);
```

#### Receipt Parsing Examples

**Indonesian Receipt (Indomaret/Alfamart):**
```
INDOMARET
JL. SUDIRMAN NO 123
JAKARTA

12/11/2025 14:30:25

AQUA 600ML      2 x 4,000    8,000
INDOMIE GORENG  3 x 3,500   10,500
                          --------
TOTAL                      18,500
TUNAI                      20,000
KEMBALIAN                   1,500
```
Extracted: merchant=INDOMARET, date=2025-11-12 14:30, amount=18500

**Restaurant Receipt:**
```
WARUNG PADANG SEDERHANA
Jl. Gatot Subroto 45

Nasi Padang Komplit    35,000
Es Teh Manis            5,000
                     --------
Subtotal               40,000
PPN 11%                 4,400
                     --------
TOTAL                  44,400

QRIS - 26 Nov 2025
```
Extracted: merchant=WARUNG PADANG SEDERHANA, date=2025-11-26, amount=44400

#### Auto-Approve Rules
```java
if (overallConfidence > 0.90
    && merchantMapping.matchCount > 5
    && amount < autoApproveThreshold) {
    autoApprove(draft);
} else {
    saveToPending(draft);
}
```

#### Cost Estimate (Google Cloud Vision)

| Usage | Free Tier | Overage Cost |
|-------|-----------|--------------|
| Document Text Detection | 1,000 units/month | $1.50 per 1,000 |

For 500 receipts/month: **FREE** (within free tier)

#### User Flow

1. **Setup (one-time)**
   - User goes to Settings → Telegram Integration
   - Click "Connect Telegram"
   - Bot sends verification code
   - User enters code in app → linked

2. **Daily Usage**
   - User takes photo of receipt
   - Send to bot via Telegram
   - Bot replies: "✓ Receipt received. Processing..."
   - Bot replies: "Extracted: Indomaret Rp 18,500 on 12 Nov. Review in app."

3. **Review in App**
   - User logs in, sees notification badge
   - Goes to Draft Transactions
   - Reviews extracted data, selects template if needed
   - Clicks Approve → Transaction posted
   - Or edits and approves

#### Telegram Bot Commands
```
/start - Register and link account
/status - Check pending drafts count
/recent - Show last 5 receipts
/help - Show usage instructions
```

#### Telegram Bot Setup (One-time Admin Setup)

**Step 1: Create Bot via BotFather**
1. Open Telegram, search for `@BotFather`
2. Send `/newbot`
3. Enter bot name: `Akunting Receipt Bot` (display name)
4. Enter username: `akunting_receipt_bot` (must end with `bot`)
5. BotFather returns: `HTTP API token: 123456789:ABCdefGHIjklMNOpqrsTUVwxyz`
6. Save the token securely (this is `TELEGRAM_BOT_TOKEN`)

**Step 2: Configure Bot Settings**
```
/setdescription - "Kirim foto struk untuk input transaksi otomatis"
/setabouttext - "Bot untuk aplikasi akunting. Kirim struk belanja untuk diproses."
/setuserpic - Upload bot profile picture
/setcommands - Set command menu:
    start - Mulai dan hubungkan akun
    status - Cek jumlah draft pending
    recent - Lihat 5 struk terakhir
    help - Bantuan penggunaan
```

**Step 3: Set Webhook (automatic on app startup, or manual)**
```bash
# Manual webhook registration (optional, app does this on startup)
curl -X POST "https://api.telegram.org/bot<TOKEN>/setWebhook" \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://your-app.com/api/telegram/webhook",
    "secret_token": "your-secret-token",
    "allowed_updates": ["message"]
  }'

# Verify webhook status
curl "https://api.telegram.org/bot<TOKEN>/getWebhookInfo"
```

**Step 4: Environment Variables**
```bash
# .env or deployment config
TELEGRAM_BOT_TOKEN=123456789:ABCdefGHIjklMNOpqrsTUVwxyz
TELEGRAM_BOT_USERNAME=akunting_receipt_bot
TELEGRAM_WEBHOOK_SECRET=random-secret-string-min-32-chars
```

#### Webhook Implementation Details

**Configuration (application.yml):**
```yaml
telegram:
  bot:
    token: ${TELEGRAM_BOT_TOKEN}
    username: ${TELEGRAM_BOT_USERNAME}
    webhook:
      url: https://your-app.com/api/telegram/webhook
      secret-token: ${TELEGRAM_WEBHOOK_SECRET}
```

**Controller:**
```java
@RestController
@RequestMapping("/api/telegram")
public class TelegramWebhookController {

    @PostMapping("/webhook")
    public BotApiMethod<?> onUpdate(
            @RequestBody Update update,
            @RequestHeader("X-Telegram-Bot-Api-Secret-Token") String token) {
        validateSecretToken(token);
        return receiptBotService.handleUpdate(update);
    }
}
```

**Webhook Registration (on startup):**
```java
@EventListener(ApplicationReadyEvent.class)
public void registerWebhook() {
    SetWebhook webhook = SetWebhook.builder()
        .url(webhookUrl)
        .secretToken(secretToken)
        .allowedUpdates(List.of("message"))
        .build();
    telegramClient.execute(webhook);
}
```

---

### 2.3 Tax Accounts Setup ✅
- [x] Pre-configured tax accounts in COA templates
- [x] PPN Masukan / Keluaran accounts
- [x] PPh 21, 23, 4(2), 25, 29 accounts

### 2.4 PPN Templates ✅
- [x] Penjualan + PPN Keluaran template
- [x] Pembelian + PPN Masukan template
- [x] PPN calculation (11%)
- [x] Non-PKP templates (no PPN) - existing basic templates work for non-PKP

### 2.5 PPh Templates ✅
- [x] PPh 23 withholding templates (2%)
- [x] PPh 4(2) templates (10% for rental)
- [x] Tax payment templates (Setor PPh 21/23/4(2)/PPN/25)

### 2.6 Tax Reports ✅
- [x] PPN Summary Report
- [x] PPN Detail (Keluaran/Masukan)
- [x] PPh 23 Withholding Report
- [x] Coretax Excel export (e-Faktur Keluaran/Masukan, Bupot Unifikasi)
- [x] Tax transaction details entity (TaxTransactionDetail)
- [x] Tax identification fields (NPWP, NITKU, NIK) on Client and CompanyConfig
- [x] Tax Object Code enum (PPh 23/4(2) codes per KEP-143/PJ/2022)

**Coretax Integration Notes:**
- Export format matches DJP Converter Excel templates
- Workflow: App → Excel export → DJP Converter → XML → Coretax import
- Validated fields: NPWP, NITKU, Kode Objek Pajak, Tarif

### 2.7 Fiscal Period Management ✅
- [x] Fiscal periods entity
- [x] Period status (open, month_closed, tax_filed)
- [x] Soft lock on month close
- [x] Hard lock after tax filing
- [x] Period close workflow
- [x] Functional tests (9 Playwright tests)

```sql
-- Added to V003: fiscal_periods table
fiscal_periods
```

### 2.8 Tax Calendar
- [ ] Tax deadline configuration
- [ ] Dashboard reminders
- [ ] Monthly checklist

### 2.9 Backup & Restore Utility ✅
- [x] Backup service (database + documents)
- [x] Coordinated backup (consistent state between DB and files)
- [x] Backup to local directory
- [x] Restore utility with validation
- [x] Backup scheduling (manual trigger via Ansible, cron optional)
- [x] Backup manifest (metadata, timestamp, checksums)
- [x] Backup rotation (configurable retention count)
- [x] Remote sync (rsync, optional)
- [x] Notifications (webhook, optional)

### 2.10 Cash Flow Statement
- [ ] Cash flow report generation
- [ ] Group by cash_flow_category from templates
- [ ] Operating/Investing/Financing sections
- [ ] PDF/Excel export

**Deliverable:** Tax-compliant accounting with export formats for DJP, document storage, Telegram receipt import with OCR, proper backup/restore, and cash flow reporting

---

## Phase 3: Payroll

**Goal:** Full payroll with PPh 21 and BPJS

### 3.1 Employee Management
- [ ] Employee entity
- [ ] Employee CRUD UI
- [ ] PTKP status configuration
- [ ] NPWP validation

```sql
-- V011: Employees
employees
```

### 3.2 Salary Components
- [ ] Salary component entity
- [ ] Component types (gaji pokok, tunjangan, BPJS, etc.)
- [ ] Preloaded component templates for IT Services
- [ ] Employee salary configuration UI

```sql
-- V012: Salary components
salary_components
```

### 3.3 BPJS Calculation
- [ ] BPJS Kesehatan rates (4% + 1%)
- [ ] BPJS Ketenagakerjaan rates (JKK, JKM, JHT, JP)
- [ ] Company vs employee portion
- [ ] Auto-calculation service

### 3.4 PPh 21 Calculation
- [ ] Progressive tax rates (5%-35%)
- [ ] PTKP deduction by status
- [ ] Biaya jabatan (5%, max 500rb)
- [ ] Monthly vs annual calculation
- [ ] PPh 21 calculator service

### 3.5 Payroll Processing
- [ ] Payroll run entity
- [ ] Payroll details entity
- [ ] Monthly payroll workflow
- [ ] Calculate all employees
- [ ] Review and adjust
- [ ] Post to journal entries
- [ ] Generate payslips

```sql
-- V013: Payroll
payroll_runs
payroll_details
```

### 3.6 Payroll Reports
- [ ] Payroll summary report
- [ ] PPh 21 monthly report
- [ ] BPJS report
- [ ] Payslip PDF generation

**Deliverable:** Complete payroll system with tax compliance

---

## Phase 4: Reconciliation & Analytics

**Goal:** Bank/marketplace reconciliation, transaction tagging, analytics, and access control

### 4.1 Bank Parser Infrastructure
- [ ] Bank parser config entity
- [ ] ConfigurableBankStatementParser class
- [ ] Column name matching with fallback
- [ ] Preload configs (BCA, BNI, BSI, CIMB)
- [ ] Admin UI for parser config

```sql
-- V014: Bank parser configs
bank_parser_configs
```

### 4.2 Bank Reconciliation
- [ ] Bank reconciliation entity
- [ ] Statement items entity
- [ ] CSV upload and parsing
- [ ] Auto-matching (exact date + amount)
- [ ] Fuzzy matching (±1 day)
- [ ] Manual matching UI
- [ ] Create missing transactions from statement
- [ ] Reconciliation report

```sql
-- V015: Bank reconciliation
bank_reconciliations
bank_statement_items
```

### 4.3 Marketplace Parser Infrastructure
- [ ] Marketplace parser config entity
- [ ] ConfigurableMarketplaceParser class
- [ ] Preload configs (Tokopedia, Shopee, Bukalapak, Lazada)

```sql
-- V016: Marketplace parser configs
marketplace_parser_configs
```

### 4.4 Marketplace Reconciliation
- [ ] Settlement upload and parsing
- [ ] Order matching
- [ ] Fee expense auto-creation
- [ ] Marketplace reconciliation report

### 4.5 Transaction Tags

**Purpose:** Flexible multi-dimensional tagging for transactions beyond projects.

**Dependencies:** Transactions (1.5), Projects (1.10)

**Note:** Extends project tracking with user-defined dimensions (client, channel, category).

#### Features
- [ ] Tag type entity (user-defined: "Client", "Channel", "Category")
- [ ] Tag entity (values per type)
- [ ] Tag type CRUD UI
- [ ] Tag CRUD UI
- [ ] Multi-tag per transaction (journal entry)
- [ ] Tag filters in transaction list
- [ ] Tag-based reports (summary by tag)

```sql
-- V017: Transaction tags
tag_types (id, name, description, is_system, created_at)
tags (id, tag_type_id, name, color, created_at)
journal_entry_tags (journal_entry_id, tag_id, PRIMARY KEY (journal_entry_id, tag_id))
```

#### Use Cases by Segment
| Segment | Tag Types |
|---------|-----------|
| IT Services | Client, Project Type (dev, consulting, training) |
| Photographers | Client, Event Type (wedding, corporate, product) |
| Online Sellers | Channel (Shopee, Tokopedia, Instagram), Category |

**Note:** Projects (1.10) handle the primary project tracking. Tags provide additional dimensions for analysis.

### 4.6 Trend Analysis

**Purpose:** Visualize business performance over time.

**Dependencies:** Reports (1.3), Dashboard KPIs

#### Features
- [ ] Revenue trend chart (12 months)
- [ ] Expense trend by category (12 months)
- [ ] Profit margin trend (12 months)
- [ ] Cash flow trend (12 months)
- [ ] Comparison: current period vs previous period
- [ ] Comparison: current period vs same period last year

```
Revenue Trend (Last 12 Months)
─────────────────────────────────────────────────
100M ┤                                    ╭──────
 80M ┤                        ╭───────────╯
 60M ┤            ╭───────────╯
 40M ┤     ╭──────╯
 20M ┼─────╯
     └─────────────────────────────────────────────
     Jan Feb Mar Apr May Jun Jul Aug Sep Oct Nov Dec
```

### 4.7 Smart Alerts

**Purpose:** Proactive notifications to help users take action before problems occur.

**Dependencies:** Projects (1.10), Reports (1.3), Receivables data

#### Alert Types

| Alert | Trigger | Action |
|-------|---------|--------|
| **Project Cost Overrun** | Costs > budget × threshold% | Review expenses, adjust scope |
| **Project Margin Drop** | Margin < target% | Investigate cost increases |
| **Overdue Receivables** | Invoice past due date | Follow up with client |
| **Payment Collection Slowdown** | Avg collection days increasing | Review AR process |
| **Expense Spike** | Category expense > 150% of average | Investigate unusual spending |
| **Cash Low Warning** | Cash < X months of expenses | Plan for cash needs |
| **Client Concentration Risk** | Single client > 40% revenue | Diversify client base |

#### Project Cost Overrun Alert (Priority)

Critical for mitigating project losses:

```
⚠️ ALERT: Project Cost Overrun Risk

Project: Mobile App - PT XYZ
Budget:  Rp 50,000,000
Spent:   Rp 42,000,000 (84%)
Status:  60% complete (estimated)

Projected Final Cost: Rp 70,000,000 (140% of budget)
Projected Loss:       Rp 20,000,000

Recommendation:
- Review remaining scope
- Negotiate change order
- Identify cost reduction opportunities
```

#### Alert Configuration
- [ ] Alert threshold settings per alert type
- [ ] Enable/disable individual alerts
- [ ] Alert delivery: Dashboard notification, Email (optional)
- [ ] Alert history and acknowledgment

```sql
-- V018: Smart alerts
alert_configurations (id, alert_type, enabled, threshold_value,
    notify_dashboard, notify_email, created_at, updated_at)

alert_history (id, alert_type, entity_type, entity_id,
    alert_data, acknowledged, acknowledged_by, acknowledged_at, created_at)
```

### 4.8 Account Balances (Materialized) - Performance Optimization

**Purpose:** Cache account balances for faster report generation.

**Dependencies:** Journal Entries (1.2), Reports (1.3)

**Note:** Implement when performance requires it. On-the-fly calculation sufficient for MVP.

- [ ] Account balances entity
- [ ] Balance update on journal entry post/void
- [ ] Period-based aggregation (monthly snapshots)
- [ ] Balance recalculation utility (rebuild from journal entries)

```sql
-- Account balances (when needed)
account_balances (id, account_id, period_start, period_end, opening_balance, debit_total, credit_total, closing_balance, ...)
```

### 4.9 User Management & Role-Based Access Control

**Purpose:** Manage users and restrict access based on roles. Required when adding non-trusted users (staff, external auditors).

**Dependencies:** Core authentication (Phase 0)

**Note:** MVP operates with single trusted admin user. Implement this when adding staff or external users.

#### Roles

| Role | Indonesian | Description | Typical User |
|------|------------|-------------|--------------|
| `ADMIN` | Administrator | System configuration, user management, destructive operations | IT person, system admin |
| `OWNER` | Pemilik | Full business visibility, approvals, company settings | Director, business owner |
| `ACCOUNTANT` | Akuntan | Full accounting operations, post/void entries | Internal accountant |
| `STAFF` | Staf | Data entry via templates, cannot post (requires approval) | Admin staff |
| `AUDITOR` | Auditor | Read-only access, export reports | External auditor, tax consultant |

#### Permission Matrix

| Feature | ADMIN | OWNER | ACCOUNTANT | STAFF | AUDITOR |
|---------|:-----:|:-----:|:----------:|:-----:|:-------:|
| **System** |
| User Management | ✅ | ❌ | ❌ | ❌ | ❌ |
| Import/Clear COA | ✅ | ❌ | ❌ | ❌ | ❌ |
| Backup/Restore | ✅ | ❌ | ❌ | ❌ | ❌ |
| **Settings** |
| Company Settings | ✅ | ✅ | ❌ | ❌ | ❌ |
| Bank Accounts | ✅ | ✅ | ❌ | ❌ | ❌ |
| **Master Data** |
| COA CRUD | ✅ | ✅ | ✅ | ❌ | ❌ |
| Template CRUD | ✅ | ✅ | ✅ | ❌ | ❌ |
| Client CRUD | ✅ | ✅ | ✅ | ✅ | ❌ |
| Project CRUD | ✅ | ✅ | ✅ | ✅ | ❌ |
| **Transactions** |
| Create Transaction | ✅ | ✅ | ✅ | ✅ | ❌ |
| Post Transaction | ✅ | ✅ | ✅ | ❌ | ❌ |
| Void Transaction | ✅ | ✅ | ✅ | ❌ | ❌ |
| Manual Journal Entry | ✅ | ✅ | ✅ | ❌ | ❌ |
| **Invoices** |
| Create/Edit Invoice | ✅ | ✅ | ✅ | ✅ | ❌ |
| Mark Invoice Paid | ✅ | ✅ | ✅ | ❌ | ❌ |
| **Reports** |
| View Dashboard | ✅ | ✅ | ✅ | ✅ | ✅ |
| View Reports | ✅ | ✅ | ✅ | ✅ | ✅ |
| Export PDF/Excel | ✅ | ✅ | ✅ | ✅ | ✅ |

#### Database Schema

```sql
-- V019: User roles and permissions
CREATE TABLE roles (
    id UUID PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,  -- ADMIN, OWNER, ACCOUNTANT, STAFF, AUDITOR
    name VARCHAR(100) NOT NULL,         -- Indonesian display name
    description TEXT,
    is_system BOOLEAN DEFAULT FALSE,    -- System roles cannot be deleted
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE permissions (
    id UUID PRIMARY KEY,
    code VARCHAR(100) NOT NULL UNIQUE,  -- e.g., 'user.create', 'transaction.post'
    name VARCHAR(100) NOT NULL,
    category VARCHAR(50) NOT NULL,      -- system, settings, master, transaction, invoice, report
    description TEXT,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE role_permissions (
    role_id UUID REFERENCES roles(id),
    permission_id UUID REFERENCES permissions(id),
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE user_roles (
    user_id UUID REFERENCES users(id),
    role_id UUID REFERENCES roles(id),
    PRIMARY KEY (user_id, role_id)
);
```

#### Features

##### User Management
- [ ] User entity enhancements (link to roles)
- [ ] User CRUD UI (create, edit, activate/deactivate)
- [ ] User list with filters (role, active status)
- [ ] Password reset by admin
- [ ] User cannot delete own account
- [ ] At least one ADMIN must exist

##### Role Management
- [ ] Role entity with permission links
- [ ] Seed default roles (ADMIN, OWNER, ACCOUNTANT, STAFF, AUDITOR)
- [ ] Role assignment UI (assign roles to users)
- [ ] View role permissions (read-only for system roles)
- [ ] Custom roles (optional, for flexibility)

##### Authorization
- [ ] Update UserDetailsServiceImpl to load roles/permissions
- [ ] Create PermissionEvaluator for SpEL expressions
- [ ] Add @PreAuthorize annotations to controllers
- [ ] Menu visibility based on permissions
- [ ] Button/action visibility based on permissions
- [ ] API-level permission checks

##### Audit
- [ ] Log permission denied attempts
- [ ] Log user management actions (create, role change, deactivate)

#### Implementation Notes

1. **STAFF workflow:** Creates draft transactions → ACCOUNTANT reviews and posts
2. **AUDITOR access:** Read-only, useful for external tax consultants during audit
3. **OWNER vs ADMIN:** Business owner shouldn't need technical admin access
4. **Migration path:** Existing admin user gets ADMIN + OWNER roles

**Deliverable:** Automated reconciliation for bank and marketplace transactions, flexible transaction tagging, trend analysis, smart alerts, optimized balance calculations, and role-based access control

---

## Phase 5: Assets & Budget

**Goal:** Fixed asset tracking and budget management

### 5.1 Fixed Asset Register
- [ ] Fixed asset entity
- [ ] Asset categories
- [ ] Asset CRUD UI
- [ ] Purchase recording

```sql
-- V020: Fixed assets
fixed_assets
```

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

```sql
-- V021: Budgets
budgets
```

### 5.5 Budget Reports
- [ ] Budget vs Actual report
- [ ] Variance analysis
- [ ] Over-budget highlighting
- [ ] PDF/Excel export

**Deliverable:** Asset management and budget tracking

---

## Phase 6+: Future Enhancements

### Additional Industry Templates
- [ ] Photography COA and journal templates
- [ ] Online Seller COA and journal templates
- [ ] General Freelancer COA and journal templates
- [ ] Industry-specific salary component templates

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

### Migration Naming
```
V{sequence}__{description}.sql
V001__create_users_table.sql
V002__create_chart_of_accounts.sql
V003__create_journal_templates.sql
```

### Adding Features Without Breaking Data

**Example: Adding tags to existing templates**

```sql
-- V007__add_template_tags.sql
-- Step 1: Create new table (no impact on existing data)
CREATE TABLE journal_template_tags (
    id UUID PRIMARY KEY,
    template_id UUID NOT NULL REFERENCES journal_templates(id),
    tag VARCHAR(100) NOT NULL,
    created_at TIMESTAMP DEFAULT NOW()
);

-- Step 2: Create index
CREATE INDEX idx_template_tags_template ON journal_template_tags(template_id);

-- No data migration needed - existing templates just have no tags
-- Application handles empty tags gracefully
```

**Example: Adding new column to existing table**

```sql
-- V009__add_fiscal_periods.sql
-- New table, no impact
CREATE TABLE fiscal_periods (...);

-- V010__add_period_status_to_transactions.sql
-- Add nullable column first
ALTER TABLE transactions
ADD COLUMN fiscal_period_id UUID REFERENCES fiscal_periods(id);

-- Backfill in application or separate migration
-- UPDATE transactions SET fiscal_period_id = ... WHERE fiscal_period_id IS NULL;
```

---

## Deployment Checklist

### Pre-Production
- [ ] All migrations tested on copy of production data
- [ ] Rollback scripts verified
- [ ] Backup taken before deployment
- [ ] Feature flags configured
- [ ] Monitoring alerts set up

### Production Deployment
1. Take database backup
2. Enable maintenance mode (if needed)
3. Run database migrations
4. Deploy new application version
5. Verify application starts
6. Run smoke tests
7. Disable maintenance mode
8. Monitor for errors

### Rollback Plan
1. If migration failed: restore from backup
2. If application failed: redeploy previous version
3. If data issue: run corrective migration

---

## Testing Strategy

### Per Phase
- [ ] Unit tests for business logic
- [ ] Integration tests for database operations
- [ ] Functional tests (Playwright) for critical paths
- [ ] Migration tests on sample data

### Critical Paths to Test
1. Transaction creation and posting
2. Journal entry balance validation
3. Report generation
4. User authentication
5. Period locking enforcement

---

## Go-Live Criteria

### MVP (Phase 1) Go-Live
- [ ] All Phase 1 features completed
- [ ] No critical bugs
- [ ] Performance acceptable (< 2s page load)
- [ ] Backup/restore tested
- [ ] Production environment ready
- [ ] Monitoring in place
- [ ] Support process defined

### Production Readiness
- [ ] Security review completed
- [ ] Data retention policy implemented
- [ ] User documentation ready
- [ ] Admin can manage users
- [ ] Can export all data (regulatory compliance)
