# Decisions & Open Questions

## Decisions Made

### 1. Transaction-Centric Architecture ✓
**Decision:** Use transaction forms with journal templates instead of direct account-based entry

**Rationale:**
- Target users have minimal accounting knowledge
- Reduces data entry errors
- Maintains proper double-entry bookkeeping behind the scenes
- More intuitive for business owners

**Trade-offs:**
- More complex implementation
- Need to build template system
- Less direct control for power users (mitigated by manual journal entry option)

### 2. Journal Template Approach ✓
**Decision:** Implement configurable journal templates with preloaded defaults and power-user customization

**Rationale:**
- Flexibility for different business scenarios
- No code changes needed for new transaction types
- Users can adapt to their specific needs
- Handles simple to complex entries (one-to-many, many-to-many)

**Trade-offs:**
- More database complexity
- Template builder UI required for power users
- Template validation logic needed

### 3. Single-Tenant Architecture ✓
**Decision:** Instance-per-client deployment (not multi-tenant with tenant_id)

**Rationale:**
- Simpler codebase (no tenant_id in every query)
- Complete data and process isolation
- No risk of cross-tenant data leaks
- Easier compliance for accounting data
- Can co-locate multiple instances for cost efficiency
- Can isolate high-value clients on dedicated VPS

**Deployment Progression:**
1. **Phase 1 (MVP):** Single company, manual deployment, Docker Compose
2. **Phase 2 (Early SaaS):** Multi-instance single node, control plane for client management, 5-50 clients
3. **Phase 3 (Growth):** Node per client, Pulumi automation, 50+ clients with premium tiers

**Trade-offs:**
- Need control plane app for SaaS automation (Phase 2+)
- More containers/processes to manage
- Acceptable: Focus on product stability first (Phase 1), automate incrementally
- More testing scenarios

### 4. Indonesian Tax Focus ✓
**Decision:** Build specifically for Indonesian tax compliance, not generic multi-country

**Rationale:**
- Target market is 100% Indonesian
- Indonesian tax rules are specific and complex
- Generic solutions often inadequate for compliance
- Competitive differentiator

**Trade-offs:**
- Not suitable for international expansion without significant changes
- Hardcoded Indonesian assumptions

### 5. No Near-Term Bank Integration ✓
**Decision:** Defer bank integration to later phase

**Rationale:**
- Focus on core bookkeeping and tax compliance first
- Manual entry sufficient for target market initially
- Complex integration requirements
- Regulatory/security overhead

**Trade-offs:**
- More manual data entry
- Potential for errors in transaction entry
- Less competitive vs solutions with bank feeds

### 6. Target Market Segments ✓
**Decision:** Focus on service businesses and simple online sellers

**Segments:**
1. IT/Software consulting and training (own business)
2. Wedding photographers/videographers (freelance + small studios)
3. Home-based online sellers (marketplace + social media)

**Rationale:**
- Clear market focus based on accessible users
- Service-heavy businesses align with transaction-centric approach
- Online sellers need simple inventory (no manufacturing complexity)
- All segments benefit from project/job tracking

**Trade-offs:**
- Not suitable for manufacturing/production businesses
- Limited appeal to traditional retail (warung, toko)
- No complex inventory costing

### 7. Project/Job Costing Required ✓
**Decision:** Include project/job costing from Phase 1

**Rationale:**
- Critical for photographers (per-event profitability)
- Important for consultants/developers (per-project tracking)
- Online sellers can use for product line analysis
- Competitive differentiator

**Implementation:** Simple project tagging with profitability reports (Option B/C hybrid)

**Trade-offs:**
- More database complexity
- Additional UI for project management
- More complex reporting

### 8. Simple Inventory for Online Sellers ✓
**Decision:** Include basic inventory tracking in Phase 1

**Rationale:**
- Online sellers need stock quantity tracking
- Simple buy/sell model (no production)
- COGS calculation needed for profit analysis
- Not complex manufacturing

**Implementation:** Basic inventory with FIFO or Average costing, no production/assembly

**Trade-offs:**
- Not suitable for manufacturers
- Limited to simple buy/sell model

### 32. Project Tracking vs Cost Centers ✓
**Decision:** Simple project tracking for Phase 1, defer generic tags to Phase 2. No traditional cost centers.

**Date:** 2025-11-24

**Rationale:**
- Target market (IT Services, Photographers) works project-based, not department-based
- Traditional cost/revenue centers are for larger organizations with departments
- Projects are the natural unit of profitability analysis for service businesses
- Generic tags provide flexibility for additional dimensions (client, channel) in Phase 2

**Analysis by Segment:**
| Segment | Projects Useful? | Cost Centers Useful? |
|---------|-----------------|---------------------|
| IT Services | ✅ High (per-project profitability) | ❌ No departments |
| Photographers | ✅ High (per-event profitability) | ❌ No departments |
| Online Sellers | ⚠️ Low (product-based, not project-based) | ❌ No departments |

**Implementation:**
- **Phase 1:** Project entity with profitability report
- **Phase 2:** Transaction tags for additional dimensions (client, channel, category)

**Trade-offs:**
- Online sellers don't benefit from project tracking (use tags for channels instead)
- No overhead allocation to projects (too complex for MVP)
- Worth it: High value for primary target segments

