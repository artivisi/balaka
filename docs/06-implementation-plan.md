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
| **1** | Core Accounting (MVP) - IT Services | 🔄 In Progress |
| **2** | Tax Compliance | ⏳ Not Started |
| **3** | Reconciliation | ⏳ Not Started |
| **4** | Payroll | ⏳ Not Started |
| **5** | Assets & Budget | ⏳ Not Started |
| **6+** | Other Industries, Advanced Features | ⏳ Not Started |

---

## Phase 0: Project Setup ✅

### 0.1 Development Environment
- [x] Spring Boot 4.0 project structure
- [x] PostgreSQL 17 local setup (Testcontainers for tests)
- [x] Flyway migration setup
- [x] Basic CI/CD pipeline (GitHub Actions)

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

## Phase 1: Core Accounting (MVP)

**Goal:** Minimal viable accounting system to go live

**Scope:** IT Services industry only (primary use case for initial deployment)

### Implementation Order Rationale

The features are ordered to maximize code reuse and enable incremental validation:

```
1.1 COA ✅ → 1.2 Journal Entries ✅ → 1.3 Reports → 1.4 Templates → 1.5 Transactions
                    │                      │              │               │
                    │                      │              │               │
                    └── Core service ──────┴── Validates ─┴── Generates ──┘
                        reused by all         the engine     journal entries

                    │                                                      │
                    ├─────────────────────────────────────→ 1.9 Amortization Schedules
                    │                                          (auto-generates adjustments)
                    │
                    └─────────────────────────────────────→ 1.10 Project Tracking
                                                               (profitability analysis)
```

- **Journal Entries first:** Core double-entry engine. Users who understand accounting can use immediately.
- **Reports second:** Validates journal entries work correctly. Trial Balance = ultimate double-entry test.
- **Templates third:** Recipes that generate journal entries. Reuses JournalEntryService.
- **Transactions fourth:** User-friendly abstraction. Reuses TemplateExecutionEngine → JournalEntryService.
- **Amortization Schedules:** Automates period-end adjustments. Reuses JournalEntryService directly.
- **Project Tracking:** Tags transactions by project. Reuses AccountBalanceCalculator for profitability.

---

### 1.1 Chart of Accounts ✅
- [x] Account entity and repository
- [x] Account types (asset, liability, equity, revenue, expense)
- [x] Hierarchical structure (parent/child)
- [x] Pre-seeded COA template: **IT Services only**
- [x] Soft delete (base entity with deleted_at, @SQLRestriction filter)
- [x] Account CRUD UI
- [x] Account activation/deactivation

**Note:** Other industry templates (Photography, Online Seller, General Freelancer) deferred to Phase 6+

```sql
-- V002: Chart of accounts
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

### 1.3 Basic Reports

**Purpose:** Validate journal entries, provide financial output. Trial Balance is the ultimate test of double-entry correctness.

**Dependencies:** Journal Entries (1.2)

**Reused by:** Account balance display, validation checks

#### Financial Statements
- [ ] Trial Balance report
- [ ] General Ledger report (all entries per account)
- [ ] Balance Sheet (Laporan Posisi Keuangan)
- [ ] Income Statement (Laporan Laba Rugi)
- [ ] Date range filtering
- [ ] PDF export
- [ ] Excel export

#### Dashboard KPIs
- [ ] Revenue (current month, vs previous month %)
- [ ] Expenses (current month, vs previous month %)
- [ ] Net Profit (current month, vs previous month %)
- [ ] Profit Margin % (current month, vs previous month pts)
- [ ] Cash Balance (sum of cash/bank accounts)
- [ ] Receivables Total (Piutang Usaha balance)
- [ ] Payables Total (Hutang Usaha balance)

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

### 1.4 Journal Templates (Basic)

**Purpose:** Predefined recipes for common transactions. Generates journal entries automatically.

**Dependencies:** COA (1.1), JournalEntryService (1.2)

**Reused by:** Transactions (1.5)

- [ ] Template entity with versioning
- [ ] Template lines entity (account mappings, debit/credit rules)
- [ ] Category field (income, expense, payment, receipt, transfer)
- [ ] Cash flow category field (operating, investing, financing)
- [ ] System templates for IT Services (preloaded via migration)
- [ ] Template CRUD UI
- [ ] Template list with category filter
- [ ] Template detail view
- [ ] Template execution (generates journal entry)

```sql
-- V004: Journal templates
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

