# Balaka

[![CI](https://github.com/artivisi/balaka/actions/workflows/ci.yml/badge.svg)](https://github.com/artivisi/balaka/actions/workflows/ci.yml)
[![codecov](https://codecov.io/gh/artivisi/balaka/graph/badge.svg)](https://codecov.io/gh/artivisi/balaka)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=artivisi_aplikasi-akunting&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=artivisi_aplikasi-akunting)
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=artivisi_aplikasi-akunting&metric=security_rating)](https://sonarcloud.io/summary/new_code?id=artivisi_aplikasi-akunting)
[![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=artivisi_aplikasi-akunting&metric=sqale_rating)](https://sonarcloud.io/summary/new_code?id=artivisi_aplikasi-akunting)

Accounting application for Indonesian small businesses. Spring Boot 4.0.3 + Thymeleaf + PostgreSQL.

**Production-ready features:**
- ✅ Core accounting (Chart of Accounts, journal entries, financial reports)
- ✅ Indonesian tax compliance (PPN, PPh 21/23/4(2), e-Faktur, e-Bupot)
- ✅ Payroll with BPJS and automatic PPh 21 calculation
- ✅ Fixed assets with depreciation (straight-line, declining balance)
- ✅ Inventory & simple production (FIFO/weighted average, BOM, production orders)
- ✅ Security hardening (AES-256-GCM encryption, audit logging, RBAC, CSP headers)
- ✅ DevSecOps (CodeQL, SpotBugs, OWASP ZAP DAST, Dependency-Check)

**Industry support:**
- IT Services & Consulting
- Online Sellers (marketplace integration ready)
- Manufacturing (coffee shops, F&B with BOM)
- Education (universities, student billing & scholarships)

## Quick Start

```bash
# Prerequisites: Java 25, Docker

# Run tests
./mvnw test

# Run with visible browser (debugging)
./mvnw test -Dtest=ChartOfAccountSeedDataTest -Dplaywright.headless=false -Dplaywright.slowmo=100

# Run OWASP ZAP DAST security scan - quick mode (~5 min, passive only)
./mvnw test -Dtest=ZapDastTest -Ddast.enabled=true -Ddast.quick=true

# Run full DAST with active scanning (~20 min, SQLi/XSS/path traversal)
./mvnw test -Dtest=ZapDastTest -Ddast.enabled=true

# Run SpotBugs security analysis
./mvnw spotbugs:spotbugs
```

## Documentation

| Document | Description |
|----------|-------------|
| [User Manual](https://artivisi.com/balaka/) | End-user documentation (Indonesian) |
| [Features & Roadmap](docs/01-features-and-roadmap.md) | Current features and future plans |
| [Architecture](docs/02-architecture.md) | Tech stack, data model, infrastructure |
| [Operations Guide](docs/03-operations-guide.md) | Deployment, release, backup/restore |
| [Tax Compliance](docs/04-tax-compliance.md) | Indonesian tax handling |
| [Security Testing](docs/05-penetration-testing-checklist.md) | Penetration testing checklist |
| [Implementation Plan](docs/06-implementation-plan.md) | Detailed implementation status |
| [ADRs](docs/adr/) | Architecture decision records |
| [SonarCloud](https://sonarcloud.io/project/overview?id=artivisi_aplikasi-akunting) | Code quality & security analysis |

## Project Status

**All 16 phases complete.** Core accounting, tax compliance, payroll, fixed assets, inventory & production, security hardening, API foundation, bank reconciliation, analytics, invoicing, recurring transactions, tax data management, OpenAPI, fiscal adjustments, payroll API, user manual.

- 3,600+ tests, 80%+ line coverage
- 4 industry seed packs, 20-file user manual
- SpotBugs/FindSecBugs: 0 issues
- OWASP ZAP DAST: 0 HIGH, 0 MEDIUM

See [Features & Roadmap](docs/01-features-and-roadmap.md) for complete feature list and [Implementation Plan](docs/06-implementation-plan.md) for detailed status.

## License

[Apache License 2.0](LICENSE)