---

### 34. Project Milestones & Payment Terms ✓
**Decision:** Include milestones, payment terms, and basic invoices in Phase 1 project tracking.

**Date:** 2025-11-24

**Rationale:**
- Milestones enable accurate progress tracking for cost overrun detection
- Payment terms define when revenue should be recognized
- Invoice linkage provides end-to-end project financial tracking
- Critical for service businesses to mitigate project losses early

**Features:**
| Feature | Purpose |
|---------|---------|
| Milestones | Track % complete per deliverable |
| Payment Terms | Define payment schedule (DP, per milestone, final) |
| Invoices | Generate invoices from payment terms |
| Revenue Recognition | Auto-trigger on milestone completion |
| Cost Overrun Detection | Compare % spent vs % complete |

**Integration:**
- Milestone completion → triggers amortization entry
- Payment term → links to invoice
- Invoice → creates receivable journal entry
- All feed into Project Profitability Report

**Cost Overrun Formula:**
```
Progress % = Σ (milestone.completion_percent × milestone.actual_progress)
Spent % = actual_costs / budget × 100

If Spent % > Progress % + threshold:
    → Trigger cost overrun alert
```

**Trade-offs:**
- More complex project management features
- Users must set up milestones (not automatic)
- Worth it: Prevents project losses, key differentiator

---

### 33. Business Analysis & Smart Alerts ✓
**Decision:** Dashboard KPIs and client/project profitability in Phase 1. Trend analysis and smart alerts in Phase 2.

**Date:** 2025-11-24

**Rationale:**
- Most accounting apps focus only on compliance (tax reports, bookkeeping)
- Our differentiator: actionable business insights
- Help users understand not just "what happened" but "what to do next"
- Project cost overrun alerts critical for mitigating losses in service businesses

**Phase 1 (MVP):**
| Feature | Purpose |
|---------|---------|
| Dashboard KPIs | Quick view of revenue, expenses, profit, cash |
| Project Profitability | Track margin per project |
| Client Profitability | Track margin per client (aggregate of projects) |

**Phase 2:**
| Feature | Purpose |
|---------|---------|
| Trend Charts | Visualize 12-month trends |
| Smart Alerts | Proactive warnings before problems occur |
| Project Cost Overrun Alert | Mitigate project losses early |

**Alert Priority:**
1. **Project Cost Overrun** - Critical for service businesses
2. **Overdue Receivables** - Cash flow impact
3. **Expense Spike** - Cost control
4. **Cash Low Warning** - Survival
5. **Client Concentration** - Risk management

**Trade-offs:**
- More development effort beyond basic bookkeeping
- Alerts need careful tuning to avoid noise
- Worth it: Key differentiator and high user value

---

### 31. Amortization Schedules ✓
**Decision:** Automated period-end adjustments for prepaid expenses, unearned revenue, and intangible assets

**Date:** 2025-11-24

**Rationale:**
- Period-end adjustments are routine, predictable, and repetitive
- Reduces manual work for recurring adjustments
- Prevents users from forgetting period-end entries
- Ensures accurate financial statements

**Scope:**
| Type | Indonesian | Auto? |
|------|------------|-------|
| Prepaid Expense | Beban Dibayar Dimuka | ✅ Automated |
| Unearned Revenue | Pendapatan Diterima Dimuka | ✅ Automated |
| Intangible Asset | Aset Tak Berwujud | ✅ Automated |
| Accrued Revenue | Pendapatan Akrual | ✅ Automated |
| Fixed Asset Depreciation | Penyusutan Aset Tetap | ❌ Phase 5 (needs fiscal regulation) |
| Complex/One-off Adjustments | Penyesuaian Lainnya | ❌ Manual (templates/journal entry) |

**Implementation Notes:**
- User creates schedule manually (no auto-detection from transactions)
- Toggle for auto-post vs draft (user chooses during creation)
- Monthly batch job generates journal entries
- Last period absorbs rounding difference
- Schedule tracks progress (completed_periods, remaining_amount)

**Trade-offs:**
- Additional module to implement
- Users must set up schedules manually
- Worth it: Significantly reduces period-end workload

---

### 30. Cloud Hosting ✓
**Decision:** Local Indonesian providers or cheap global (DigitalOcean), avoid big cloud unless requested

**Date:** 2025-11-22

**Rationale:**
- Cost control for mid-range pricing strategy
- Data residency compliance (Indonesia)
- Big cloud (AWS, GCP) overkill for target market

**Preferred Providers:**
- Indonesian: IDCloudHost, Biznet Gio, Dewaweb
- Global (budget): DigitalOcean
- Avoid: AWS, GCP (unless client specifically requests)

**Implementation Notes:**
- Single VPS per instance (MVP)
- Docker Compose deployment
- Can co-locate multiple instances on same VPS for cost efficiency

---

### 29. Market & Business Model ✓
**Decision:** Monthly subscription, mid-range pricing, business owner focus

**Date:** 2025-11-22

**Target Market:**
- IT Services / Consulting
- Photography / Videography Services
- Online Seller / Marketplace
- General Services (Freelancer)

**Pain Points Addressed:**
- Too complex (competitor apps are accountant-focused)
- Poor mobile experience
- Not designed for business owners

