# Balaka — Engineering Post-Mortem Measurement

Period 2025-11-19 (first commit) → 2026-08-26 (release 2026.08). Data
snapshot 2026-08-31. Single project, project-level baselines only;
regeneration in §11.

This is a **product-mode** measurement: one developer who is also product
owner and first user, open-source repository, real production deployment.
There is no external QA organisation, no ticket ceremony, and no
requirements counterparty — so the measures here are the ones such a
project natively produces: release containment, maintenance mix,
third-party quality trends, and production incident detection. Metrics
that require a counterparty (requirement churn against a baseline,
decision latency, ticket flow) are structurally absent, not omitted.

Two of the quality feeds are computed by third parties on public
dashboards, so every number in §6–§7 can be audited without trusting this
document:

- SonarCloud: <https://sonarcloud.io/project/overview?id=artivisi_aplikasi-akunting>
- Codecov: <https://app.codecov.io/gh/artivisi/balaka>

## 0. Summary

| Measure | Value |
|---|---|
| Code | 54.2k main Java + 31.0k Thymeleaf template + 1.8k SQL + 1.0k JS non-blank LOC; test tree 73.2k (1.35× main Java); 439 main classes, 223 templates |
| Commits | 993 non-merge over 281 calendar days on 109 active days; 295 fix (30%) |
| Releases | 23 tagged releases: 8 minor lines + 15 patches; patch count per line 7 → 5 → 3 → 0, zero patches since 2026.04 |
| Defect ledger | Commit-first, mostly ticketless: 16 GitHub issues (9 defects), median issue cycle time 0 days (same-day close), max 11 |
| Coverage | Backend line coverage 45.9% → 76.6% (Codecov, 183 measured commits); the climb to 70% took 16 days of deliberate work in January |
| Quality trend | SonarCloud over 11 analyses: tech debt 4,328 → 1,903 min while ncloc grew 37.7k → 82.5k; duplication 3.7% → 2.6%; two regressions, both with assignable causes and both cleared |
| Tests | 3,685 test methods in 222 classes; 136 Playwright functional test classes; ZAP DAST in the suite; SpotBugs 0-issue policy |
| Production incident | Backup pipeline failed silently for 48 days before detection (issues #36, #37) — the project's largest single quality event |
| AI assistance | 855 of 993 commits (86%) carry a `Co-Authored-By: Claude` trailer; 56 commits authored by the Claude account; development AI-assisted throughout |

## 1. Project facts

| Dimension | Value |
|---|---|
| Product | Indonesian double-entry accounting application for small businesses (invoicing, tax compliance incl. PPh 21/PPN/SPT export, payroll, fixed assets, inventory/BOM, bank reconciliation, REST API) |
| Duration | 40 weeks, 2025-11-19 → 2026-08-26; 109 active commit days (39% of calendar) |
| Team | 1 developer (also product owner and operator), AI-assisted; no external QA, BA, or PM |
| Stack | Spring Boot 4 + Thymeleaf + HTMX/Alpine.js, PostgreSQL, Flyway; Playwright + Testcontainers for tests |
| Structure | 439 main Java files (54,208 non-blank LOC), 223 templates (30,969), 5 production Flyway migrations (1,842 SQL), 9 frontend JS/CSS files (974) |
| Test tree | 371 files, 73,217 non-blank LOC (Java tests + test migrations + fixtures); 222 test classes declaring 3,685 test methods, of which 136 classes are Playwright functional tests |
| Scope growth | Roadmap ran phase 0 → phase 20 (core accounting → tax → payroll → assets → inventory → API → reconciliation → analytics → invoicing → recurring → tax data → OpenAPI → fiscal → payroll API → manual → SPT → TER → scheduled payroll → journal API), plus issue-driven insertions (#21–#38, BUG-001–020, FR-001–006) |
| Delivery | 23 tagged releases with release notes (`docs/releases/`), CI on GitHub Actions, automated Ansible deployment to one production target |
| Docs | 151 files, 23,542 non-blank lines (user manual 20 files, operations guide, ADRs, implementation plan) |

Working-tree LOC and file counts: `data/git.json`. The test tree being
1.35× the main Java tree is a deliberate policy (feature completion is
defined by a passing Playwright test); §6 examines whether it paid.

## 2. Sources and method

Everything is computed from four sources, all public:

| Source | Used for |
|---|---|
| Git history (993 non-merge commits, conventional-commit subjects) | taxonomy, monthly mix, fix density, hot files, active days, hours |
| Git tags + `docs/releases/` (23 releases) | cadence, patch containment |
| GitHub issues (16) | external/deferred defect ledger, cycle time |
| SonarCloud + Codecov public APIs | quality and coverage trends, third-party computed |

No LLM-assisted classification was needed: commit types are read from the
conventional-commit prefix (`fix:`, `feat:`, …), and the 231 commits
without a recognised prefix — 188 of them from the pre-convention first
two weeks — are counted as `other`, not guessed.

## 3. Delivery and release containment

With no external test phase, every defect that escapes the suite ships to
production, and every one that matters forces a patch release. The tag
history is therefore the project's containment record.

### 3.1 Patch releases per minor line

| Line | Base tag date | Patches | First patch after | Last patch after | Fix commits shipped in patches |
|---|---|---|---|---|---|
| 2025.12 | 2025-12-06 | 7 | 2 d | 21 d | 46 |
| 2026.01 | 2026-01-03 | 0 | — | — | 0 |
| 2026.02 | 2026-02-18 | 5 | 0 d (same day) | 8 d | 33 |
| 2026.03 | 2026-03-08 ¹ | 3 | 1 d | 11 d | 39 |
| 2026.04 | 2026-04-11 | 0 | — | — | 0 |
| 2026.05 | 2026-05-27 | 0 | — | — | 0 |
| 2026.06 | 2026-06-01 | 0 | — | — | 0 |
| 2026.08 | 2026-08-26 | 0 | — | — | 0 |

¹ The 2026.03 line was first tagged `2026.03.1` directly; it is treated
as the line's base and the remaining three tags as its patches.

Full per-tag table with commit and fix counts: `csv/releases.csv`.

### 3.2 Analysis

- The first release needed seven patches in three weeks; 2026.02 needed a
  same-day patch. From 2026.04 on, **four consecutive minor lines over
  five months shipped without a single patch release.**
- The zero-patch era coincides with three things: coverage reaching its
  75%+ plateau (§6), feature velocity dropping as the roadmap completed
  (§4), and the product entering routine production use. The data cannot
  apportion credit between them — but the era with heavy feature work
  *and* coverage below 75% (Dec–Mar) is exactly the era that needed 15
  patch releases, and the era with neither needed none.
- Minor cadence was roughly monthly while building (Dec–Jun), stretching
  to 46 and then 86 days as the roadmap finished — a maturity signal,
  not an abandonment one (the 2026.08 release carried 35 commits).

## 4. Work rhythm and maintenance mix

### 4.1 Monthly commit mix

| Month | Total | feat | fix | refactor | test | docs | other² |
|---|---|---|---|---|---|---|---|
| 2025-11 | 200 | 4 | 7 | 1 | 0 | 0 | 188 |
| 2025-12 | 249 | 49 | 70 | 25 | 16 | 44 | 45 |
| 2026-01 | 133 | 1 | 43 | 55 | 22 | 3 | 9 |
| 2026-02 | 157 | 36 | 68 | 2 | 2 | 34 | 15 |
| 2026-03 | 108 | 14 | 47 | 3 | 6 | 18 | 20 |
| 2026-04 | 63 | 1 | 38 | 1 | 0 | 11 | 12 |
| 2026-05 | 43 | 18 | 10 | 2 | 8 | 1 | 4 |
| 2026-06 | 6 | 0 | 0 | 0 | 0 | 3 | 3 |
| 2026-07 | 14 | 3 | 8 | 1 | 0 | 1 | 1 |
| 2026-08 | 20 | 1 | 4 | 0 | 6 | 5 | 4 |

² chore, release, ci, build, style, and unclassified. November predates
the conventional-commit discipline, so its split is unreliable.

Readable phases: **build** (Nov–Dec, feature-heavy), **consolidate**
(January: 55 refactor + 22 test commits, almost no features — this is the
coverage push of §6), **build again** (Feb–Mar), **stabilise** (April:
60% of commits are fixes, closing out the BUG series), **maintain**
(May–Aug: single-digit to low-double-digit commit months, mostly
issue-driven).

### 4.2 Solo cadence

- 109 active days out of 281 (39%), spread evenly across the week
  (13–17 active days per weekday — weekends indistinguishable from
  weekdays).
- Commit hours are bimodal: an early block 04:00–07:59 (202 commits) and
  an evening block 20:00–23:59 (236 commits), with a shallower midday
  plateau — the signature of part-time work wrapped around other
  commitments rather than office hours.
- Effort was not time-tracked. Active days is the only honest
  denominator this data supports; person-month productivity claims are
  deliberately not made (§10).

## 5. Defects

### 5.1 Commit-first, ticketless

295 of 993 commits are fixes (30%). Only 16 GitHub issues exist, 9 of
them defects — the tracker is used for externally-reported problems and
deferred work, not as a defect intake. Of 13 closed issues the median
cycle time is **0 days** (same-day close; max 11): when developer,
product owner, and user are one person, the ticket is usually written
after the diagnosis, as a record rather than a request.

Consequence for measurement: ticket-based defect metrics (inflow,
backlog, reopen rate) would see ~5% of this project's defect activity.
The fix-commit stream and the patch-release trail (§3) are the real
ledger. Named-defect series in commit subjects: BUG-001–020 (10 commits
reference a `BUG-n`), issues referenced by 15 commits.

### 5.2 Fix density by area

Fix commits assigned to the area where the commit changed the most lines;
density against current non-blank LOC (`csv/fix_density.csv`):

| Area | LOC | Fix commits | Fix/KLOC |
|---|---|---|---|
| CI workflows | 466 | 19 | 40.8 |
| Build (pom.xml, scripts) | 3,213 | 31 | 9.6 |
| Frontend JS/static | 974 | 8 | 8.2 |
| Deploy (Ansible/Pulumi) | 2,072 | 11 | 5.3 |
| Migrations | 1,842 | 3 | 1.6 |
| Main Java | 54,208 | 77 | 1.4 |
| Test tree | 73,217 | 102 | 1.4 |
| Templates | 30,969 | 32 | 1.0 |

Two readings. First, per line, **infrastructure code is an order of
magnitude more defect-prone than application code** — CI workflows,
build config, and deployment scripts have no test suite, no type checker,
and fail only in situ. The hot-file list agrees: `pom.xml` (22 fix
commits), `ci.yml` (14), `publish-manual.yml` (10) all rank above any
service class. Second, a third of all fix commits land in the test tree
— tests are code and carry their own defect stream (flaky waits,
leaked fixtures, timing).

Hot files: `csv/hot_files.csv`. Top application files:
`UserManualGenerator` (19), `DataImportService` (17),
`TransactionService` (12) — the generator and importer are the two
components that parse loosely-structured external content, which is
where the defects predictably concentrated.

## 6. Testing and coverage

The suite: 3,685 test methods in 222 classes; 136 Playwright functional
classes driving a real browser against a real PostgreSQL (Testcontainers);
ZAP DAST included; feature completion is defined as "verified by a
functional test".

Backend line coverage as computed by Codecov over 183 CI runs
(`csv/codecov_history.csv`, 22,433 tracked lines):

| Date | Coverage | Event |
|---|---|---|
| 2026-01-03 | 45.9% | first measured commit |
| 2026-01-10 | 50.6% | January consolidation begins |
| 2026-01-12 | 63.9% | |
| 2026-01-19 | 70.2% | 70% crossed after 16 days of deliberate work |
| 2026-03-14 | 75.9% | plateau reached |
| 2026-08-26 | 76.6% | current |

The climb from 46% to 70% was a **decision, not a drift**: January's
commit mix (§4.1) is 55 refactor + 22 test + 43 fix commits and one
feature. The plateau at ~76% has held for five months of further change.

Read against §3: every patch release the project ever needed occurred
while coverage was below the plateau; no minor line released after the
plateau (2026.04 onward) has needed one. Correlation, with the confound
already named in §3.2 — but the January investment is the only variable
the developer directly controlled.

Known gap, self-filed as issue #38: `BankReconciliationApiController` at
1.1% line coverage — evidence the plateau is an average, not a floor.

## 7. Code quality and security — third-party computed

SonarCloud, 11 analyses over 9 months (`csv/sonar_history.csv`):

| Date | ncloc | Bugs | Vulns | Smells | Dup % | Debt (min) |
|---|---|---|---|---|---|---|
| 2025-11-28 | 37,674 | 29 | 5 | 526 | 3.7 | 4,328 |
| 2025-12-07 | 52,423 | 43 | 0 | 740 | 3.8 | 5,692 |
| 2026-01-03 | 54,793 | 45 | 0 | 694 | 3.7 | 5,273 |
| 2026-02-03 | 54,214 | 1 | 0 | 42 | 3.4 | 330 |
| 2026-03-07 | 75,250 | 1 | 40 | 201 | 3.8 | 2,104 |
| 2026-04-08 | 79,144 | 0 | 0 | 38 | 2.7 | 237 |
| 2026-05-26 | 79,495 | 0 | 0 | 31 | 2.7 | 227 |
| 2026-06-16 | 80,464 | 106 | 0 | 1,329 | 2.8 | 37,960 |
| 2026-07-09 | 81,540 | 8 | 2 | 770 | 2.6 | 4,011 |
| 2026-08-25 | 82,452 | 2 | 0 | 355 | 2.6 | 1,903 |

(An eleventh analysis on 2026-08-26 repeats the 08-25 values and is
omitted from the table.)

End to end: ncloc up 2.2×, absolute debt down 56%, debt per KLOC down
~5×, duplication 3.7% → 2.6%, security hotspots 100% reviewed since the
second analysis. Locally, a SpotBugs 0-issue policy is enforced in the
build, with every exclusion justified in `spotbugs-exclude.xml`.

The two regressions both have assignable causes visible in the history:

- **2026-03-07, 40 vulnerabilities**: dependency CVEs. Cleared within
  the month by the Spring Boot 4.0.4 upgrade (`8f7b7f2`) and resolution
  of all six Dependabot alerts (`7a59e6d`).
- **2026-06-16, 106 bugs / 633 h debt**: the analysis following the
  Spring Boot 4.1.0 + frontend dependency upgrade (`5fd5a31`) and a
  SonarCloud analyzer re-baseline, in a month with six commits — the
  findings were inherited, not written. Cleared by a dedicated July
  sweep (`cc8637c`, `35c1092`, `73bea13`) back to rating A.

The pattern worth keeping: both spikes were flagged by an external
referee the developer does not control, and both were swept within
weeks. A self-run scanner can be quietly ignored; a public dashboard
badge cannot.

## 8. Production incidents

The project's largest quality event produced no failing test, no Sonar
finding, and no patch release: the **production backup pipeline failed
silently for 48 days** before detection (2026-08-26). The monitoring in
place checked that an upload happened — and a stale archive was
re-uploaded daily, so every check passed while no new backup existed.
Two hardening issues remain open from it:

- #36 — backup uploads never verify archive freshness (the actual hole)
- #37 — backup `manifest.json` invalid JSON (checksum written twice)

A second, smaller operations defect from the same period: renamed
Ansible cron entries left the old entries running as duplicates
(`07d2f8b`), because the cron module keys entries by name.

For a solo-operated product the measure that matters here is **mean time
to detect**, and 48 days is the baseline this project now improves on.
The general lesson generalises beyond backups: every monitor must verify
the *freshness and validity* of the artifact it guards, not the success
of the motion that produces it. Test suites bound the defects you can
express as tests; operations failures live outside that boundary and
need their own instrumentation.

## 9. AI assistance

Development was AI-assisted (Claude Code) for effectively the whole
project, and unlike most published accounts this is verifiable in the
public history rather than self-reported:

- 855 of 993 non-merge commits (86%) carry a `Co-Authored-By: Claude`
  trailer;
- 56 commits are authored by the Claude account directly;
- 34 commits link the originating session URL in a `Claude-Session`
  trailer.

What this measurement can and cannot say: it **can** put third-party
quality evidence next to an AI-assisted codebase — coverage rising to
and holding 76% (§6), absolute tech debt falling 56% while the code
grew 2.2× (§7), four consecutive patch-free releases (§3) — against
the common expectation that AI-assisted code accumulates unreviewed
debt. It **cannot** quantify productivity attribution (no time
tracking, no counterfactual, and per-session cost data is not retained
beyond the tool's retention window), so no tokens-per-line or
cost-per-feature figures are offered.

## 10. Limitations

- Single project, single developer; no organisational baseline. Every
  comparison is internal (early vs late project).
- Effort is not time-tracked. Active commit days (109) is a floor-proxy
  for calendar engagement, not hours; productivity ratios are therefore
  not computed.
- Commit taxonomy relies on self-assigned conventional-commit prefixes;
  the first two weeks (188 commits) predate the discipline and are
  excluded from mix conclusions.
- Codecov history starts 2026-01-03; the first six weeks have no
  coverage measurements. Coverage is backend-only (JaCoCo);
  templates and frontend JS are untracked by it.
- SonarCloud analyses run per push, so the 11 points are unevenly
  spaced; between-analysis excursions are invisible.
- The issue ledger covers a small fraction of defect activity (§5.1);
  no defect-inflow or reopen statistics are possible or offered.
- Patch-release containment (§3) counts only defects severe enough to
  warrant a release; production defects tolerated until the next minor
  are indistinguishable from features in the commit stream.
- Formal sizing (IFPUG FP, UCP, COCOMO) was not applied. The repo is
  public, so a counted size can be produced — and independently checked
  — later.

## 11. Files and regeneration

| File | Produces |
|---|---|
| `_build_git.py` | `data/git.json`, `csv/{commits,monthly_mix,fix_density,hot_files}.csv` |
| `_build_releases.py` | `data/releases.json`, `csv/{releases,containment}.csv` |
| `_build_issues.py` | `data/issues.json`, `csv/issues.csv` (needs `gh` auth) |
| `_build_external.py` | `data/{sonar,codecov}.json`, `csv/{sonar_history,codecov_history}.csv` (public APIs, no token) |

Run from the repo root: `python3 postmortem/_build_<name>.py`. Stdlib
only, no dependencies. Scripts fail loudly on unexpected data rather
than guessing.
