# Requirements & Feature Specification

## Target Market

### Primary Users
- Small businesses in Indonesia
- Sole proprietors
- Freelancers

### Specific Target Segments

#### Initial User (Own Business)
- Software development projects
- Corporate training services
- IT consulting services

#### Prospective Client Segments

**Wedding Photography/Videography:**
- Freelance photographers/videographers
- Small studios employing multiple freelancers
- Project-based billing (per event)
- Equipment investment tracking
- Client deposits and milestone payments

**Home-Based Online Sellers:**
- Selling via marketplace platforms (Tokopedia, Shopee, Bukalapak)
- Social media commerce (Instagram, Facebook, WhatsApp)
- Simple buy-and-sell model (no manufacturing)
- Managing multiple sales channels
- COD and online payment reconciliation

#### Explicitly NOT Targeted (Near-Term)
- Home production businesses (cake making, clothing manufacturing, crafts)
- Manufacturing with inventory assembly/production
- Businesses with complex inventory costing (BOM, work-in-process)

### User Characteristics
- Minimal accounting/finance/tax knowledge
- May employ junior accounting staff (fresh graduates)
- One bookkeeper may serve multiple clients
- 100% Indonesian users

### Key Constraints
- Limited budget for accounting software
- Need simple, guided workflows
- Require extensive reporting and analysis
- Must handle Indonesian tax compliance seamlessly

## Core Features

### 1. Simplified Transaction Entry
- Template-based transactions for common business scenarios
- Smart categorization with suggestions
- Receipt/invoice photo upload with OCR
- Bulk import from Excel/CSV
- Recurring transaction automation
- Business language interface (not accounting jargon)
- Attach multiple supporting documents per transaction

### 2. Indonesian Tax Compliance

#### PPh (Pajak Penghasilan)
- PPh 21 calculation (employee withholding)
- PPh 23 tracking (services received)
- PPh 4(2) final tax
- PPh Pasal 25 (monthly installments)
- Form 1770/1770S generation for sole proprietors

#### PPN (VAT)
- PPN input/output tracking
- SPT Masa PPN (monthly VAT return)
- e-Faktur integration preparation
- PKP vs non-PKP handling

#### Other Tax Features
- NPWP management
- Tax calendar with reminders (monthly, quarterly, annual deadlines)
- Automated tax calculations from transactions

### 3. Multi-Client Dashboard
- Easy switching between client companies
- Consolidated view across multiple clients
- Task management per client
- Permission levels: Owner, Operator, Power Operator, Viewer, Auditor

### 4. Reports & Analysis

#### Financial Reports (Indonesian Format)
- Laporan Laba Rugi (Income Statement)
- Neraca (Balance Sheet)
- Arus Kas (Cash Flow Statement)
- Buku Besar (General Ledger)
- All reports in Indonesian terminology

#### Analysis Tools
- Revenue trends
- Expense breakdown by category
- Profit margin analysis
- Budget vs actual comparison
- Tax burden analysis

#### Export Capabilities
- PDF, Excel, CSV formats
- Tax consultant review-ready exports

### 5. Guided Workflows
- Onboarding wizard (company setup)
- Chart of accounts templates by industry
- Monthly closing checklist
- Tax filing reminders with step-by-step guides
- Data validation warnings (non-blocking)
- Undo/edit capabilities

### 6. Indonesian-Specific Features
- Rupiah currency with proper formatting
- Indonesian fiscal year (January-December)
- Faktur Pajak (tax invoice) numbering
- Chart of accounts templates for specific business types:
  - IT Services / Consulting
  - Photography / Videography Services
  - Online Seller / Marketplace
  - General Services (Freelancer)
- Bahasa Indonesia throughout the interface

### 7. Segment-Specific Features

#### For Service Businesses (IT, Consulting, Photography)
- **Project/Job Costing:**
  - Tag transactions by project/client
  - Track project profitability
  - Project-based income statements
- **Milestone Billing:**
  - Down payment tracking
  - Progress billing
  - Retention tracking
