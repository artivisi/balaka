# Database Schema & Data Model

## Entity Relationship Overview

```mermaid
graph TD
    tenants["tenants"] -->|1:many| coa["chart_of_accounts"]
    tenants -->|1:many| transactions["transactions"]
    tenants -->|1:many| jt["journal_templates<br/>(system + custom)"]

    transactions -->|has many| je["journal_entries"]
    coa -->|referenced by| je

    transactions -->|generated from| jt
    jt -->|defines structure| jtl["journal_template_lines"]
```

## Core Entities

### 1. Tenants (Multi-tenancy)

```sql
tenants
- id                    UUID PRIMARY KEY
- name                  VARCHAR(255) NOT NULL
- business_type         VARCHAR(100)           -- 'warung', 'toko', 'jasa', etc.
- npwp                  VARCHAR(20)
- is_pkp                BOOLEAN DEFAULT false  -- PKP status for PPN
- fiscal_year_start     INTEGER DEFAULT 1      -- Month (1-12)
- timezone              VARCHAR(50) DEFAULT 'Asia/Jakarta'
- created_at            TIMESTAMP
- updated_at            TIMESTAMP
- is_active             BOOLEAN DEFAULT true
```

### 2. Chart of Accounts

```sql
chart_of_accounts
- id                    UUID PRIMARY KEY
- tenant_id             UUID NOT NULL REFERENCES tenants(id)
- code                  VARCHAR(20) NOT NULL   -- e.g., "1-1100"
- name                  VARCHAR(255) NOT NULL  -- e.g., "Kas"
- name_en               VARCHAR(255)           -- Optional English name
- account_type          VARCHAR(20) NOT NULL   -- 'asset', 'liability', 'equity', 'revenue', 'expense'
- parent_id             UUID REFERENCES chart_of_accounts(id)
- level                 INTEGER                -- Hierarchy level (1, 2, 3...)
- is_header             BOOLEAN DEFAULT false  -- Header vs detail account
- is_active             BOOLEAN DEFAULT true
- description           TEXT
- created_at            TIMESTAMP
- updated_at            TIMESTAMP

UNIQUE(tenant_id, code)
INDEX(tenant_id, account_type)
INDEX(tenant_id, parent_id)
```

**Account Types:**
- `asset` - Aset (Harta)
- `liability` - Kewajiban (Hutang)
- `equity` - Ekuitas (Modal)
- `revenue` - Pendapatan
- `expense` - Beban

**Example Hierarchy:**
```mermaid
graph TD
    A["1-0000 Aset<br/>(Header)"]
    B["1-1000 Aset Lancar<br/>(Header)"]
    C["1-1100 Kas<br/>(Detail)"]
    D["1-1200 Bank BCA<br/>(Detail)"]
    E["1-1300 Piutang Usaha<br/>(Detail)"]
    F["1-1400 PPN Masukan<br/>(Detail)"]
    G["1-2000 Aset Tetap<br/>(Header)"]
    H["1-2100 Peralatan Kantor<br/>(Detail)"]

    A --> B
    A --> G
    B --> C
    B --> D
    B --> E
    B --> F
    G --> H
```

### 3. Journal Templates

```sql
journal_templates
- id                    UUID PRIMARY KEY
- tenant_id             UUID REFERENCES tenants(id)  -- NULL for system templates
- name                  VARCHAR(255) NOT NULL
- description           TEXT
- category              VARCHAR(50)            -- 'expense', 'income', 'payment', 'transfer', etc.
- is_system             BOOLEAN DEFAULT false  -- System vs user-created
- is_active             BOOLEAN DEFAULT true
- created_by            UUID REFERENCES users(id)
- created_at            TIMESTAMP
- updated_at            TIMESTAMP

INDEX(tenant_id, category)
INDEX(is_system, is_active)
```

**Categories:**
- `income` - Penerimaan
- `expense` - Pengeluaran
- `payment` - Pembayaran
- `receipt` - Penerimaan Pembayaran
- `transfer` - Transfer Antar Akun
- `tax` - Pajak
- `adjustment` - Penyesuaian
- `closing` - Penutupan