### 1.5 Transactions

**Purpose:** User-friendly abstraction over templates. Non-accountants select a template, fill in amounts.

**Dependencies:** Templates (1.4), JournalEntryService (1.2)

- [ ] Transaction entity with type and numbering
- [ ] Transaction sequences per type (auto-increment per category)
- [ ] Status workflow (draft → posted → void)
- [ ] Transaction form UI (driven by template structure)
- [ ] Account mapping from template
- [ ] Transaction list with filters (date, type, status)
- [ ] Transaction detail view
- [ ] Post transaction (executes template → creates journal entry)
- [ ] Void transaction (voids linked journal entry)

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

### 1.6 Formula Support (Optional for MVP)

**Purpose:** Enable percentage calculations in templates (e.g., 11% PPN).

**Dependencies:** Templates (1.4)

**Note:** Basic templates with fixed amounts work without formulas. Implement when tax templates needed (Phase 2).

- [ ] SpEL integration with SimpleEvaluationContext
- [ ] FormulaContext class for transaction data
- [ ] Percentage calculations (100%, 11%, etc.)
- [ ] Simple arithmetic expressions
- [ ] Formula validation on template save

---

### 1.7 Account Balances (Materialized) - Performance Optimization

**Purpose:** Cache account balances for faster report generation.

**Dependencies:** Journal Entries (1.2), Reports (1.3)

**Note:** Defer until performance requires it. On-the-fly calculation sufficient for MVP.

- [ ] Account balances entity
- [ ] Balance update on journal entry post/void
- [ ] Period-based aggregation (monthly snapshots)
- [ ] Balance recalculation utility (rebuild from journal entries)

```sql
-- V006: Account balances (when needed)
account_balances (id, account_id, period_start, period_end, opening_balance, debit_total, credit_total, closing_balance, ...)
```

---

### 1.8 Template Enhancements

**Purpose:** Improve template discoverability and user experience.

**Dependencies:** Templates (1.4)

- [ ] Template tags
- [ ] User favorites
- [ ] Usage tracking (last used, frequency)
- [ ] Search functionality
- [ ] Recently used list

```sql
-- V007: Template preferences
journal_template_tags (id, template_id, tag, ...)
user_template_preferences (id, user_id, template_id, is_favorite, last_used_at, use_count, ...)
```

---

### 1.9 Amortization Schedules

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
- [ ] Amortization schedule entity
- [ ] Amortization entries entity (tracks each period)
- [ ] Schedule CRUD UI
- [ ] Schedule list with filters (type, status)
- [ ] Manual schedule creation (user-initiated, no auto-detection)
- [ ] Auto-post toggle per schedule (user chooses during creation)
- [ ] Monthly batch job (generates journal entries)
- [ ] Period-end dashboard integration
- [ ] Remaining balance display
- [ ] Schedule completion handling
- [ ] Rounding handling (last period absorbs difference)

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

### 1.10 Project Tracking

**Purpose:** Track profitability per project/job for service businesses.

**Dependencies:** COA (1.1), JournalEntryService (1.2), Transactions (1.5)

**Note:** Decision #7 - Critical for IT Services and Photographers. Simple tagging approach, not full project management.

#### Project Features
- [ ] Project entity (code, name, client_id, status, budget)
- [ ] Project CRUD UI
- [ ] Project list with filters (status, client)
- [ ] Link transactions to project (optional project_id on journal entries)
- [ ] Project selection in transaction form
- [ ] Project Profitability Report
- [ ] Project Income Statement (revenue - costs per project)

#### Client Features
- [ ] Client entity (code, name, contact info, notes)
- [ ] Client CRUD UI
- [ ] Client list with search
- [ ] Link projects to client
- [ ] Client Profitability Report (aggregate of all client projects)
- [ ] Client Revenue Ranking (top clients by revenue)

#### Project Milestones
- [ ] Milestone entity (name, target date, completion %, status)
- [ ] Milestone CRUD UI (inline in project form)
- [ ] Milestone status tracking (pending, in_progress, completed)
- [ ] Milestone progress calculation (weighted by completion %)
- [ ] Milestone overdue detection

