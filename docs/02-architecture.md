# Architecture

## System Overview

Indonesian accounting application for small businesses built with Spring Boot 4.0 and designed specifically for Indonesian tax compliance.

### Architecture Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Architecture | Monolith | Single codebase, simpler deployment |
| Frontend | Server-rendered + HTMX | No SPA complexity, works without JS |
| Backend | Java 25 + Spring Boot 4.0 | BigDecimal precision, type safety |
| Database | PostgreSQL 18 | ACID, JSON support, mature |
| Multi-tenancy | Single-tenant (per instance) | Complete data isolation |

### Stack Summary

```
User Interface
├── Thymeleaf templates
├── HTMX (partial page updates)
├── Alpine.js (client reactivity)
└── Bootstrap + Tailwind CSS

Application
├── Java 25 (virtual threads)
├── Spring Boot 4.0
├── Spring Data JPA
├── Spring Security
└── Flyway migrations

Data Storage
├── PostgreSQL 18
└── MinIO / Local filesystem (documents)

External Services
├── Google Cloud Vision (OCR)
├── Telegram Bot API
└── Email service
```

## Architecture Patterns

### Transaction-Centric Design

Users describe business events; system generates journal entries:

```
User sees:     "Expense Payment" form
User enters:   Amount, category, payment account
System creates: Dr Beban Listrik, Cr Bank BCA
```

All journal entries are created through transactions. There are no standalone journal entries.

```sql
transaction (header)
├── journal_entries[] (debit/credit lines)
├── journal_template (mapping rules)
└── account_mappings (user-selected accounts)
```

### Single-Tenant Deployment

Each company gets isolated instance and database:

```
Control Plane
├── Instance A (Company 1)
│   ├── PostgreSQL Database
│   └── Spring Boot App
├── Instance B (Company 2)
│   ├── PostgreSQL Database
│   └── Spring Boot App
```

Benefits:
- Complete data isolation
- No multi-tenancy complexity
- Independent scaling
- Simpler compliance

## Application Layers

### Controller Layer

**Location:** `src/main/java/.../controller/`

- 25+ controllers for business domains
- RESTful design with proper HTTP mappings
- Method-level security annotations
- HTMX integration for dynamic updates

Key Controllers:
| Controller | Responsibility |
|------------|----------------|
| DashboardController | KPIs, overview |
| TransactionController | All journal CRUD (create, edit, post, void) |
| JournalEntryController | Ledger views only (read-only) |
| JournalTemplateController | Template configuration |
| PayrollController | Payroll processing |
| TaxReportController | Tax compliance |

### Service Layer

**Location:** `src/main/java/.../service/`

- Business logic implementation
- Transaction management
- Indonesian tax calculations

Key Services:
| Service | Responsibility |
|---------|----------------|
| JournalService | Double-entry bookkeeping |
| FormulaService | SpEL-based calculations |
| Pph21Service | Indonesian PPh 21 |
| PayrollService | Payroll processing |
| OcrService | Receipt processing |

### Repository Layer

**Location:** `src/main/java/.../repository/`

- Spring Data JPA repositories
- Custom queries for reporting
- Soft delete patterns

### Entity Layer

**Location:** `src/main/java/.../entity/`

- 85 JPA entities
- UUID primary keys
- Audit fields (createdAt, updatedAt)

## Data Model

### Core Entities

```sql
company_config
├── id                    UUID PK
├── name                  VARCHAR(255)
├── npwp                  VARCHAR(20)
├── is_pkp                BOOLEAN
└── fiscal_year_start     INTEGER

chart_of_accounts
├── id                    UUID PK
├── code                  VARCHAR(20) UNIQUE
├── name                  VARCHAR(255)
├── account_type          ENUM (asset, liability, equity, revenue, expense)
├── parent_id             UUID FK
├── is_header             BOOLEAN
└── is_active             BOOLEAN

journal_templates
├── id                    UUID PK
├── name                  VARCHAR(255)
├── category              VARCHAR(50)
├── cash_flow_category    ENUM (OPERATING, INVESTING, FINANCING, NON_CASH)
├── is_system             BOOLEAN
└── lines[]               → journal_template_lines

journal_template_lines
├── id                    UUID PK
├── template_id           UUID FK
├── line_number           INTEGER
├── line_type             ENUM (debit, credit)
├── account_id            UUID FK (nullable if user-selectable)
├── mapping_key           VARCHAR(100)
├── formula               TEXT
└── is_required           BOOLEAN

transactions
├── id                    UUID PK
├── template_id           UUID FK
├── transaction_type      VARCHAR(3)
├── transaction_number    VARCHAR(50) UNIQUE
├── transaction_date      DATE
├── total_amount          DECIMAL(15,2)
├── status                ENUM (draft, posted, void)
└── account_mappings[]    → transaction_account_mappings

journal_entries
├── id                    UUID PK
├── transaction_id        UUID FK (NOT NULL)
├── account_id            UUID FK
├── debit                 DECIMAL(15,2)
└── credit                DECIMAL(15,2)
```

### Template System

**System Templates (is_system=true):**
Used by internal modules, not user-modifiable:
- Post Gaji Bulanan (PayrollService)
- Penyusutan Aset Tetap (FixedAssetService)
- Pelepasan Aset Tetap (FixedAssetService)
- Jurnal Penutup Tahun (FiscalYearClosingService)
- Jurnal Manual (TransactionService)
- Inventory templates (Phase 5)

