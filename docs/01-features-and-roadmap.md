# Features and Roadmap

## Overview

Indonesian accounting application for small businesses. Spring Boot 4.0 + Thymeleaf + PostgreSQL with single-tenant architecture.

## Target Market

### Primary Users
- Small businesses in Indonesia
- Sole proprietors
- Freelancers
- Junior accountants / fresh graduates
- Bookkeepers serving multiple clients

### Target Segments

| Segment | Status | Notes |
|---------|--------|-------|
| IT Services / Consulting | ✅ Supported | Project tracking, milestone billing |
| Online Sellers (Marketplace) | ✅ Supported | Inventory, COGS, multi-channel sales |
| Manufacturing (Coffee/F&B) | ✅ Supported | BOM, production orders, cost accumulation |
| Education (Universities) | ✅ Supported | Student billing, scholarships, receivables |
| Corporate Training | ✅ Supported | Project-based accounting |
| Photography / Videography | ✅ Supported | Equipment tracking, event-based billing |

### Not Targeted (Near-Term)
- Universities with grant/fund accounting (requires fund dimension)
- Complex manufacturing with WIP tracking (current: simple BOM-based)
- Multi-currency operations
- Advanced marketplace reconciliation (CSV parsing, automated fee extraction)

## Implemented Features

### Phase 0: Project Setup (Complete)
- Java 25 + Spring Boot 4.0 setup
- PostgreSQL with Flyway migrations
- Spring Security authentication
- CI/CD pipeline with GitHub Actions

### Phase 1: Core Accounting MVP (Complete)

**Chart of Accounts (COA)**
- Hierarchical account structure
- Indonesian account naming (Aset, Kewajiban, Ekuitas, Pendapatan, Beban)
- IT Services seed data template
- Header vs detail accounts

**Journal Template System**
- Template-based transaction entry
- User-defined templates (customizable)
- System templates (payroll, depreciation, year-end closing)
- Formula support with SpEL expressions
- Dynamic account mapping via `accountHint`
- Cash flow categorization (Operating, Investing, Financing)

**Double-Entry Bookkeeping**
- Transaction-centric architecture (all entries via transactions)
- Draft → Posted → Void workflow
- Auto-generated transaction numbers (TYPE-YYYY-seq)
- Void with audit trail (no hard deletes)

**Financial Reports**
- Trial Balance
- Income Statement (Laporan Laba Rugi)
- Balance Sheet (Laporan Posisi Keuangan)
- General Ledger
- Account Ledger

**Project Tracking**
- Project-based profitability
- Tag transactions by project
- Project income statements

**Dashboard KPIs**
- Revenue trends
- Expense breakdown
- Monthly comparisons

**Data Import/Export**
- COA import from JSON/Excel
- Template import from JSON
- Excel/PDF report exports

### Phase 2: Tax Compliance + Cash Flow (Complete)

**Indonesian Tax Compliance**
- PPN (VAT) tracking - Masukan/Keluaran
- PPh 21 calculation with progressive rates
- PPh 23 withholding tracking
- PPh 4(2) final tax handling
- e-Faktur data export format
- e-Bupot export format

**Fiscal Period Management**
- Monthly period status (Open, Month Closed, Tax Filed)
- Edit restrictions for closed periods
- Tax deadline tracking

**Tax Calendar**
- Monthly/quarterly/annual deadlines
- Upcoming deadline notifications

**Telegram Integration**
- Receipt photo upload via bot
- OCR processing with Google Cloud Vision
- Auto-transaction creation

**Backup & Restore**
- Automated daily local backup
- Backblaze B2 offsite backup
- Google Drive monthly archive
- Encrypted backup with GPG

**Cash Flow Statement**
- Operating/Investing/Financing categorization
- Auto-classification from templates

### Phase 3: Payroll + RBAC (Complete)

**Employee Management**
- Employee master data
- PTKP status handling
- Bank account info
- Active/inactive status

**Salary Components (17 Indonesian Components)**
- Gaji Pokok
- Tunjangan (Transport, Makan, Jabatan, etc.)
- BPJS Kesehatan (company + employee portions)
- BPJS Ketenagakerjaan (JKK, JKM, JHT, JP)
- PPh 21 deduction

**Payroll Processing**
- Monthly payroll runs
- Auto-calculation of BPJS and PPh 21
- Payslip generation
- Journal entry posting

**User Management & RBAC**
- 6 roles: SUPERADMIN, OWNER, ACCOUNTANT, BOOKKEEPER, EMPLOYEE, VIEWER
- Method-level security with Spring Security
- Granular authority-based permissions