#### Payment Terms & Invoices
- [ ] Payment term entity (name, %, trigger, linked milestone)
- [ ] Payment term CRUD UI (inline in project form)
- [ ] Invoice entity (basic: number, date, amount, status)
- [ ] Invoice generation from payment term
- [ ] Invoice status tracking (draft, sent, paid, overdue)
- [ ] Link invoice to payment term
- [ ] Auto-trigger revenue recognition on milestone completion

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

**Deliverable:** Working accounting system - can record journal entries manually or via templates, generate reports, automate period-end adjustments, track project/client profitability with milestones and payment terms

**Note:** Document attachment deferred to Phase 2. Store receipts in external folder during MVP.

### MVP Checklist for Go Live
- [ ] Can create manual journal entries (for accountants)
- [ ] Can create transactions using templates (for business users)
- [ ] Trial Balance balances (validates double-entry correctness)
- [ ] Can generate Balance Sheet and Income Statement
- [ ] Can export reports to PDF/Excel
- [ ] Dashboard shows KPIs (revenue, expenses, profit, cash, receivables, payables)
- [ ] Can set up amortization schedules for prepaid/unearned items
- [ ] Period-end adjustments auto-generated from schedules
- [ ] Can create and manage clients
- [ ] Can create projects with milestones and payment terms
- [ ] Can generate invoices from payment terms
- [ ] Milestone completion triggers revenue recognition
- [ ] Cost overrun detection (% spent vs % complete)
- [ ] Can generate Project Profitability Report
- [ ] Can generate Client Profitability Report
- [ ] Basic user management
- [ ] Database backup via pg_dump (no documents yet)
- [ ] Production deployment tested

### Code Reuse Summary

| Component | Created In | Reused By |
|-----------|------------|-----------|
| JournalEntryService | 1.2 | 1.3, 1.4, 1.5, 1.9, 1.10 |
| AccountBalanceCalculator | 1.3 | 1.4 (validation), 1.5 (display), 1.9 (remaining balance), 1.10 (profitability) |
| TemplateExecutionEngine | 1.4 | 1.5 |
| ChartOfAccountRepository | 1.1 | All subsequent features |
| AmortizationScheduleService | 1.9 | Period-end dashboard |
| ProjectService | 1.10 | Transaction form, Profitability reports |

---

## Phase 2: Tax Compliance

**Goal:** Indonesian tax features (PPN, PPh) + document attachment

### 2.0 Infrastructure (Deferred from Phase 0)
- [ ] Local storage directory setup

### 2.1 Document Attachment
- [ ] Document entity
- [ ] Local filesystem storage
- [ ] File upload UI (single file)
- [ ] Attach to transaction
- [ ] View/download document
- [ ] Thumbnail generation (images)

```sql
-- V008: Documents
documents
```

### 2.2 Tax Accounts Setup
- [ ] Pre-configured tax accounts in COA templates
- [ ] PPN Masukan / Keluaran accounts
- [ ] PPh 21, 23, 4(2), 25, 29 accounts

### 2.3 PPN Templates
- [ ] Penjualan + PPN Keluaran template
- [ ] Pembelian + PPN Masukan template
- [ ] PPN calculation (11%)
- [ ] Non-PKP templates (no PPN)

### 2.4 PPh Templates
- [ ] PPh 23 withholding templates (2%)
- [ ] PPh 4(2) templates
- [ ] Conditional formulas for thresholds

### 2.5 Tax Reports
- [ ] PPN Summary Report
- [ ] PPN Detail (Keluaran/Masukan)
- [ ] PPh 23 Withholding Report
- [ ] e-Faktur CSV export format
- [ ] e-Bupot export format

### 2.6 Fiscal Period Management
- [ ] Fiscal periods entity
- [ ] Period status (open, month_closed, tax_filed)
- [ ] Soft lock on month close
- [ ] Hard lock after tax filing
- [ ] Period close workflow

```sql
-- V009: Fiscal periods
fiscal_periods
```

### 2.7 Tax Calendar
- [ ] Tax deadline configuration
- [ ] Dashboard reminders
- [ ] Monthly checklist

### 2.8 Backup & Restore Utility
- [ ] Backup service (database + documents)
- [ ] Coordinated backup (consistent state between DB and files)
- [ ] Backup to local directory
- [ ] Restore utility with validation
- [ ] Backup scheduling (manual trigger for MVP)
- [ ] Backup manifest (metadata, timestamp, file list)