**User Templates (is_system=false):**
Customizable by users:
- Pendapatan Jasa
- Beban Gaji, Beban Listrik
- Transfer Antar Bank
- Penyetoran Pajak

### Formula Engine

SpEL expressions with secure context:

```java
// Simple percentage
"amount * 0.11"

// Conditional
"amount > 2000000 ? amount * 0.02 : 0"

// Dynamic variables (from PayrollService)
"grossSalary"
"companyBpjs * 0.8"
```

Security: `SimpleEvaluationContext.forReadOnlyDataBinding()` blocks type references, constructors, bean references.

### Extended Features

```sql
-- Project management
project
├── project_milestones
├── project_transactions
└── project_profitability

-- Payroll system
employees
├── salary_components (17 Indonesian components)
├── payroll_runs
└── payroll_details

-- Tax compliance
fiscal_periods
├── tax_deadlines
└── tax_transaction_details

-- Fixed assets
fixed_assets
├── asset_categories
├── depreciation_schedules
└── disposal_records
```

## Security Architecture

### Authentication

- Spring Security with BCrypt
- Session-based (no JWT complexity)
- CSRF protection integrated with HTMX

### Authorization (RBAC)

6 predefined roles with authority-based permissions:

| Role | Access Level |
|------|--------------|
| SUPERADMIN | System administration |
| OWNER | Full company access |
| ACCOUNTANT | Accounting operations |
| BOOKKEEPER | Transaction entry |
| EMPLOYEE | Self-service only |
| VIEWER | Read-only |

Method-level security:
```java
@PreAuthorize("hasAuthority('TRANSACTION_CREATE')")
public void createTransaction() { ... }
```

## Infrastructure

### Deployment Stack

```yaml
# docker-compose.yml
services:
  app:
    image: registry/akunting:${VERSION}
    ports: ["8080:8080"]
    environment:
      - SPRING_PROFILES_ACTIVE=production

  db:
    image: postgres:18
    volumes:
      - postgres_data:/var/lib/postgresql/data

  nginx:
    image: nginx:alpine
    ports: ["80:80", "443:443"]
```

### Directory Structure

```
/opt/accounting-finance/
├── app.jar
├── application.properties
├── documents/
├── backup/
├── scripts/
│   ├── backup.sh
│   ├── backup-b2.sh
│   ├── backup-gdrive.sh
│   ├── backup-upload.sh
│   ├── backup-check.sh
│   ├── backup-lib.sh
│   └── restore.sh
└── .backup-key

/var/log/accounting-finance/
├── app.log
└── backup.log
```

### Backup Strategy (3-2-1)

| Type | Schedule | Retention | Location |
|------|----------|-----------|----------|
| Local | Daily 02:00 | 7 days | VPS `/backup/` |
| B2 | Daily 03:00 | 4 weeks | Backblaze B2 |
| Google Drive | Daily 04:00 | 12 months | Google Drive |

## Testing Strategy

### Test Types

| Type | Tool | Location |
|------|------|----------|
| Unit | JUnit 5 + Mockito | `src/test/java/unit/` |
| Integration | Testcontainers | `src/test/java/integration/` |
| E2E | Playwright | `src/test/java/functional/` |

### Target Coverage

- 80% across all layers
- All critical paths tested
- Playwright for user workflows

## Performance Considerations

### Database Optimization

- HikariCP connection pooling (10 max, 2 idle)
- Optimal indexes for common queries
- JOIN FETCH to prevent N+1
- Materialized views for reports

PostgreSQL tuning for 2GB VPS:

| Setting | Value | Notes |
|---------|-------|-------|
| shared_buffers | 96 MB | 5% of RAM (conservative) |
| effective_cache_size | 256 MB | OS cache estimate |
| work_mem | 4 MB | Sort/hash ops |
| maintenance_work_mem | 48 MB | VACUUM, CREATE INDEX |
| random_page_cost | 1.1 | SSD storage |
| autovacuum_vacuum_scale_factor | 0.05 | Aggressive for OLTP |

### Application Performance

- Virtual threads (Java 25)
- G1GC garbage collector (Java 25 default)
- Dynamic heap sizing (512m-1024m)
- Async processing for heavy reports
- Spring Cache for frequent data
- Lazy loading for JPA relationships

JVM settings:

| Setting | Value | Notes |
|---------|-------|-------|
| Heap | 512-1024 MB | Dynamic sizing |
| GC | G1GC | Java 25 default, optimal for heaps <4GB |

Startup time: ~50-55 seconds on 1 vCPU

### Nginx Performance

- Gzip compression (level 5)
- Keepalive connections (65s timeout)
- Rate limiting (10 req/s per IP)
- Static asset caching (30 days)
- Upstream connection pooling (keepalive 32)

## Monitoring

### Application Metrics

- Spring Boot Actuator (health, metrics)
- JaCoCo (code coverage)
- OWASP Dependency-Check
- Structured logging

### Infrastructure Monitoring

- System metrics (CPU, memory, disk)
- Database performance
- SSL certificate expiry
- Backup verification