**Pricing:**
- Model: Monthly subscription
- Range: Mid-range (Rp 200k - 500k/month)
- Goal: Cover hosting expenses per instance

**Bookkeeper Support:**
- Separate credentials per client
- No shared dashboard across clients
- Complete data isolation per instance

---

### 28. Tax Integration (e-Faktur, e-Bupot, e-Filing) ✓
**Decision:** Export format default, PJAP integration as custom project

**Date:** 2025-11-22

**Rationale:**
- PJAP (Penyedia Jasa Aplikasi Perpajakan) authorization required for direct integration
- Custom integration adds significant complexity and cost
- Export format sufficient for most users
- Keep core product simple

**What's in Core Product:**
- e-Faktur: Export CSV format for upload to DJP e-Faktur app
- e-Bupot: Export format for upload to DJP e-Bupot
- e-Filing: Export format for SPT data
- Tax reports (PPN, PPh 21, PPh 23, etc.) in standard format

**Custom Project (upon request):**
- Direct PJAP integration (OnlinePajak, Klikpajak, etc.)
- Automated e-Faktur submission
- Automated e-Bupot submission
- Separate development and invoicing per client

**Implementation Notes:**
- Export templates follow DJP required format
- Validate data before export (NPWP format, required fields)
- Track which transactions have been exported/reported

---

### 27. Document Storage Details ✓
**Decision:** Compression enabled, 10MB limit, ClamAV scanning

**Date:** 2025-11-22

**Storage Optimization:**
- Image compression: Yes (80% quality on upload)
- Thumbnail generation: Yes (for preview in UI)
- PDF optimization: Yes (compress on upload)
- Reduces storage cost, faster loading

**File Size Limits:**
- Max per upload: 10 MB
- Max total per company: Based on pricing tier or bill by usage
- Supported formats: JPG, PNG, PDF, Excel (XLS/XLSX), CSV

**Security - Virus Scanning:**
- ClamAV (open source, self-hosted)
- Scan on upload before storing
- Reject infected files with error message

**Implementation Notes:**
- Use ImageMagick or similar for image compression/thumbnails
- Use Apache PDFBox for PDF optimization
- ClamAV daemon running alongside app
- Storage quota tracked per instance
- Alert when approaching storage limit

---

### 26. Digital Signature & E-Meterai ✓
**Decision:** Custom project upon request (not in core product)

**Date:** 2025-11-22

**Rationale:**
- Similar to PJAP integration approach
- Not all users need certified e-signature
- E-meterai only required for documents > Rp 5 juta threshold
- Integration adds complexity and per-transaction cost
- Target market can use manual process initially

**What's NOT in Core Product:**
- PSrE integration (PrivyID, VIDA, Digisign, Peruri Sign)
- E-Meterai API integration (Peruri)
- Certified digital signatures on invoices/contracts

**What IS in Core Product:**
- Basic signature image on documents (uploaded scan)
- Document generation (invoices, contracts) ready for manual e-meterai

**Custom Project Scope (upon request):**
- PSrE provider integration
- E-Meterai bulk application
- Automated signing workflow
- Separate development and invoicing

**Cost Reference (for client quotation):**
- E-Meterai: ~Rp 10,000 per stamp
- Digital signature certificate: Rp 200k-500k/year
- Per-signature fee varies by provider

---

### 25. Marketplace Reconciliation ✓
**Decision:** Phase 1 feature with configurable parser (same approach as bank reconciliation)

**Date:** 2025-11-22

**Rationale:**
- Online sellers are a target market segment
- Need to reconcile marketplace settlements with books
- Same configurable parser approach as bank CSV - reuse architecture
- Track marketplace fees, shipping costs, net settlements

**Implementation Notes:**
- Single `ConfigurableMarketplaceParser` class (reuse pattern from bank parser)
- Marketplace configurations stored in database
- Column name matching with index fallback

- **Marketplace Parsers (Phase 1):**
  - Tokopedia (settlement report)
  - Shopee (income report)
  - Bukalapak
  - Lazada
  - Generic CSV (user maps columns)

- **Fields to Extract:**
  - Order ID / Transaction ID
  - Order date
  - Settlement date
  - Gross sales amount
  - Marketplace fee
  - Shipping fee
  - Promo/discount
  - Net settlement amount

- **Reconciliation Workflow:**
  1. User downloads settlement report from marketplace seller center
  2. Upload to app, select marketplace (or auto-detect)
  3. Parser extracts transactions
  4. Match with recorded sales (by order ID or amount + date)
  5. Auto-create fee expense entries
  6. Show matched/unmatched for review

- **Integration with Payment Gateway/E-wallet:**
  - Custom project upon request (not Phase 1)

**Trade-offs:**
- Must maintain parser configs as marketplaces change formats
- Multiple marketplaces = multiple configs to manage
- Worth it: Core value for online seller segment

---

### 24. Accounting Standards Compliance ✓
**Decision:** SAK EMKM compliance with cash flow statement addition (beyond SAK EMKM requirement)

**Date:** 2025-11-22

**Rationale:**
- Target market is UMKM - SAK EMKM is the applicable standard
- PSAK Syariah not needed (for sharia financial service providers, not customers)
- Cash flow statement not required by SAK EMKM but easy to implement with template-based design
- Cash flow provides valuable business insight, differentiator from competitors