### 4. Journal Template Lines

```sql
journal_template_lines
- id                    UUID PRIMARY KEY
- template_id           UUID NOT NULL REFERENCES journal_templates(id) ON DELETE CASCADE
- line_number           INTEGER NOT NULL       -- Order of lines
- line_type             VARCHAR(10) NOT NULL   -- 'debit' or 'credit'
- account_id            UUID REFERENCES chart_of_accounts(id)  -- NULL if user-selectable
- mapping_key           VARCHAR(100)           -- e.g., "expense_account", "payment_account"
- label                 VARCHAR(255)           -- UI label for user selection
- amount_source         VARCHAR(20) NOT NULL   -- 'transaction_amount', 'fixed', 'percentage', 'formula'
- amount_value          DECIMAL(15,2)          -- For fixed amounts or percentage
- formula               TEXT                   -- For complex calculations
- is_required           BOOLEAN DEFAULT true
- description           TEXT
- created_at            TIMESTAMP

INDEX(template_id, line_number)
```

**Amount Source Types:**
- `transaction_amount` - Use transaction amount * percentage
- `fixed` - Fixed amount (stored in amount_value)
- `percentage` - Percentage of transaction (e.g., 11% for PPN)
- `formula` - Custom formula (e.g., "transaction_amount * 0.11 + 1000")
- `user_defined` - User enters amount manually

**Example Template Lines:**

Template: "Bayar Beban + PPN"
```
Line 1: Debit  | account_id: NULL           | mapping_key: "expense_account"  | amount: 100% | label: "Akun Beban"
Line 2: Debit  | account_id: [PPN Masukan]  | mapping_key: NULL               | amount: 11%  | label: NULL
Line 3: Credit | account_id: NULL           | mapping_key: "payment_account"  | amount: 111% | label: "Dibayar dari"
```

### 5. Transactions

```sql
transactions
- id                    UUID PRIMARY KEY
- tenant_id             UUID NOT NULL REFERENCES tenants(id)
- template_id           UUID REFERENCES journal_templates(id)
- transaction_number    VARCHAR(50) NOT NULL   -- Auto-generated: TRX-2025-00001
- transaction_date      DATE NOT NULL
- description           TEXT NOT NULL
- reference_no          VARCHAR(100)           -- External reference (invoice no, etc.)
- total_amount          DECIMAL(15,2) NOT NULL -- Base amount (DPP for tax transactions)
- status                VARCHAR(20) DEFAULT 'draft'  -- 'draft', 'posted', 'void'
- metadata              JSONB                  -- Template-specific data
- created_by            UUID REFERENCES users(id)
- posted_by             UUID REFERENCES users(id)
- posted_at             TIMESTAMP
- voided_by             UUID REFERENCES users(id)
- voided_at             TIMESTAMP
- void_reason           TEXT
- created_at            TIMESTAMP
- updated_at            TIMESTAMP

UNIQUE(tenant_id, transaction_number)
INDEX(tenant_id, transaction_date)
INDEX(tenant_id, status)
INDEX(template_id)
```

**Status Values:**
- `draft` - Saved but not posted to ledger
- `posted` - Posted to general ledger
- `void` - Cancelled (soft delete, maintains audit trail)

**Metadata Example (JSONB):**
```json
{
  "customer_id": "uuid",
  "invoice_number": "INV-2025-001",
  "due_date": "2025-02-15",
  "ppn_rate": 0.11,
  "includes_ppn": true
}
```

### 6. Transaction Account Mappings

```sql
transaction_account_mappings
- id                    UUID PRIMARY KEY
- transaction_id        UUID NOT NULL REFERENCES transactions(id) ON DELETE CASCADE
- mapping_key           VARCHAR(100) NOT NULL  -- Matches template line mapping_key
- account_id            UUID NOT NULL REFERENCES chart_of_accounts(id)
- amount                DECIMAL(15,2)          -- NULL means use template calculation
- notes                 TEXT
- created_at            TIMESTAMP

INDEX(transaction_id)
INDEX(account_id)
```

