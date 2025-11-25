# Claude Instructions

## Project Overview

Indonesian accounting application for small businesses. Spring Boot 4.0 + Thymeleaf + PostgreSQL.

## Current Status

- **Phase 0:** ✅ Complete (project setup, auth, CI/CD)
- **Phase 1:** 🔄 In Progress (Core Accounting MVP)
  - 1.1 COA: ✅ Complete
  - 1.2 Journal Entries: ✅ Complete
  - 1.3 Basic Reports: ✅ Complete
  - 1.4 Journal Templates: ✅ Complete
  - 1.5 Transactions: ✅ Complete
  - 1.6 Formula Support: ✅ Complete
  - 1.7 Template Enhancements: ✅ Complete
  - 1.7.5 HTMX Optimization: ✅ Complete
  - 1.8 Amortization Schedules: ✅ Complete
  - 1.9 Project Tracking: ⏳ Next - See `docs/06-implementation-plan.md`
  - 1.10 Dashboard KPIs: ⏳ Pending
  - See `docs/06-implementation-plan.md` for full plan

## Key Files

| Purpose | Location |
|---------|----------|
| Implementation Plan | `docs/06-implementation-plan.md` |
| Project Tracking TODO | `TODO-PROJECT-TRACKING.md` |
| Entities | `src/main/java/.../entity/` |
| Services | `src/main/java/.../service/` |
| Controllers | `src/main/java/.../controller/` |
| Templates | `src/main/resources/templates/` |
| Migrations | `src/main/resources/db/migration/` |
| Functional Tests | `src/test/java/.../functional/` |

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
- Flyway migrations: V001-V007
- Seed data: IT Services COA, admin user (admin/admin)

## Architecture

```
User → Controller (MVC) → Service → Repository → PostgreSQL
         ↓
    Thymeleaf Templates (HTMX + Alpine.js)
```

## Current Focus

Next: Project Tracking (1.9) - See `TODO-PROJECT-TRACKING.md`:
1. Database schema (clients, projects, milestones, payment_terms, invoices)
2. Client management (entity, service, controller, UI)
3. Project management with milestones
4. Payment terms and invoice generation
5. Transaction-project linking
6. Profitability reports (project, client, cost overrun)
7. Functional tests

Then: Dashboard KPIs (1.10)