**Employee Self-Service**
- View own payslips
- View tax documents (1721-A1)
- Limited access to own data only

### Phase 4: Fixed Assets (Complete)

**Fixed Asset Register**
- Asset categories with default settings
- Acquisition tracking
- Status management (Active, Fully Depreciated, Disposed)

**Depreciation**
- Straight-line method
- Declining balance method
- Monthly batch depreciation
- Auto journal entry via templates

**Asset Disposal**
- Sell, write-off, transfer workflows
- Gain/loss calculation
- Disposal journal entries

### Phase 5: Inventory & Production (Complete)

**Product Management**
- Product master with unit of measure (FIFO/Weighted Average)
- Product categories (hierarchical)
- Bill of Materials (BOM) for production

**Inventory Transactions**
- Purchase recording (stock in)
- Sale recording with auto-COGS
- Stock adjustments (in/out)
- Production in/out

**Stock Valuation**
- FIFO method with layer tracking
- Weighted average method
- Perpetual inventory with real-time balance

**Simple Production**
- BOM (Bill of Materials) definition
- Production orders with status workflow
- Material consumption on completion
- Finished goods receipt with cost accumulation

**Sales Integration**
- Auto-COGS calculation on sales posting
- Margin calculation per transaction
- Product profitability report

### Industry Seed Packs & User Manuals (Complete)

**Industry Seed Packs**
- IT Services (PT ArtiVisi Intermedia) - 75 COA, 37 templates, payroll components
- Online Seller (Toko Gadget Murah) - 80 COA, marketplace-specific accounts
- Manufacturing (Kedai Kopi Nusantara) - 90 COA, 33 templates, BOM products
- Education (STMIK Tazkia) - 87 COA, 31 templates, student billing

**User Manual (15 files, 12-section structure)**
- Setup & Administration
- Basic Accounting Tutorial (crown jewel)
- Fixed Assets & Depreciation
- Tax Compliance
- Payroll & BPJS
- Industry Overview
- Service Industry Guide
- Trading Industry Guide
- Manufacturing Industry Guide
- Education Industry Guide
- Security & Compliance
- 4 Appendices (Glossary, Templates, Amortization, Accounts)

**Functional Tests**
- Service: 24 tests (billing, payments, reports)
- Seller: 28 tests (inventory, sales, COGS)
- Manufacturing: 44 tests (BOM, production, costing)
- Campus: 19 tests (billing, scholarships, receivables)
- Total: 115 industry-specific functional tests

### Phase 6: Security Hardening (Complete)

**Data at Rest Encryption**
- Field-level AES-256-GCM encryption for PII
- Document storage encryption
- Database connection SSL

**Authentication Hardening**
- Password complexity (12+ chars, mixed requirements)
- Account lockout (5 attempts, 30-minute lockout)
- Rate limiting on login and API endpoints
- Session timeout (15 minutes)

**Input Validation & Output Encoding**
- Magic byte validation for file uploads
- XSS prevention
- Log injection prevention
- Generic error messages

**Security Audit Logging**
- Authentication events (login/logout/failed)
- User management operations
- Sensitive data access
- Document operations

**Data Protection**
- Data masking for sensitive fields in UI
- GDPR/UU PDP compliance (DSAR export, anonymization)
- Privacy policy page

**DevSecOps**
- CodeQL static analysis
- SonarCloud integration
- OWASP Dependency-Check
- ZAP DAST scanning

### Phase 7: API Foundation (Complete)

**REST API with OAuth 2.0 Device Flow**
- 16 API controllers (transactions, drafts, templates, analysis, payroll, tax, bank reconciliation, bills, data import, documents)
- OAuth 2.0 Device Flow authentication (RFC 8628)
- Pagination support
- Device token management UI
- OpenAPI/Swagger documentation (springdoc-openapi)

### Phase 8: Bank Reconciliation (Complete)

- Bank statement CSV import with configurable parsers
- Auto-matching (3-pass: exact, fuzzy date, keyword)
- Manual matching UI
- Create transactions from unmatched bank items
- Reconciliation reports

### Phase 9: Analytics & Insights (Complete)

- Smart alerts (cash low, overdue receivables, expense spikes, project cost overrun, client concentration)
- Transaction tags for flexible categorization
- Tag-based reports
- AI analysis reports with per-industry KPIs

### Phase 10: Invoice & Bill Management (Complete)

- Customer invoices (create, send, track payments, aging)
- Vendor bills with API (create, approve, mark paid)
- Payment tracking
- Aging reports
- Customer/vendor statements