**Purpose:** Links user-selected accounts to template placeholders

**Example:**
```
Transaction: Bayar listrik Rp 1,000,000
Mappings:
- mapping_key: "expense_account"  → account_id: [5-1100 Beban Listrik]
- mapping_key: "payment_account"  → account_id: [1-1200 Bank BCA]
```

### 7. Journal Entries

```sql
journal_entries
- id                    UUID PRIMARY KEY
- tenant_id             UUID NOT NULL REFERENCES tenants(id)
- transaction_id        UUID REFERENCES transactions(id) ON DELETE RESTRICT
- entry_date            DATE NOT NULL
- account_id            UUID NOT NULL REFERENCES chart_of_accounts(id)
- debit                 DECIMAL(15,2) DEFAULT 0
- credit                DECIMAL(15,2) DEFAULT 0
- description           TEXT
- created_at            TIMESTAMP
- updated_at            TIMESTAMP

INDEX(tenant_id, entry_date)
INDEX(tenant_id, account_id, entry_date)
INDEX(transaction_id)

CONSTRAINT check_debit_or_credit CHECK (
  (debit > 0 AND credit = 0) OR (credit > 0 AND debit = 0)
)
```

**Immutability:** Once posted, journal entries should not be edited. Corrections done via reversal entries.

**Example Journal Entries:**
```
Transaction: TRX-2025-00123
Entry 1: account_id: [5-2100], debit: 1,000,000, credit: 0, description: "Beban Sewa Kantor"
Entry 2: account_id: [1-1400], debit:   110,000, credit: 0, description: "PPN Masukan"
Entry 3: account_id: [1-1200], debit:         0, credit: 1,110,000, description: "Bank BCA"
```

### 8. Account Balances (Materialized)

```sql
account_balances
- id                    UUID PRIMARY KEY
- tenant_id             UUID NOT NULL REFERENCES tenants(id)
- account_id            UUID NOT NULL REFERENCES chart_of_accounts(id)
- period_year           INTEGER NOT NULL
- period_month          INTEGER NOT NULL       -- 1-12
- opening_balance       DECIMAL(15,2) DEFAULT 0
- debit_total           DECIMAL(15,2) DEFAULT 0
- credit_total          DECIMAL(15,2) DEFAULT 0
- ending_balance        DECIMAL(15,2) DEFAULT 0
- updated_at            TIMESTAMP

UNIQUE(tenant_id, account_id, period_year, period_month)
INDEX(tenant_id, period_year, period_month)
```

**Purpose:** Pre-aggregated balances for fast report generation

**Calculation:**
```
ending_balance = opening_balance + debit_total - credit_total  (for asset/expense)
ending_balance = opening_balance - debit_total + credit_total  (for liability/equity/revenue)
```

## Supporting Entities

### 9. Users

```sql
users
- id                    UUID PRIMARY KEY
- email                 VARCHAR(255) UNIQUE NOT NULL
- password_hash         VARCHAR(255) NOT NULL
- full_name             VARCHAR(255) NOT NULL
- role                  VARCHAR(50) NOT NULL   -- 'owner', 'operator', 'power_operator', 'viewer', 'auditor'
- is_active             BOOLEAN DEFAULT true
- created_at            TIMESTAMP
- updated_at            TIMESTAMP
- last_login_at         TIMESTAMP
```

### 10. User Tenant Access

```sql
user_tenant_access
- id                    UUID PRIMARY KEY
- user_id               UUID NOT NULL REFERENCES users(id)
- tenant_id             UUID NOT NULL REFERENCES tenants(id)
- role                  VARCHAR(50) NOT NULL   -- 'owner', 'operator', 'power_operator', 'viewer', 'auditor'
- is_active             BOOLEAN DEFAULT true
- access_start_date     DATE                   -- For time-bound access (auditor)
- access_end_date       DATE                   -- For time-bound access (auditor)
- granted_by            UUID REFERENCES users(id)
- granted_at            TIMESTAMP
- revoked_at            TIMESTAMP

UNIQUE(user_id, tenant_id)
INDEX(user_id)
INDEX(tenant_id)
INDEX(access_end_date) WHERE role = 'auditor'
```