**SAK EMKM Requirements:**
- Laporan Posisi Keuangan (Balance Sheet)
- Laporan Laba Rugi (Income Statement)
- Catatan atas Laporan Keuangan / CALK (Notes)
- Historical cost basis
- Accrual accounting
- Entity concept (separate business from personal)

**Additional (Beyond SAK EMKM):**
- Laporan Arus Kas (Cash Flow Statement)
  - Operating, Investing, Financing sections
  - Direct method (easier with template-based design)
  - Add `cash_flow_category` field to journal templates
  - Auto-classify based on template used

**Implementation Notes:**
- Pre-configured chart of accounts per SAK EMKM
- Journal templates tagged with cash flow category:
  - OPERATING: Sales, purchases, expenses, payroll
  - INVESTING: Asset purchase/sale
  - FINANCING: Loans, owner capital, withdrawals
  - NON_CASH: Depreciation, accruals (excluded from cash flow)
- Standard financial reports: Balance Sheet, Income Statement, Cash Flow, Notes
- PSAK Syariah: Not supported (not applicable to target market)

---

### 23. Data Retention & Document Digitization ✓
**Decision:** 10-year retention, scanned/photo receipts legally valid, physical can be destroyed after proper digitization

**Date:** 2025-11-22

**Research Findings:**

- **Retention Period: 10 Years**
  - Legal basis: UU KUP Pasal 28 ayat 11, PMK 54/2021, UU No. 8/1997
  - Applies to: bookkeeping records, tax documents, supporting documents
  - Must be stored in Indonesia
  - Criminal sanctions for non-compliance (UU KUP Pasal 39)

- **Digital Documents Legally Valid**
  - Legal basis: UU ITE Pasal 5, 6 and PMK 81/2024
  - Both physical and digital formats accepted
  - Effective January 2025 (Coretax system)

- **Scanned/Photo Receipts Acceptable**
  - Legal basis: UU ITE Pasal 5-6, UU No. 8/1997 Pasal 12, 15
  - Requirements for validity:
    - Accessible and displayable
    - Integrity maintained
    - Accountable/verifiable
    - Descriptive of transaction

- **Physical Documents Can Be Destroyed After Scanning**
  - Legal basis: UU No. 8/1997 Pasal 12 ayat 3-4, PP 88/1999
  - Requires legalization (berita acara):
    - Date of digitization
    - Document type
    - Confirmation scan matches original
    - Signature of responsible person
  - Exception: documents with national/company importance

**Implementation Notes:**
- Mobile receipt photo capture feature
- Auto-generate berita acara (legalization record) on upload confirmation
- Store metadata: capture date, hash for integrity verification, uploader info
- Audit trail for document access
- 10-year retention policy in database backup strategy
- Host in Indonesia (local providers or Indonesian cloud regions)

---

### 22. Bank Reconciliation ✓
**Decision:** Yes - Manual upload with bank-specific CSV parsers

**Date:** 2025-11-22

**Rationale:**
- Valuable for catching missing/duplicate entries
- No automatic bank feed needed - users download CSV from bank app
- Most target users have accounts at major Indonesian banks
- Critical for maintaining accurate cash records

**Implementation Notes:**
- **Bank Statement Parsers (Phase 1):**
  - **CSV/Excel only** - no PDF support (PDF extraction unreliable, ~68% accuracy)
  - Preload configs for: BCA, BNI, BSI, CIMB Niaga
  - Generic CSV (user maps columns via UI)
  - Add more banks based on user demand
  - Credit card statements: use CSV export from internet banking

- **Parser Design:**
  - Single `ConfigurableBankStatementParser` class (not per-bank classes)
  - Bank configurations stored in database (not YAML)
  - Column name matching with index fallback:
    ```
    BankParserConfig:
      - bank_code: "bca"
      - bank_name: "Bank Central Asia"
      - date_columns: ["Tanggal", "Date", "Tgl"]
      - date_format: "dd/MM/yyyy"
      - description_columns: ["Keterangan", "Description"]
      - debit_columns: ["Debit", "Mutasi Debit"]
      - credit_columns: ["Credit", "Mutasi Kredit"]
      - balance_columns: ["Saldo", "Balance"]
      - decimal_separator: ","
      - thousand_separator: "."
      - skip_header_rows: 1
      - fallback_indices: {date: 0, desc: 1, debit: 2, credit: 3, balance: 4}
    ```
  - Parsing logic: match column name first → fallback to index → error if not found
  - Preload common bank configs (BCA, BNI, BSI, CIMB) during setup
  - Admin can add/edit bank configs via UI without code changes

- **Reconciliation Workflow:**
  1. User downloads CSV/Excel from bank app/website
  2. Upload to app, select bank (or auto-detect)
  3. Parser extracts: date, description, amount, balance
  4. System matches against recorded transactions (by date + amount)
  5. Show matched, unmatched (in bank only), unmatched (in books only)
  6. User reviews and creates missing entries or marks as reconciled

- **Matching Logic:**
  - Exact match: same date + same amount
  - Fuzzy match: ±1 day + same amount (for timing differences)
  - Manual match: user links transactions manually

