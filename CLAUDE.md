# Claude Instructions

## Project Overview

Indonesian accounting application for small businesses. Spring Boot 4.0 + Thymeleaf + PostgreSQL.

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
| User Manual | `docs/user-manual/*.md` (15 files, 12-section structure) |
| Security Exclusions | `spotbugs-exclude.xml` (SpotBugs false positives with justifications) |
| Entities | `src/main/java/.../entity/` |
| Services | `src/main/java/.../service/` |
| Controllers | `src/main/java/.../controller/` |
| Templates | `src/main/resources/templates/` |
| Migrations (Production) | `src/main/resources/db/migration/` (V001-V004) |
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

# Run tests (excludes DAST by default)
./mvnw test

# Run specific functional test
./mvnw test -Dtest=MfgBomTest

# Run with visible browser (debugging)
./mvnw test -Dtest=MfgBomTest -Dplaywright.headless=false -Dplaywright.slowmo=100

# Run SpotBugs security analysis
./mvnw spotbugs:spotbugs
# Results: target/spotbugsXml.xml or target/site/spotbugs.html

# Run OWASP ZAP DAST security scan (requires Docker)
./mvnw test -Dtest=ZapDastTest -Ddast.enabled=true
# Results: target/security-reports/zap-*.html

# Run full test suite INCLUDING DAST (requires Docker, adds ~2+ min)
./mvnw test -Ddast.enabled=true
```

## Database

- PostgreSQL via Testcontainers (tests)
- Production migrations: V001-V004 (schema + minimal bootstrap)
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

Phase 6 (Security Hardening) in progress!

Phase 5 highlights (complete):
- Product and ProductCategory entities with FIFO/Weighted Average costing
- Inventory transactions: Purchase, Sale, Adjustment, Production In/Out
- FIFO layers and weighted average cost calculation
- BOM (Bill of Materials) for simple production
- Production orders with component consumption and finished goods receipt
- Auto-COGS calculation on sales with margin analysis
- Product profitability reports
- Coffee shop industry seed pack (17 CSV files: products, BOMs, production orders, inventory)
- Playwright functional tests (44 manufacturing tests, all using data-testid)
- DataImportService supports manufacturing/inventory data import

Phase 6 highlights (in progress):
- Field-level encryption (AES-256-GCM) for PII fields
- Document storage encryption with backward compatibility
- Password complexity enforcement (12+ chars, mixed case, numbers, special)
- Account lockout (5 attempts, 30-minute lockout)
- Rate limiting on login and API endpoints
- Comprehensive security audit logging
- Data masking for sensitive fields in UI
- GDPR/UU PDP compliance (DSAR export, anonymization)
- DevSecOps: CodeQL, SonarCloud, OWASP Dependency-Check, ZAP DAST
- Security regression tests (Playwright + JUnit)
- SpotBugs/FindSecBugs audit: ✅ 0 issues (164→0, fixed 33 real vulnerabilities, documented 140 Spring DI false positives)

User Manual (12-section structure complete):
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

Manufacturing Tests Status:
- Coffee shop seed pack: ✅ Complete (industry-seed/coffee-shop/)
- Functional tests: ✅ 44 tests passing (MfgBomTest, MfgProductionTest, MfgCostingTest, etc.)
- Test pattern: All using data-testid locators (zero text/CSS/positional locators)
- Test data initializer: CoffeeTestDataInitializer loads seed pack
- User manual: ✅ Complete (09-industri-manufaktur.md)

Campus/Education Tests Status:
- Campus seed pack: ✅ Complete (industry-seed/campus/)
- Functional tests: ✅ 19 tests passing (CampusBillingTest, CampusPaymentTest, CampusScholarshipTest, CampusReportsTest)
- Test pattern: All using data-testid locators (zero text/CSS/positional locators)
- Test data initializer: CampusTestDataInitializer loads seed pack
- User manual: ✅ Complete (10-industri-pendidikan.md with 9 screenshots)

See `docs/06-implementation-plan.md` for full plan