- **Equipment/Asset Tracking:**
  - Photography equipment register
  - IT equipment inventory
  - Depreciation tracking

#### For Online Sellers
- **Simple Inventory Tracking:**
  - Buy/sell model (no production)
  - Stock quantity tracking
  - COGS calculation (FIFO or Average)
- **Multi-Channel Sales:**
  - Tag sales by platform (Tokopedia, Shopee, Instagram, etc.)
  - Platform fee tracking
  - Shipping cost allocation
- **Payment Method Tracking:**
  - COD reconciliation
  - E-wallet settlements (GoPay, OVO, DANA)
  - Bank transfer tracking

### 8. Document Management & Audit Trail

#### Supporting Document Storage
- **Upload & Attachment:**
  - Attach receipts, invoices, contracts to transactions
  - Multiple files per transaction (photos, PDFs, scans)
  - Drag-and-drop upload interface
  - Mobile photo capture support

- **Storage Strategy:**
  - Cloud storage (S3, GCS, or equivalent)
  - Organized by tenant → fiscal year → month → transaction
  - File size limits and compression
  - Supported formats: JPG, PNG, PDF, Excel, Word

- **Document Retention:**
  - Configurable retention policy per tenant
  - Default: 10 years (Indonesian tax audit requirement)
  - Automatic archival after retention period
  - Download archive before deletion

- **Document Viewing:**
  - In-app preview (images, PDFs)
  - Thumbnail views in transaction lists
  - Full-screen viewer with zoom
  - Download original files

#### Audit Features
- **Audit Trail:**
  - Complete history of all data changes
  - Who changed what, when, and why
  - Before/after values for all edits
  - Cannot be deleted or modified
  - Searchable and filterable

- **Auditor Role & Access:**
  - Read-only access to all financial data
  - View all transactions and supporting documents
  - Access to complete audit trail
  - Export capabilities for audit work papers
  - Time-bound access (grant access for audit period only)
  - No ability to modify any data

- **Audit-Ready Reports:**
  - Trial balance with supporting schedules
  - General ledger with document references
  - Transaction detail reports with attached documents
  - Account reconciliation reports
  - Period-to-period comparison reports
  - Export package for external auditors (PDF/Excel bundle)

- **Compliance & Security:**
  - Document access logging (who viewed what, when)
  - Secure sharing links with expiration
  - Password-protected exports
  - Digital signature support for finalized reports
  - E-meterai integration for official documents

## Non-Features (Not Planned Near-Term)
- Bank integration / automatic bank feeds
- Payroll processing (may record as expenses only)
- Complex manufacturing inventory (BOM, WIP, assembly)
- Production costing
- Mobile app (Phase 3 consideration)
- Multi-currency support

## Competitive Differentiators

1. **Tax Automation** - Automatic calculation of all Indonesian taxes from transactions
2. **Junior-Friendly** - Guided workflows, validation, intelligent suggestions
3. **Multi-Client Efficiency** - One bookkeeper can efficiently serve many clients
4. **Project-Based Accounting** - Built-in project/job costing for service businesses and freelancers
5. **Multi-Channel Support** - Handles online sellers across multiple marketplaces and social media
6. **Analysis Focus** - Not just compliance, but actionable business insights (project profitability, channel performance)
7. **Indonesian-Native** - Built specifically for Indonesian regulations, not translated

## Success Criteria

### For End Users (Business Owners)
- Can complete monthly bookkeeping without accounting knowledge
- Tax reports generated automatically and accurately
- Clear visibility into business financial health
- Confidence in compliance with Indonesian tax regulations

### For Operators (Bookkeepers)
- Can manage 5-10 clients efficiently
- Fast data entry with templates
- Clear task lists and reminders
- Easy switching between client contexts

### For Business (SaaS)
- Scalable multi-tenant architecture
- Low support burden (self-service capable)
- High user retention
- Clear upgrade path from personal use to team/multi-client
