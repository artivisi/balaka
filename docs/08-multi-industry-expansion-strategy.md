# Multi-Industry Expansion Strategy

## Overview

This document outlines the strategy for making this accounting application generic and adaptable to multiple industries through API-based integration with domain-specific applications.

## Architecture Vision

```
┌─────────────────────┐     ┌─────────────────────┐     ┌─────────────────────┐
│   Grant Management  │     │   Inventory App     │     │   POS App           │
│   (University)      │     │   (Retail)          │     │   (Restaurant)      │
└──────────┬──────────┘     └──────────┬──────────┘     └──────────┬──────────┘
           │                           │                           │
           │ POST /api/transactions    │                           │
           └───────────────────────────┴───────────────────────────┘
                                       │
                                       ▼
                        ┌──────────────────────────┐
                        │   Core Accounting API    │
                        │   (This App)             │
                        │                          │
                        │   - COA Management       │
                        │   - Journal Templates    │
                        │   - Template Execution   │
                        │   - Reports              │
                        └──────────────────────────┘
```

## Design Principles

### 1. Core Accounting Stays Generic

The core accounting app handles only accounting primitives:
- Chart of Accounts (COA)
- Journal Entries (double-entry bookkeeping)
- Journal Templates (reusable transaction patterns)
- Template Execution (create journal entries from templates)
- Reports (trial balance, balance sheet, income statement, etc.)

### 2. Domain Logic Lives in Domain Apps

Industry-specific business rules stay in separate applications:

| Domain App | Owns |
|------------|------|
| Grant Management | Grants, budgets, compliance, fund restrictions |
| Inventory | Stock, COGS calculation, warehouse management |
| POS | Sales, receipts, daily settlement |
| Payroll (non-ID) | Country-specific payroll rules |
| Real Estate | Property, tenants, lease management |
| Manufacturing | BOM, work orders, production costing |

### 3. Integration via API

Domain apps call the accounting API to record financial transactions. Each domain app:
- Validates business rules locally
- Calls accounting API to create journal entries
- Handles failures with retry/SAF pattern

## Why Not Add Modules to This App?

### Fund Accounting Example

Universities need to track restricted vs unrestricted funds. This requires:

| Requirement | Can Template Solve? | Why Not? |
|-------------|---------------------|----------|
| Track balance by fund | No | Need fund dimension on every journal line |
| Validate fund restrictions | No | Need business logic, not just math |
| Fund-based reporting | No | Need additional query dimension |

### Grant Accounting Example

Grants have compliance rules:

| Requirement | Can Template Solve? | Why Not? |
|-------------|---------------------|----------|
| Budget enforcement | No | Need budget tracking per category |
| Cost allowability | No | Need policy rules engine |
| Grant lifecycle | No | Need status, dates, workflow |
| Compliance reporting | No | Need grant-specific report formats |

**Conclusion:** Templates are recording mechanisms. Fund/grant accounting requires tracking dimensions, validation rules, and compliance logic that don't belong in a generic accounting system.

## Integration Pattern: Store and Forward (SAF)

### Why SAF?

Distributed transactions are complex. Instead of two-phase commit, we use eventual consistency:

```
Domain App                          Accounting API
    │
    ├── 1. Validate business rules locally
    ├── 2. Save to local SAF queue (status=PENDING)
    ├── 3. POST to Accounting API
    │       ├── Success → Update SAF status=COMPLETED
    │       └── Failure → Keep PENDING, retry later
    │
    └── 4. Background job retries PENDING entries
```

### Benefits

- Domain app is source of truth for domain data
- Accounting app is source of truth for journal entries
- Each system owns its domain
- Failures don't block business operations
- Retry handles transient failures

### Idempotency

Critical for SAF pattern. Client generates unique key per transaction:

```json
POST /api/transactions
{
    "idempotencyKey": "grant-app-2024-001-expense-123",
    ...
}
```

If client retries same request, server returns existing transaction instead of creating duplicate.

## API Specification

### Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | /api/transactions | Execute template, create transaction |
| GET | /api/transactions/{id} | Get transaction by ID |
| GET | /api/transactions?idempotencyKey={key} | Check if transaction exists |
| GET | /api/templates | List available templates |
| GET | /api/templates/{code} | Get template details |
| GET | /api/accounts | List chart of accounts |
| GET | /api/accounts/{code} | Get account details |
| GET | /api/reports/trial-balance | Trial balance report |
| GET | /api/reports/balance-sheet | Balance sheet report |
| GET | /api/reports/income-statement | Income statement report |

### Transaction Request

```json
POST /api/transactions
{
    "idempotencyKey": "grant-app-2024-001-expense-123",
    "templateCode": "EXPENSE-CASH",
    "transactionDate": "2024-12-03",
    "description": "Lab equipment purchase - Grant NSF-2024-001",
    "amount": 50000000,
    "variables": {
        "expenseAccount": "5.1.01",
        "cashAccount": "1.1.01"
    },
    "metadata": {
        "sourceSystem": "grant-app",
        "sourceId": "expense-123",
        "grantNumber": "NSF-2024-001"
    }
}
```