- **Reconciliation Report:**
  - Opening balance
  - Matched transactions
  - Unreconciled items (both sides)
  - Closing balance
  - Reconciliation status per period

**Trade-offs:**
- Must update bank config if CSV format changes (database update, no code change)
- No real-time sync (manual upload required)
- Acceptable: Users already download statements for their records

---

### 21. Budget Management ✓
**Decision:** Simple budget vs actual reports (Option B)

**Date:** 2025-11-22

**Rationale:**
- Target market benefits from basic budget tracking
- No need for complex approval workflows
- Aligns with analysis/insight focus of the app
- Helps users control spending and track project profitability

**Implementation Notes:**
- **Budget Setup:**
  - Set budget amount per account per period (monthly/quarterly/yearly)
  - Optional: per project budget
  - Copy previous period budget as starting point

- **Budget vs Actual Report:**
  - Show budgeted vs actual amounts side by side
  - Calculate variance (amount and percentage)
  - Highlight over-budget items
  - Filter by period, account category, project

- **No complex features (keep simple):**
  - No approval workflows
  - No real-time alerts (can add later if needed)
  - No multi-version budget scenarios

**Trade-offs:**
- No budget enforcement (soft tracking only)
- Users must manually review reports
- Sufficient for target market needs

---

### 20. Fixed Asset Management ✓
**Decision:** Basic asset register with auto-journaling via templates + scheduled batch (Option B enhanced)

**Date:** 2025-11-22

**Rationale:**
- Photographers have significant equipment (cameras, lenses, lighting)
- Basic tracking needed for proper accounting
- Auto-depreciation achievable without complex asset module
- Reuse existing journal template infrastructure

**Implementation Notes:**
- **Asset Register:**
  - Asset master data: name, category, purchase date, cost, useful life, depreciation method
  - Track status: Active, Disposed, Fully Depreciated
  - Calculate monthly depreciation amount

- **Auto-Journaling via Templates + Scheduler:**
  - Create depreciation journal templates per asset category
  - Scheduled batch job (monthly) generates depreciation entries:
    1. Query active assets not fully depreciated
    2. Calculate depreciation for period
    3. Generate journal entry using template
    4. Post automatically or queue for review
  - Reuses existing template system - no separate depreciation engine

- **Depreciation Methods:**
  - Straight-line (Garis Lurus): `(Cost - Residual) / Useful Life`
  - Declining balance (Saldo Menurun): `Book Value × Rate`

- **Asset Disposal:**
  - Record sale/write-off via transaction
  - Calculate gain/loss on disposal

**Trade-offs:**
- Not full-featured asset management (no revaluation, complex scenarios)
- Sufficient for target market needs

---

### 19. Document Storage ✓
**Decision:** Dual implementation selectable by config/profile

**Date:** 2025-11-22

**Rationale:**
- MVP needs simplest possible setup (local filesystem)
- Production/SaaS needs scalable storage (S3-compatible)
- Single interface, swap implementation via configuration
- No code changes needed when scaling up

**Implementation Notes:**
- Abstract storage interface: `DocumentStorageService`
- Two implementations selectable by Spring profile/config:

  **1. Local Filesystem (MVP/development):**
  - Profile: `storage.type=local`
  - Store files in configurable directory
  - Simple, zero external dependencies
  - Suitable for single-instance MVP

  **2. S3-Compatible Storage (Production/SaaS):**
  - Profile: `storage.type=s3`
  - Works with:
    - MinIO (locally installed, same server)
    - MinIO (separate VPS)
    - AWS S3
    - GCP Cloud Storage (S3-compatible mode)
    - Indonesian cloud providers with S3 API
  - Configure via: endpoint, bucket, access key, secret key

- Common operations: upload, download, delete, generate signed URL
- File metadata stored in database (filename, path/key, size, mime type, upload date)
- Actual file content stored in selected backend

**Trade-offs:**
- Two implementations to maintain
- Worth it: Flexibility from MVP to production without code changes

---

### 18. Multi-Currency Support ✓
**Decision:** Rupiah only for Phase 1 (Option A)

**Date:** 2025-11-22

**Rationale:**
- Target market (photographers, online sellers, most IT consultants) primarily use IDR accounts
- Foreign expenses paid via IDR card/bank = bank handles conversion, no FX exposure
- Multi-currency only needed when holding foreign currency accounts or invoicing in foreign currency with time gap
- Simplifies Phase 1 implementation significantly

**When Multi-Currency is NOT Needed:**
- Pay foreign services (AWS, etc.) via IDR credit card/bank
- Receive foreign payments via PayPal with auto-convert to IDR
- All bank accounts are in IDR

**When Multi-Currency IS Needed (Phase 2):**
- Own USD/foreign currency bank account
- Invoice in USD with payment received later to USD account
- Foreign supplier with credit terms paid in foreign currency

**Implementation Notes:**
- Phase 1: All transactions in IDR, no currency field needed
- Phase 2: Add multi-currency with Option C (auto-fetch Kurs Pajak from Kemenkeu)
- BI API available for market rates, Kemenkeu scraper for Kurs Pajak

**Trade-offs:**
- IT consultants with foreign clients/USD accounts not supported in Phase 1
- Acceptable: Small subset of target market, can be added later