**Purpose:** Multi-client access for bookkeepers

### 11. Audit Log

```sql
audit_logs
- id                    UUID PRIMARY KEY
- tenant_id             UUID NOT NULL REFERENCES tenants(id)
- user_id               UUID REFERENCES users(id)
- entity_type           VARCHAR(100) NOT NULL  -- 'transaction', 'journal_entry', etc.
- entity_id             UUID NOT NULL
- action                VARCHAR(50) NOT NULL   -- 'create', 'update', 'delete', 'void', 'post'
- old_values            JSONB
- new_values            JSONB
- ip_address            VARCHAR(45)
- user_agent            TEXT
- created_at            TIMESTAMP

INDEX(tenant_id, created_at DESC)
INDEX(entity_type, entity_id)
INDEX(user_id, created_at DESC)
```

### 12. Documents (Supporting Evidence)

```sql
documents
- id                    UUID PRIMARY KEY
- tenant_id             UUID NOT NULL REFERENCES tenants(id)
- transaction_id        UUID REFERENCES transactions(id) ON DELETE SET NULL
- document_type         VARCHAR(50) NOT NULL   -- 'receipt', 'invoice', 'contract', 'other'
- file_name             VARCHAR(255) NOT NULL
- file_size             BIGINT NOT NULL        -- In bytes
- file_type             VARCHAR(100) NOT NULL  -- MIME type
- storage_path          TEXT NOT NULL          -- Cloud storage path/key
- storage_url           TEXT                   -- Signed URL (temporary)
- thumbnail_path        TEXT                   -- Thumbnail for images
- uploaded_by           UUID REFERENCES users(id)
- uploaded_at           TIMESTAMP NOT NULL
- description           TEXT
- is_archived           BOOLEAN DEFAULT false
- archived_at           TIMESTAMP
- metadata              JSONB                  -- OCR text, dimensions, etc.

INDEX(tenant_id, uploaded_at DESC)
INDEX(transaction_id)
INDEX(tenant_id, document_type)
INDEX(tenant_id, is_archived)
```

**Purpose:** Store supporting documents for transactions (receipts, invoices, contracts)

**Storage organization:**
```mermaid
graph TD
    A["bucket/"]
    B["{tenant_id}/"]
    C["{year}/"]
    D["{month}/"]
    E["{document_id}.{ext}"]
    F["{document_id}_thumb.jpg"]

    A --> B
    B --> C
    C --> D
    D --> E
    D --> F
```

### 13. Document Access Log

```sql
document_access_logs
- id                    UUID PRIMARY KEY
- document_id           UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE
- user_id               UUID NOT NULL REFERENCES users(id)
- tenant_id             UUID NOT NULL REFERENCES tenants(id)
- access_type           VARCHAR(50) NOT NULL   -- 'view', 'download', 'share'
- ip_address            VARCHAR(45)
- user_agent            TEXT
- accessed_at           TIMESTAMP NOT NULL

INDEX(document_id, accessed_at DESC)
INDEX(user_id, accessed_at DESC)
INDEX(tenant_id, accessed_at DESC)
```

**Purpose:** Audit trail for document access (who viewed/downloaded what, when)

## Data Validation Rules

### Chart of Accounts
- `code` must be unique per tenant
- `account_type` must be one of defined types
- `parent_id` must be same tenant
- Header accounts cannot have transactions (enforced in app)

### Journal Templates
- System templates: `tenant_id` must be NULL
- Custom templates: `tenant_id` must NOT be NULL
- Template lines must balance (total debit % = total credit %)

### Transactions
- `transaction_date` cannot be in future
- `total_amount` must be > 0
- Status transitions: draft → posted → void (one-way)
- Cannot void if referenced by other transactions