### Transaction Response

```json
{
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "transactionNumber": "TRX-2024-0001",
    "status": "POSTED",
    "journalEntryId": "550e8400-e29b-41d4-a716-446655440001",
    "createdAt": "2024-12-03T10:30:00Z"
}
```

### Error Response

```json
{
    "error": "TEMPLATE_NOT_FOUND",
    "message": "Template with code 'INVALID-CODE' not found",
    "timestamp": "2024-12-03T10:30:00Z"
}
```

## Authentication

### API Key Authentication

Simple approach for server-to-server communication:

```
Authorization: Bearer <api-key>
```

API keys are:
- Generated per client application
- Stored hashed in database
- Scoped to specific permissions (read-only, read-write)
- Rotatable without downtime

### OAuth2 (Future)

For more complex scenarios:
- Multiple client apps with different permissions
- User-context API calls
- Token refresh and revocation

## Domain App Integration Examples

### University Grant Management

```
Grant App validates:
- Grant is active (start date <= today <= end date)
- Cost category is allowable
- Budget has sufficient balance
- Expense complies with grant terms

Then calls Accounting API:
POST /api/transactions
{
    "templateCode": "GRANT-EXPENSE",
    "amount": 50000000,
    "variables": {
        "expenseAccount": "5.1.01.001",  // Lab Equipment
        "fundAccount": "1.1.01.003"       // Grant Bank Account
    }
}
```

### Retail Inventory

```
Inventory App calculates:
- COGS using FIFO/weighted average
- Inventory valuation adjustment

Then calls Accounting API:
POST /api/transactions
{
    "templateCode": "COGS-RECOGNITION",
    "amount": 15000000,
    "variables": {
        "cogsAccount": "5.1.01",
        "inventoryAccount": "1.1.05"
    }
}
```

### Restaurant POS

```
POS App calculates:
- Daily sales total
- Payment method breakdown
- Tips distribution

Then calls Accounting API:
POST /api/transactions
{
    "templateCode": "DAILY-SALES",
    "amount": 25000000,
    "variables": {
        "cashAccount": "1.1.01",
        "salesAccount": "4.1.01",
        "taxPayable": "2.1.03"
    }
}
```

## Deployment Options

### Single Instance (Small Business)

- One accounting instance
- Domain apps call same instance
- Simplest deployment

### Multi-Tenant (SaaS)

- Single accounting instance with tenant isolation
- Each tenant has own COA, templates, data
- API key scoped to tenant

### Dedicated Instance (Enterprise)

- Separate accounting instance per customer
- Full data isolation
- Custom configuration per instance

## What Stays in Core App

| Feature | Status | Notes |
|---------|--------|-------|
| COA Management | Core | UI + API |
| Journal Templates | Core | UI + API |
| Template Execution | Core | API primary |
| Journal Entries | Core | Created via templates |
| Basic Reports | Core | UI + API |
| Payroll (ID) | Optional Module | Indonesian-specific |
| Tax Compliance (ID) | Optional Module | Indonesian-specific |

## What Becomes Domain Apps

| Domain | Features |
|--------|----------|
| Grant Management | Grants, funds, budgets, compliance |
| Inventory | Stock, COGS, warehouses |
| POS | Sales, receipts, shifts |
| Payroll (non-ID) | Country-specific rules |
| Fixed Assets | Depreciation, disposal (could stay in core) |
| Budgeting | Budget vs actual (could stay in core) |

## Implementation Roadmap

### Phase 1: API Foundation

1. Add REST API controllers for core operations
2. Implement idempotency key on Transaction entity
3. Add API key authentication
4. Generate OpenAPI documentation
5. Integration tests for API endpoints

### Phase 2: API Enhancements

1. Report API endpoints
2. Pagination and filtering
3. Rate limiting
4. Audit logging for API calls
5. API versioning strategy

### Phase 3: Reference Domain App

1. Build simple domain app as reference implementation
2. Demonstrate SAF pattern
3. Document integration patterns
4. Provide client SDK (optional)

## Success Criteria

1. Domain apps can record transactions without knowing accounting details
2. Accounting app has no industry-specific code
3. Adding new industry requires only new domain app + templates
4. API handles failures gracefully with idempotency
5. Reports aggregate all transactions regardless of source

## Risks and Mitigations

| Risk | Mitigation |
|------|------------|
| API performance | Async processing, caching |
| Data consistency | Idempotency keys, SAF pattern |
| Security | API keys, rate limiting, audit logs |
| Complexity | Good documentation, reference implementations |
| Version compatibility | API versioning, deprecation policy |