---

### 17. Transaction Numbering ✓
**Decision:** Per transaction type with yearly reset - `{TYPE}-{YYYY}-{seq}`

**Date:** 2025-11-22

**Rationale:**
- Easier on user's eye - can identify transaction type from number
- Organized categorization for audit and reporting
- Yearly reset keeps numbers manageable

**Implementation Notes:**
- Format: `{TYPE}-{YYYY}-{seq}` (e.g., SAL-2025-00001, EXP-2025-00001)
- Separate sequence per transaction type:
  - SAL: Sales / Penjualan
  - PUR: Purchase / Pembelian
  - EXP: Expense / Biaya
  - RCV: Receipt / Penerimaan
  - PAY: Payment / Pembayaran
  - JNL: General Journal / Jurnal Umum
  - ADJ: Adjustment / Penyesuaian
- Sequence resets to 00001 each fiscal year
- Sequence width configurable (default 5 digits)
- Faktur Pajak numbering follows DJP rules (separate system)

**Trade-offs:**
- Multiple sequences to manage
- Must handle backdating within same year correctly
- Worth it: Better UX and organization

---

### 16. Fiscal Period Locking ✓
**Decision:** Lock after tax filing, soft lock after month close (Option D)

**Date:** 2025-11-22

**Rationale:**
- Implement from the start to avoid migration headaches later
- Balances data integrity with practical correction needs
- Aligns with tax compliance requirements
- Prevents accidental modification of filed tax periods

**Implementation Notes:**
- **Month close (soft lock):**
  - Warning displayed when editing closed period
  - Requires elevated permission (admin/owner role)
  - Audit trail logged for any modifications
  - Recommended: use reversal entries instead of direct edits
- **Tax filing (hard lock):**
  - No edits allowed to periods with filed SPT
  - Must use reversal/adjustment entries in current period
  - SPT filing date recorded per tax type (PPN, PPh 21, PPh 23, etc.)
  - If correction needed: file SPT Pembetulan first, then unlock
- Period status: Open → Month Closed → Tax Filed
- UI should clearly indicate period lock status

**Trade-offs:**
- More complex period management
- Users must understand reversal entry workflow
- Worth it: Prevents tax compliance issues down the line

---

### 15. Payroll Integration ✓
**Decision:** Full payroll with salary component templates (Option C)

**Date:** 2025-11-22

**Rationale:**
- High value-add for target users without accounting/finance/tax background
- PPh 21 and BPJS calculations are too complex for IT consultants, photographers, online sellers
- Differentiator from generic accounting apps
- Reduces errors in tax withholding calculations

**Implementation Notes:**
- Salary component templates:
  - Gaji Pokok
  - Tunjangan (transport, makan, komunikasi, etc.)
  - BPJS Kesehatan (auto-calculate 4% company + 1% employee)
  - BPJS Ketenagakerjaan (JKK, JKM, JHT, JP with configurable rates)
  - PPh 21 (auto-calculate with progressive rates + PTKP)
- Employee master data: PTKP status (TK/0, K/0, K/1, K/2, K/3), NPWP
- Generate payslips
- Auto-generate journal entries for:
  - Salary expense
  - BPJS payable (company + employee portions)
  - PPh 21 payable
  - Net salary payable
- Configurable salary structures per employee or employee group
- **Onboarding simplification:**
  - Preload salary component templates during instance setup
  - Pregenerate salary configs for common job positions per industry:
    - IT Services: Developer, Designer, Project Manager, Admin
    - Photography: Photographer, Videographer, Editor, Assistant
    - Online Seller: Warehouse staff, Packer, Customer service
  - Users can use defaults immediately, customize later if needed
  - No salary configuration required during onboarding

**Trade-offs:**
- Significant implementation effort
- Must keep up with tax regulation changes (PPh 21 rates, PTKP, UMR)
- Employee data management adds complexity
- Worth it: Core value proposition for target market

---

### 14. Conditional Template Logic ✓
**Decision:** No conditional logic - keep templates simple (Option A)

**Date:** 2025-11-22

**Rationale:**
- Simpler implementation, predictable behavior
- Easier to debug when journal entries are incorrect
- Users explicitly choose the correct template for their scenario
- Avoids "magic" behavior that may confuse users

**Implementation Notes:**
- Create separate templates for different scenarios (e.g., "Penjualan Jasa", "Penjualan Jasa + PPh 23")
- Provide **preview/simulation functionality** so users can:
  - See what inputs a template requires
  - Preview generated journal entries before committing
  - Verify debit/credit balance before saving
- Template selector should guide users to correct template based on transaction characteristics

**Trade-offs:**
- More templates to manage
- User must select correct template (mitigated by preview feature)
- May revisit Option B in future if template proliferation becomes unmanageable

---

### 13. Formula Complexity ✓
**Decision:** Full expression language using SpEL with SimpleEvaluationContext (Option D)

**Date:** 2025-11-22

**Rationale:**
- SpEL is built-in to Spring - no additional dependencies
- SimpleEvaluationContext provides secure sandbox for untrusted input
- Blocks dangerous operations: type references, constructors, bean references, arbitrary method calls
- Supports all required operations: arithmetic, comparison, logical, ternary conditionals, property access
- Long-term support guaranteed by Spring team
- Familiar syntax for Java developers