### Journal Entries
- `debit` and `credit` cannot both be > 0
- `debit` or `credit` must be > 0 (at least one)
- Entries for a transaction must balance: SUM(debit) = SUM(credit)
- `entry_date` should match `transaction.transaction_date`

### Account Balances
- Updated via triggers or background jobs
- Recalculated on period close
- Read-only to application (calculated field)

## Indexing Strategy

### High-frequency queries
```sql
-- Transaction listing
INDEX(tenant_id, transaction_date DESC, status)

-- Account activity
INDEX(tenant_id, account_id, entry_date)

-- Template selection
INDEX(tenant_id, category, is_active) WHERE is_system = false
INDEX(category, is_active) WHERE is_system = true

-- User access
INDEX(user_id, tenant_id) WHERE is_active = true
```

### Composite indexes for reports
```sql
-- Balance sheet / Income statement
INDEX(tenant_id, account_type, entry_date)

-- Trial balance
INDEX(tenant_id, entry_date, account_id)

-- Tax reports
INDEX(tenant_id, entry_date) WHERE account_id IN (ppn_accounts)
```

## Data Integrity

### Cascading Rules
- `journal_template_lines` CASCADE on template delete
- `transaction_account_mappings` CASCADE on transaction delete
- `journal_entries` RESTRICT on transaction delete (cannot delete posted transactions)

### Soft Deletes
- Transactions: Set status = 'void' instead of DELETE
- Chart of Accounts: Set is_active = false instead of DELETE
- Users: Set is_active = false instead of DELETE

### Audit Trail
- All mutations logged to `audit_logs`
- Journal entries are append-only (no updates after posting)
- Transaction edits only allowed in 'draft' status

## Sample Data Flow

### Creating a Transaction

1. **User selects template:**
   ```sql
   SELECT * FROM journal_templates
   WHERE (tenant_id = :tenant_id OR is_system = true)
     AND category = 'expense'
     AND is_active = true
   ```

2. **Load template lines:**
   ```sql
   SELECT * FROM journal_template_lines
   WHERE template_id = :template_id
   ORDER BY line_number
   ```

3. **User fills form, system creates transaction:**
   ```sql
   INSERT INTO transactions
   (tenant_id, template_id, transaction_date, description, total_amount, status)
   VALUES (:tenant_id, :template_id, :date, :desc, :amount, 'draft')
   ```

4. **Save account mappings:**
   ```sql
   INSERT INTO transaction_account_mappings
   (transaction_id, mapping_key, account_id)
   VALUES
   (:txn_id, 'expense_account', :expense_acct_id),
   (:txn_id, 'payment_account', :payment_acct_id)
   ```

5. **Generate journal entries:**
   ```sql
   -- For each template line, resolve account and amount
   INSERT INTO journal_entries
   (tenant_id, transaction_id, entry_date, account_id, debit, credit, description)
   VALUES
   (:tenant_id, :txn_id, :date, :expense_acct, :amount, 0, :desc),
   (:tenant_id, :txn_id, :date, :ppn_acct, :ppn_amount, 0, 'PPN Masukan'),
   (:tenant_id, :txn_id, :date, :bank_acct, 0, :total, :desc)
   ```

6. **Post transaction:**
   ```sql
   UPDATE transactions
   SET status = 'posted',
       posted_by = :user_id,
       posted_at = NOW()
   WHERE id = :txn_id
   ```

7. **Update balances (trigger or async job):**
   ```sql
   UPDATE account_balances
   SET debit_total = debit_total + :debit,
       credit_total = credit_total + :credit,
       ending_balance = /* recalculate */
   WHERE tenant_id = :tenant_id
     AND account_id = :account_id
     AND period_year = :year
     AND period_month = :month
   ```

## Migration Strategy

### Initial Setup
1. Create tenants table
2. Create users and user_tenant_access
3. Create chart_of_accounts with default templates
4. Load system journal templates
5. Create transaction tables
6. Create journal_entries and balances

### Seed Data
- Default chart of accounts by business type
- System journal templates (common Indonesian scenarios)
- Indonesian tax accounts (PPN, PPh, etc.)
- Sample transactions for demo
