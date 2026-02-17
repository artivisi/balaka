# Claude Instructions

## Project Overview

Indonesian accounting application for small businesses. Spring Boot 4.0 + Thymeleaf + PostgreSQL. Licensed under Apache License 2.0.

## Current Status

- **Phase 0:** ✅ Complete (project setup, auth, CI/CD)
- **Phase 1:** ✅ Complete (Core Accounting MVP)
- **Phase 2:** ✅ Complete (Tax Compliance + Cash Flow)
- **Phase 3:** ✅ Complete (Payroll + RBAC + Employee Self-Service)
- **Phase 4:** ✅ Complete (Fixed Assets)
- **Phase 5:** ✅ Complete (Inventory & Production)
  - 5.1 Product Master: ✅ Complete
  - 5.2 Inventory Transactions: ✅ Complete
  - 5.3 Inventory Reports: ✅ Complete
  - 5.4 Simple Production (BOM): ✅ Complete
  - 5.5 Integration with Sales: ✅ Complete
- **Phase 6:** 🔄 In Progress (Security Hardening)
  - 6.1-6.5: ✅ Complete (Critical fixes, Encryption, Auth hardening, Input validation, Audit logging)
  - 6.6: ✅ Complete (Data Protection)
  - 6.7: ✅ Complete (API Security)
  - 6.8: 🔄 Partial (GDPR/UU PDP - consent management, breach response pending)
  - 6.9: 🔄 Partial (DevSecOps - container security, API fuzzing pending)
  - 6.10: ✅ Complete (Security Documentation)
- **Phase 7:** ⏳ Not Started (API Foundation)
- **Phase 9:** ✅ Complete (Bank Reconciliation)
- See `docs/06-implementation-plan.md` for full plan

## Key Files

| Purpose | Location |
|---------|----------|
| Features & Roadmap | `docs/01-features-and-roadmap.md` |
| Architecture | `docs/02-architecture.md` |
| Operations Guide | `docs/03-operations-guide.md` |
| Tax Compliance | `docs/04-tax-compliance.md` |
| Implementation Plan | `docs/06-implementation-plan.md` |
| ADRs | `docs/adr/` |
| User Manual | `docs/user-manual/*.md` (16 files, 14-section structure) |
| Security Exclusions | `spotbugs-exclude.xml` (SpotBugs false positives with justifications) |
| Entities | `src/main/java/.../entity/` |
| Services | `src/main/java/.../service/` |
| Controllers | `src/main/java/.../controller/` |
| Templates | `src/main/resources/templates/` |
| Migrations (Production) | `src/main/resources/db/migration/` (V001-V007) |
| Test Migrations (Integration) | `src/test/resources/db/test/integration/` (V900-V912) |
| Industry Seed Packs | `industry-seed/{it-service,online-seller}/` (loaded via DataImportService) |
| Functional Tests | `src/test/java/.../functional/` |
| Infrastructure (Pulumi) | `deploy/pulumi/` |
| Configuration (Ansible) | `deploy/ansible/` |

## Development Guidelines

1. **Feature completion criteria:** Item is only checked when verified by Playwright functional test
2. **No fallback/default values:** Throw errors instead of silently handling missing data
3. **Technical language:** No marketing speak, strictly technical documentation
4. **Test-driven:** Write functional tests for new features
5. **Migration strategy:** Modify existing migrations instead of creating new ones (pre-production)
6. **Code quality:** Maintain SpotBugs 0-issue status. Any new exclusions in `spotbugs-exclude.xml` must have comprehensive justifications with mitigation details

## Running the App

```bash
# First-time setup on Ubuntu (install Playwright browsers)
./setup-ubuntu.sh

# Run all tests (unit, integration, functional, DAST)
# Requires Docker for Testcontainers (PostgreSQL, ZAP)
./mvnw test

# Run specific functional test
./mvnw test -Dtest=MfgBomTest

# Run with visible browser (debugging)
./mvnw test -Dtest=MfgBomTest -Dplaywright.headless=false -Dplaywright.slowmo=100

# Run SpotBugs security analysis
./mvnw spotbugs:check
# Results: target/spotbugsXml.xml

# Run only DAST tests
./mvnw test -Dtest=ZapDastTest
# Results: target/security-reports/zap-*.html

# Run DAST in quick mode (passive scan only, ~1 min)
./mvnw test -Dtest=ZapDastTest -Ddast.quick=true
```

## Database

- PostgreSQL via Testcontainers (tests)
- Production migrations: V001-V007 (schema + minimal bootstrap + bank recon)
- Test data:
  - Functional tests: NO migrations - all data loaded via `@TestConfiguration` initializers from industry-seed/ packs
  - Integration tests: V900-V912 (preloaded data for unit/service/security tests)
- Industry seed packs: `industry-seed/{it-service,online-seller,coffee-shop}/seed-data/` (COA, templates, products, BOMs, etc.)

## Architecture

```
User → Controller (MVC) → Service → Repository → PostgreSQL
         ↓
    Thymeleaf Templates (HTMX + Alpine.js)
```

## Current Focus

Phase 9 (Bank Reconciliation) complete. Phase 6 (Security Hardening) partially complete.

Phase 9 highlights (complete):
- Bank statement import (CSV parsing with configurable parsers for BCA, Mandiri, BNI, BSI, CIMB)
- Custom parser config for unsupported banks
- Auto-matching: 3-pass algorithm (Exact → Fuzzy Date → Keyword)
- Manual match, Bank-Only, Book-Only classification
- Reconciliation reports (print/export)
- 4 enums, 5 entities, 5 repositories, 4 services, 2 controllers, ~10 templates
- V006 (schema) + V007 (seed) migrations
- 5 permissions: VIEW, IMPORT, MATCH, COMPLETE, CONFIG
- 16 Playwright functional tests passing
- User manual: ✅ Complete (14-rekonsiliasi-bank.md with 10 screenshots)

User Manual (14-section structure):
- 01-setup-awal.md: Setup & Administration
- 02-tutorial-akuntansi.md: Basic Accounting Tutorial (crown jewel)
- 03-aset-tetap.md: Fixed Assets & Depreciation
- 04-perpajakan.md: Tax Compliance
- 05-penggajian.md: Payroll & BPJS
- 06-pengantar-industri.md: Industry Overview
- 07-industri-jasa.md: Service Industry
- 08-industri-dagang.md: Trading Industry
- 09-industri-manufaktur.md: Manufacturing
- 10-industri-pendidikan.md: Education
- 11-keamanan-kepatuhan.md: Security & Compliance
- 12-lampiran-*.md: Appendices (glosarium, template, amortisasi, akun)
- 13-bantuan-ai.md: AI-Assisted Transactions
- 14-rekonsiliasi-bank.md: Bank Reconciliation

See `docs/06-implementation-plan.md` for full plan