**Implementation Notes:**
- Use `SimpleEvaluationContext.forReadOnlyDataBinding()` for formula evaluation
- Create `FormulaContext` root object with transaction data (amount, rate, etc.)
- Register custom functions via `setVariable()` if needed for complex scenarios
- Example formulas:
  - `amount * 0.11` (simple percentage)
  - `amount > 2000000 ? amount * 0.02 : 0` (conditional - PPh 23 threshold)
  - `transaction.amount * rate.ppn` (field references)

**Trade-offs:**
- Slightly more complex than simple percentage parsing
- Need to validate formula syntax before saving templates
- Formula errors surface at runtime (mitigate with validation on template save)

---

### 12. Template Categories/Tags ✓
**Decision:** Category + tags with favorites, frequently used, and search (Option D extended)

**Date:** 2025-11-22

**Rationale:**
- Category provides primary structure for navigation
- Tags enable flexible cross-categorization
- Favorites for quick access to preferred templates
- Frequently used for automatic surfacing of common templates
- Search for direct lookup when user knows what they want

**Implementation Notes:**
- Each template has one category (required) and multiple tags (optional)
- User-specific favorites list (stored per user)
- Track usage count per template per user for "frequently used" ranking
- Full-text search on template name, description, and tags
- UI: Show favorites first → frequently used → then by category

**Trade-offs:**
- More complex UI (multiple access patterns)
- Additional tracking for usage statistics
- User preferences storage needed

---

### 11. Template Validation Rules ✓
**Decision:** No runtime validation - show all templates (Option C)

**Date:** 2025-11-22

**Rationale:**
- Single-tenant architecture: each database instance is company-specific
- Templates will be pre-generated during SaaS onboarding based on company setup (PKP status, business type, etc.)
- No need for runtime validation since templates are already tailored per instance
- Simpler implementation, no conditional logic needed

**Implementation Notes:**
- SaaS control plane handles template seeding during instance provisioning
- Template set determined by company configuration at setup time
- Users can add/customize templates post-setup as needed

**Trade-offs:**
- Relies on correct setup during onboarding
- Manual template management if company status changes (e.g., becomes PKP)

---

### 10. Template Versioning ✓
**Decision:** Version templates - each edit creates new version, old transactions link to old version (Option A)

**Date:** 2025-11-22

**Rationale:**
- Required for audit traceability
- Historical accuracy of what template was used at transaction time
- Compliance requirement for accounting records

**Implementation Notes:**
- Each template edit creates a new version record
- Journal entries store reference to specific template version used
- Include traceability key in journal posting for backtracking to the originating template
- Template version history viewable for audit purposes

**Trade-offs:**
- Additional database complexity (version table, foreign keys)
- UI needed to show version history
- Migration strategy for template updates

---

### 9. Technology Stack: Spring Boot Monolith ✓
**Decision:** Java 25 + Spring Boot 4.0 + Thymeleaf + HTMX + Alpine.js + PostgreSQL 17

**Date:** 2025-11-19

**Alternatives Considered:**
1. Spring Boot + React/Vue (Split BE/FE)
2. ExpressJS + Handlebars (Node.js)
3. Spring Boot + Thymeleaf + HTMX (Selected)

**Rationale:**
- **Monolith over Split BE/FE:**
  - Single codebase reduces maintenance overhead
  - No data model or validation duplication
  - Simple session-based authentication
  - Faster development and debugging
  - Sufficient UX for form-heavy accounting workflows

- **Java 25 + Spring Boot over Node.js:**
  - BigDecimal for precise financial calculations
  - Type safety for complex business logic
  - Mature transaction management (ACID)
  - Virtual threads for efficient resource utilization (especially when co-locating instances)
  - Large Java talent pool in Indonesia
  - Battle-tested security for financial applications

- **HTMX Enhancement:**
  - Modern UX without SPA complexity
  - Partial page updates
  - Progressive enhancement
  - No build process required

- **Document Storage Strategy:**
  - Phase 1: MinIO (self-hosted, cost-optimized)
  - Phase 2+: Indonesian cloud or AWS/GCP
  - S3-compatible API for easy migration

- **DevSecOps Tools:**
  - Docker Compose (local development)
  - Ansible (IaC, deployment)
  - Playwright (functional testing)
  - K6 (performance testing)
  - SonarQube, OWASP tools, Trivy (security)

**Implementation:** See [Technology Stack Documentation](05-technology-stack.md) for details

**Trade-offs:**
- Less flashy UI than SPA (acceptable for target users)
- Server-rendered pages (suitable for form-heavy workflows)
- MinIO requires ops overhead (offset by cost savings)
- Can refactor to API architecture later if mobile app needed

**Review Date:** After MVP launch

## Open Questions

### Template System Design

#### Q1: Template Versioning? ✓ ANSWERED
**Decision:** Option A - Version templates with traceability key in journal postings

See Decision #10 above.

---

#### Q2: Template Validation Rules? ✓ ANSWERED
**Decision:** Option C - No runtime validation, templates pre-generated per instance

See Decision #11 above.

---

#### Q3: Template Categories/Tags? ✓ ANSWERED
**Decision:** Option D extended - Category + tags + favorites + frequently used + search

