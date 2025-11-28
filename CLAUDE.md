# Claude Instructions

## Project Overview

Indonesian accounting application for small businesses. Spring Boot 4.0 + Thymeleaf + PostgreSQL.

## Current Status

- **Phase 0:** ✅ Complete (project setup, auth, CI/CD)
- **Phase 1:** ✅ Complete (Core Accounting MVP)
  - 1.1 COA: ✅ Complete
  - 1.2 Journal Entries: ✅ Complete
  - 1.3 Basic Reports: ✅ Complete
  - 1.4 Journal Templates: ✅ Complete
  - 1.5 Transactions: ✅ Complete
  - 1.6 Formula Support: ✅ Complete
  - 1.7 Template Enhancements: ✅ Complete
  - 1.7.5 HTMX Optimization: ✅ Complete
  - 1.8 Amortization Schedules: ✅ Complete
  - 1.9 Project Tracking: ✅ Complete
  - 1.10 Dashboard KPIs: ✅ Complete
  - 1.11 User Manual: ✅ Complete
  - 1.12 Data Import: ✅ Complete
  - 1.13 Deployment & Operations: ✅ Complete
- **Phase 2:** ✅ Complete (Tax Compliance + Cash Flow)
  - 2.0-2.10: All complete (Refactoring, Documents, Telegram, Tax, Reports, Fiscal Periods, Tax Calendar, Backup/Restore, Cash Flow Statement)
- **Phase 3:** 🚧 In Progress (Payroll + RBAC + Employee Self-Service)
  - 3.1 Employee Management: ✅ Complete
  - 3.2 Salary Components: ✅ Complete
  - 3.3 BPJS Calculation: ✅ Complete
  - 3.4 PPh 21 Calculation: ✅ Complete
  - 3.5 Payroll Processing: ✅ Complete
  - 3.6 Payroll Reports: ⏳ Next
  - 3.7 User Management & RBAC: ⏳ Pending (additive permissions, EMPLOYEE role)
  - 3.8 Employee Self-Service: ⏳ Pending (My Payslips, My Bukti Potong)
- **Phase 4:** Analytics & Reconciliation (Tags, Trends, Alerts, Bank Recon)
- **Phase 5:** Assets & Budget
- See `docs/06-implementation-plan.md` for full plan

## Key Files

| Purpose | Location |
|---------|----------|
| Implementation Plan | `docs/06-implementation-plan.md` |
| User Manual | `docs/user-manual/*.md` |
| Entities | `src/main/java/.../entity/` |
| Services | `src/main/java/.../service/` |
| Controllers | `src/main/java/.../controller/` |
| Templates | `src/main/resources/templates/` |
| Migrations | `src/main/resources/db/migration/` |
| Functional Tests | `src/test/java/.../functional/` |
| Infrastructure (Pulumi) | `deploy/pulumi/` |
| Configuration (Ansible) | `deploy/ansible/` |

## Development Guidelines

1. **Feature completion criteria:** Item is only checked when verified by Playwright functional test
2. **No fallback/default values:** Throw errors instead of silently handling missing data
3. **Technical language:** No marketing speak, strictly technical documentation
4. **Test-driven:** Write functional tests for new features
5. **Migration strategy:** Modify existing migrations instead of creating new ones (pre-production)

## Running the App

```bash
# Run tests
./mvnw test

# Run specific functional test
./mvnw test -Dtest=ChartOfAccountSeedDataTest

# Run with visible browser (debugging)
./mvnw test -Dtest=ChartOfAccountSeedDataTest -Dplaywright.headless=false -Dplaywright.slowmo=100
```

## Database

- PostgreSQL via Testcontainers (tests)
- Flyway migrations: V001-V010
- Seed data: IT Services COA, admin user (admin/admin)

## Architecture

```
User → Controller (MVC) → Service → Repository → PostgreSQL
         ↓
    Thymeleaf Templates (HTMX + Alpine.js)
```

## Current Focus

Phase 3 (Payroll) in progress.

Phase 3.1-3.5 complete:
- Employee Management with PTKP status
- Salary Components (17 preloaded Indonesian components)
- BPJS Calculation (Kesehatan + Ketenagakerjaan)
- PPh 21 Calculation (progressive rates, PTKP deduction)
- Payroll Processing (create, calculate, approve, post workflow)

Phase 3.5 highlights:
- PayrollRun/PayrollDetail entities for monthly payroll
- Integration with Transaction/Template system (domain separation)
- Extended FormulaContext with generic variables map for external modules
- Payroll journal template with formula variables (grossSalary, companyBpjs, netPay, totalBpjs, pph21)
- 18 Playwright functional tests

Next: Phase 3.6 (Payroll Reports)

See `docs/06-implementation-plan.md` for full plan