### Phase 11: Recurring Transactions (Complete)

- Recurring transaction templates
- Configurable scheduling
- Auto-posting

### Phase 12: Tax Data Management (Complete)

- Tax detail entry UI (e-Faktur, e-Bupot fields)
- Tax detail & document API
- Auto-populate tax details from transaction data
- Client management UI
- Fiscal period management
- Tax export API (e-Faktur, Bukti Potong, PPN/PPh reports, rekonsiliasi fiskal, PPh Badan)

### Phase 13: OpenAPI Migration (Complete)

- springdoc-openapi 3.0.1 integration
- 16 @Tag API controllers
- AI extensions (x-authentication, x-workflows, x-csv-files, x-industries, x-error-codes)
- Swagger UI at /swagger-ui.html

### Phase 14: Fiscal Adjustments API (Complete)

- CRUD API for fiscal adjustment entries (koreksi fiskal)

### Phase 15: Payroll API + PPh 21 (Complete)

- Employee API (CRUD, salary component assignment)
- Salary component API (CRUD)
- Payroll run API (create, calculate PPh 21, approve, post)
- 1721-A1 generation per employee
- Annual PPh 21 summary

### Phase 16: User Manual Revamp (Complete)

- Full AI-operated lifecycle documentation (installation, data migration, daily operations, reporting, tax filing)

## Planned Features

No planned phases. Future enhancements are implemented on-demand when client needs arise. See `docs/06-implementation-plan.md` "Future Enhancements" section.

## Multi-Industry Expansion Strategy

### Architecture Vision

The accounting system serves as a generic core that industry-specific applications can integrate with:

```
Domain App (Grant Mgmt, POS, etc.)
        │
        │ POST /api/transactions
        ▼
Core Accounting API
├── COA Management
├── Journal Templates
├── Template Execution
└── Reports
```

### Integration Pattern: Store and Forward (SAF)

Domain apps call accounting API with:
1. Validate business rules locally
2. Call accounting API to create journal entries
3. Handle failures with retry/idempotency

```json
POST /api/transactions
{
    "idempotencyKey": "domain-app-expense-123",
    "templateCode": "EXPENSE-CASH",
    "transactionDate": "2024-12-03",
    "amount": 50000000,
    "variables": {
        "expenseAccount": "5.1.01",
        "cashAccount": "1.1.01"
    }
}
```

### Domain Apps That Can Integrate

| Domain | Features | Integration |
|--------|----------|-------------|
| Grant Management | Fund tracking, compliance, budgets | API calls for journal entries |
| Inventory (Advanced) | COGS, warehouse management | Built-in module (Phase 5) |
| POS | Sales, receipts, daily settlement | API calls |
| Real Estate | Property, tenants, leases | API calls |
| Manufacturing | BOM, work orders, production costing | API calls |

### What Stays in Core

| Feature | Notes |
|---------|-------|
| COA Management | UI + API |
| Journal Templates | UI + API |
| Template Execution | API primary |
| Basic Reports | UI + API |
| Payroll (Indonesian) | Built-in module |
| Tax Compliance (Indonesian) | Built-in module |
| Fixed Assets | Built-in module |
| Inventory (Simple) | Built-in module (Phase 5) |

### Why Inventory/Production is Built-in

Unlike fund accounting, inventory transactions fit the existing journal template model:

```
Purchase raw materials:
  Dr Raw Materials Inventory    Rp 500,000
  Cr Cash                       Rp 500,000

Sale with COGS:
  Dr Cash                       Rp 600,000
  Cr Sales Revenue              Rp 600,000

  Dr COGS                       Rp 400,000
  Cr Finished Goods             Rp 400,000
```

No new dimension needed on journal entries. Standard debit/credit flows.

### Why Fund Accounting Requires Separate App

Fund accounting requires tracking two dimensions per entry:
- Natural account (Equipment Expense)
- Fund (NSF Grant 2024-001)

Current model has single `account_id` per journal entry. Adding `fund_id` would:
- Pollute generic model
- Add complexity for non-fund users
- Require fund balance tracking
- Need restriction validation logic

Correct approach: Separate Grant Management app owns fund logic, calls Accounting API for journal recording.

## Success Criteria

### For Business Owners
- Complete monthly bookkeeping without accounting knowledge
- Tax reports generated automatically
- Clear business financial visibility

### For Bookkeepers
- Manage 5-10 clients efficiently
- Fast data entry with templates
- Clear task lists and reminders

### For Technical Operations
- Single-tenant isolation per company
- Automated backup with 3-2-1 strategy
- < 4 hour disaster recovery