See Decision #12 above.

---

#### Q4: Formula Complexity? ✓ ANSWERED
**Decision:** Option D - Full expression language using SpEL with SimpleEvaluationContext

See Decision #13 above.

---

#### Q5: Conditional Template Logic? ✓ ANSWERED
**Decision:** Option A - No conditional logic, with preview/simulation functionality

See Decision #14 above.

---

### Business Logic Questions

#### Q6: Business Type Priority? ✓ ANSWERED
**Question:** Which business types should we create default chart of accounts templates for in Phase 1?

**Decision:**
1. IT Services / Consulting
2. Photography / Videography Services
3. Online Seller / Marketplace
4. General Services (Freelancer)

See Decision #6 above.

---

#### Q7: Inventory Tracking? ✓ ANSWERED
**Question:** Should Phase 1 include inventory management features?

**Decision:** Option C - Basic inventory with FIFO/Average costing for online sellers

See Decision #8 above.

---

#### Q8: Payroll Integration? ✓ ANSWERED
**Decision:** Option C - Full payroll with salary component templates

See Decision #15 above.

---

#### Q9: Project/Job Costing? ✓ ANSWERED
**Question:** Should transactions be taggable by project/job for cost tracking?

**Decision:** YES - Option B/C hybrid (simple tagging + profitability reports)

**Use cases covered:**
- Photographers billing by event/wedding
- Consultants/developers tracking project costs
- Multi-project expense allocation

See Decision #7 above.

---

### Data & Technical Questions

#### Q10: Fiscal Period Locking? ✓ ANSWERED
**Decision:** Option D - Lock after tax filing, soft lock after month close (from Phase 1)

See Decision #16 above.

---

#### Q11: Transaction Numbering? ✓ ANSWERED
**Decision:** Per transaction type with yearly reset - `{TYPE}-{YYYY}-{seq}`

See Decision #17 above.

---

#### Q12: Multi-Currency Support? ✓ ANSWERED
**Decision:** Option A - Rupiah only for Phase 1, add later if required

See Decision #18 above.

---

#### Q13: Document Storage? ✓ ANSWERED
**Decision:** Dual implementation - Local FS (MVP) + S3-compatible (Production), selectable by config

See Decision #19 above.

---

### Feature Scope Questions

#### Q14: Fixed Asset Management? ✓ ANSWERED
**Decision:** Option B enhanced - Basic register + auto-journaling via templates + scheduled batch

See Decision #20 above.

---

#### Q15: Budget Management? ✓ ANSWERED
**Decision:** Option B - Simple budget vs actual reports

See Decision #21 above.

---

#### Q16: Bank Reconciliation (without integration)? ✓ ANSWERED
**Decision:** Yes - Manual CSV upload with bank-specific parsers (BCA, BNI, BSI, CIMB)

See Decision #22 above.

---

## Research Needed

### Market Research ✓ COMPLETED
See Decision #29 - Market & Business Model

### Technical Research ✓ COMPLETED
- e-Faktur, e-Bupot, e-Filing: See Decision #28 - Export format, PJAP as custom project
- Cloud hosting: See Decision #30 - Local providers or DigitalOcean

### Competitive Analysis ✓ COMPLETED
Not needed - self-driven feature set based on own company needs

### Regulatory Research ✓ COMPLETED
1. **Data retention**: See Decision #23 - 10-year retention, digital valid
2. **Tax audit**: See Decision #23 - digital documents accepted
3. **Electronic signature**: See Decision #26 - custom project upon request
4. **Upcoming regulation**: PMK 81/2024 Coretax effective Jan 2025
5. **Accounting standards**: See Decision #24 - SAK EMKM with cash flow

### Payment Integration Research ✓ COMPLETED
- Payment gateway (Midtrans, Xendit): Custom project upon request
- E-wallet integration: Custom project upon request
- Marketplace reconciliation: See Decision #25 - Phase 1 with configurable parser

### Digital Signature & E-Meterai Research ✓ COMPLETED
See Decision #26 - Custom project upon request

### Document Storage & Management Research ✓ COMPLETED
- Storage approach: See Decision #19 - Local FS (MVP) + S3-compatible (Production)
- Storage details: See Decision #27 - Compression, 10MB limit, ClamAV scanning

---

## Decision Log Template

When decisions are made, document them here:

```
### Decision: [Title]
**Date:** YYYY-MM-DD
**Decided by:** [Name/Team]
**Question:** [What was being decided]
**Options considered:** [List]
**Decision:** [Chosen option]
**Rationale:** [Why]
**Implementation notes:** [How to implement]
**Review date:** [When to revisit if needed]
```

---

## Next Steps

~~1. **Prioritize open questions** based on Phase 1 requirements~~ ✓ DONE
~~2. **Make MVP decisions** (defer others to later phases)~~ ✓ DONE
~~3. **Validate assumptions** with potential users~~ ✓ DONE (self as primary user)

**Ready for Implementation:**
1. **Set up tech stack** and development environment
2. **Create database schema** based on data model
3. **Build proof of concept** for template system
4. **Implement core features** per decisions above
5. **Design UI/UX** for transaction entry flow (business owner focused)

**Total Decisions Made: 30**