### 2.9 Transaction Tags

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
-- V010: Transaction tags
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

### 2.10 Trend Analysis

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

### 2.11 Smart Alerts

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
-- V011: Smart alerts
alert_configurations (id, alert_type, enabled, threshold_value,
    notify_dashboard, notify_email, created_at, updated_at)

alert_history (id, alert_type, entity_type, entity_id,
    alert_data, acknowledged, acknowledged_by, acknowledged_at, created_at)
```

**Deliverable:** Tax-compliant accounting with export formats for DJP, document storage, proper backup/restore, flexible transaction tagging, trend analysis, and smart alerts

---

## Phase 3: Reconciliation

**Goal:** Bank and marketplace reconciliation

### 3.1 Bank Parser Infrastructure
- [ ] Bank parser config entity
- [ ] ConfigurableBankStatementParser class
- [ ] Column name matching with fallback
- [ ] Preload configs (BCA, BNI, BSI, CIMB)
- [ ] Admin UI for parser config

```sql
-- V010: Bank parser configs
bank_parser_configs
```

### 3.2 Bank Reconciliation
- [ ] Bank reconciliation entity
- [ ] Statement items entity
- [ ] CSV upload and parsing
- [ ] Auto-matching (exact date + amount)
- [ ] Fuzzy matching (±1 day)
- [ ] Manual matching UI
- [ ] Create missing transactions from statement
- [ ] Reconciliation report

```sql
-- V011: Bank reconciliation
bank_reconciliations
bank_statement_items
```

### 3.3 Marketplace Parser Infrastructure
- [ ] Marketplace parser config entity
- [ ] ConfigurableMarketplaceParser class
- [ ] Preload configs (Tokopedia, Shopee, Bukalapak, Lazada)

```sql
-- V012: Marketplace parser configs
marketplace_parser_configs
```

### 3.4 Marketplace Reconciliation
- [ ] Settlement upload and parsing
- [ ] Order matching
- [ ] Fee expense auto-creation
- [ ] Marketplace reconciliation report

### 3.5 Cash Flow Statement
- [ ] Cash flow report generation
- [ ] Group by cash_flow_category from templates
- [ ] Operating/Investing/Financing sections
- [ ] PDF/Excel export

**Deliverable:** Automated reconciliation for bank and marketplace transactions

---

## Phase 4: Payroll

**Goal:** Full payroll with PPh 21 and BPJS

### 4.1 Employee Management
- [ ] Employee entity
- [ ] Employee CRUD UI
- [ ] PTKP status configuration
- [ ] NPWP validation

```sql
-- V013: Employees
employees
```

### 4.2 Salary Components
- [ ] Salary component entity
- [ ] Component types (gaji pokok, tunjangan, BPJS, etc.)
- [ ] Preloaded component templates for IT Services
- [ ] Employee salary configuration UI

```sql
-- V014: Salary components
salary_components
```

### 4.3 BPJS Calculation
- [ ] BPJS Kesehatan rates (4% + 1%)
- [ ] BPJS Ketenagakerjaan rates (JKK, JKM, JHT, JP)
- [ ] Company vs employee portion
- [ ] Auto-calculation service

### 4.4 PPh 21 Calculation
- [ ] Progressive tax rates (5%-35%)
- [ ] PTKP deduction by status
- [ ] Biaya jabatan (5%, max 500rb)
- [ ] Monthly vs annual calculation
- [ ] PPh 21 calculator service

### 4.5 Payroll Processing
- [ ] Payroll run entity
- [ ] Payroll details entity
- [ ] Monthly payroll workflow
- [ ] Calculate all employees
- [ ] Review and adjust
- [ ] Post to journal entries
- [ ] Generate payslips

```sql
-- V015: Payroll
payroll_runs
payroll_details
```

### 4.6 Payroll Reports
- [ ] Payroll summary report
- [ ] PPh 21 monthly report
- [ ] BPJS report
- [ ] Payslip PDF generation

**Deliverable:** Complete payroll system with tax compliance

---

## Phase 5: Assets & Budget

**Goal:** Fixed asset tracking and budget management

### 5.1 Fixed Asset Register
- [ ] Fixed asset entity
- [ ] Asset categories
- [ ] Asset CRUD UI
- [ ] Purchase recording

```sql
-- V016: Fixed assets
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
-- V017: Budgets
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
