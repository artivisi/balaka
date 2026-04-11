# Agent Pipeline — Driving Balaka With an Autonomous LLM Agent

This directory shows how to drive Balaka with an autonomous large-language-model agent that reads unstructured source documents — invoices, bills, payment confirmations — and posts the corresponding bookkeeping transactions through Balaka's REST API. It is intended for two audiences: Indonesian SME operators who want to do their own books without hiring an in-house accountant, and researchers reproducing the companion results in the paper **"Balaka: An Open-Source Accounting Platform for Indonesian Islamic SMEs — Architecture, Security, and Inclusive Design"** (IFESDC 2026).

The artifact is structured around one worked example: an IT services consultancy running on the `it-service` industry seed pack, processing three synthetic source documents through the full `parsed` → `reviewed` → `posted` lifecycle. Everything in this directory — the runbook template, the worked example, the invocation recipe — is reproducible against any Balaka instance that has imported a compatible seed pack.

## 1. What this is

Balaka exposes every operation a human can perform through the web interface as an equivalent REST API call, with identical validation, identical audit trail, and identical fiscal-period locking. An agent runtime with filesystem and HTTP capabilities — Claude Code, Cowork, Antigravity, or any comparable system — can therefore perform the end-to-end bookkeeping task: read a source document, classify it against a mapping of source patterns to journal templates, build a draft transaction, surface it to a human for review, and post it to the ledger on approval. This directory contains the reference pattern for doing that: a sanitised runbook template, three worked source documents with their parsed outputs, and a recipe for wiring up an agent runtime.

The practical argument for why this matters: the bookkeeping bottleneck for Indonesian SMEs has historically been *classification and data entry from unstructured documents*, not arithmetic or regulatory complexity. Accountants have been able to do the classification because they were trained for it; SME operators without that training have not. An agent capable of reading documents and choosing templates absorbs the classification work. Combined with Balaka's industry seed packs, which ship the institution's chart of accounts and template library as ready-to-import data, the arrangement shifts the marginal staffing requirement for compliant digital bookkeeping: the institution no longer needs an in-house accountant to do the work, though it still needs an operator willing to review what the agent produces.

## 2. Architectural Commitment: API-Only Posting

**Agents post through the same REST API any third-party integration would use, never through direct database access.** This is the one non-negotiable rule of the pipeline. Every guarantee the ledger offers — double-entry balance enforcement, template-based journal derivation, fiscal-period locking, audit logging of who posted what and when — is implemented in the application layer, above the database, and can only be enforced if posting passes through it. An agent that bypasses the API would produce transactions that are indistinguishable from valid ones on the wire but have not been subjected to those checks. The rule exists specifically to make that class of bug impossible by construction.

Balaka ships nineteen dedicated REST API controllers, generated with springdoc-openapi. The OpenAPI specification is served at runtime at `/v3/api-docs` (JSON) and browsable via Swagger UI at `/swagger-ui.html`. The controllers relevant to the agent pipeline are:

| Controller | What it does |
|---|---|
| `DeviceAuthApiController` | OAuth 2.0 device authorisation flow (RFC 8628) for headless clients. No bearer token required; used to obtain one. |
| `ChartOfAccountApiController` | Read the leaf accounts and account hierarchy of the institution. |
| `TemplateApiController` | Read the journal template library, including formulas, variables, and account slots. |
| `DraftTransactionApiController` | Create, update, approve, and reject draft transactions. The main endpoint the pipeline posts to. |
| `TransactionApiController` | Create and manage committed transactions. |

The remaining fourteen controllers cover bank reconciliation, bills, documents, employees, payroll, salary components, financial analysis, fiscal adjustments, tax details, tax export, data import, and the three analysis endpoints. They are not required for the expense-posting pipeline described here but are available to an agent that needs to drive other parts of the system (a payroll agent, a tax-export agent, a reconciliation agent). The full list lives under [`src/main/java/com/artivisi/accountingfinance/controller/api/`](../../src/main/java/com/artivisi/accountingfinance/controller/api/).

Authentication for a headless agent uses the OAuth 2.0 device authorisation flow implemented on `DeviceAuthApiController`. The handshake is described in `agent-invocation.md` §1.

## 3. The Five-Step Pipeline

The pipeline is five steps and four transaction states.

| Step | Who owns it | What happens |
|---|---|---|
| **1. Capture** | Human or automation | A source document lands in the pipeline inbox: an invoice PDF, a bank-statement screenshot, a payment confirmation email. Naming convention is up to the institution. |
| **2. Parse** | Agent | The agent reads the document, extracts amounts, dates, vendor identifiers, and any structured fields. It looks up the matching entry in the runbook (see §4). It builds a draft-transaction payload conforming to the `CreateDraftRequest` shape. It writes the payload to `parsed/` with `status = "parsed"`. |
| **3. Review** | Human | The operator reads the parsed file and its source document side by side, confirms amounts, mapping, and accounts, then flips `status` to `reviewed`. Corrections happen here. |
| **4. Post** | Agent | The agent sees `status = "reviewed"` and calls `POST /api/drafts` followed by `POST /api/drafts/{id}/approve` to commit the draft to the ledger. On success, `status` becomes `posted` and `postedTransactionId` is recorded. |
| **5. Archive** | Agent or automation | Raw source files for which the JSON is now the authoritative record are deleted or compressed. Statement-style documents (bank statements, card statements) that multiple transactions derive from are kept for period-level verification. |

The four explicit transaction states — `parsed`, `reviewed`, `posted`, `skipped` (plus `error` for irrecoverable failures) — draw the boundary between agent responsibility and human responsibility at exactly the point where it makes sense. The agent owns document interpretation and ledger posting. The human owns judgment: is this amount right, is this the correct template, is this even a transaction the institution wants to record. An auditor inspecting the ledger cannot distinguish agent-posted transactions from human-posted ones, and need not.

## 4. The Runbook

`runbook.template.json` is the data file that captures, as institution-specific configuration, the mapping from source-document patterns to Balaka journal templates. The agent reads the runbook once at startup. Every per-document decision — which template to use, which expense account to debit, which line order the BANK account sits on, whether to skip or net — is answered by looking up an entry in the runbook.

The runbook is the place where institution-specific accounting policy lives. Everything else in the pipeline — the parser, the loop, the API client, the state transitions — is generic. Because policy is a single data file, an accountant can audit or edit it without reading code, and an operator can version it under git alongside the rest of the institution's configuration.

The shipped file in this directory is a **template**: it uses string names for templates and accounts rather than UUIDs, and it ships with seven representative vendor mappings for an IT services consultancy. A working institutional runbook substitutes real UUIDs (by calling `GET /api/drafts/templates` and `GET /api/drafts/accounts` on the running Balaka instance) and extends the vendor mappings to cover the institution's actual bank-statement line formats. The `setupChecklist` section of the template enumerates the substitution steps.

## 5. Worked Example

See [`worked-example/`](./worked-example/). Three synthetic source documents — a cloud-hosting invoice, an office electricity bill, and a combined BPJS Kesehatan + Ketenagakerjaan payment confirmation — are presented as an agent would receive them in the wild, along with the parsed draft payloads the agent produces after applying the runbook. The example runs on the `it-service` industry seed pack. Each source file includes a reviewer checklist documenting exactly what a human verifies before promoting the draft from `parsed` to `reviewed`, so the human/agent boundary is explicit and auditable.

## 6. Scope and Non-Goals

This pipeline records transactions through Balaka's API. It does **not** submit tax filings to Coretax. Per [ADR-003 §3.2](../../docs/adr/003-tax-compliance.md), Balaka's core product exports Coretax-compatible files (e-Faktur CSV, e-Bupot, SPT data formats) for manual upload by the user; direct integration with PJAP-mediated submission endpoints is treated as a separate custom engagement. Any agent pipeline described here is therefore a *recording* pipeline, not a *filing* pipeline. The institution still uploads the exported files to Coretax or to its chosen PJAP the same way it would if bookkeeping were done entirely by hand.

This pipeline also does not:

- Attempt to quantify the human-to-agent effort split. Balaka has no measured data on this and the paper is careful not to invent numbers. The responsibility split — agent classifies and posts, human reviews — is structural; the quantity of human time saved is an empirical question we do not claim to have answered.
- Prescribe a specific agent runtime. The pipeline is designed so that any runtime capable of reading local files and making authenticated HTTP calls can drive it, and `agent-invocation.md` gives the wiring shape for three current runtimes.
- Replace accounting competence entirely. The institution still needs someone who recognises when a sale has occurred, can spot a misclassification in a draft transaction, and knows when to escalate. What is eliminated is the need for that person to be an in-house accountant authoring templates from scratch.

## 7. Reproducing the §3.5 Results

A researcher wanting to reproduce the pipeline pattern described in §3.5 of the paper follows these steps:

1. **Start a Balaka instance.** Any supported deployment target works — a local development instance, a Docker Compose setup, or a VPS deployment. See the top-level `README.md` for deployment instructions.
2. **Import the IT Services seed pack.** Either through the Data Import screen in the web UI or through `POST /api/data-import` with the seed-pack ZIP. Confirm that the seed pack's templates (`Bayar Beban Cloud & Server`, `Bayar Beban Listrik`, `Bayar Beban Telekomunikasi`, `Bayar Beban Operasional`, `Bayar BPJS`, `Beban Admin Bank`) appear in `GET /api/drafts/templates`.
3. **Obtain an access token.** Register an agent client and run the OAuth 2.0 device authorisation handshake against `/api/device/code` and `/api/device/token`. See `agent-invocation.md` §1 for the sequence.
4. **Resolve the runbook placeholders.** Copy `runbook.template.json` to an institutional copy (e.g. `runbook.json`). For each template named in `vendorMappings`, substitute the real template UUID. For each entry in `bankAccounts`, substitute the real chart-of-accounts UUID.
5. **Load an agent runtime.** Any agent capable of reading local files and making authenticated HTTP calls will do. Point it at the institutional runbook, the `worked-example/` directory as an inbox, and the Balaka base URL.
6. **Run the pipeline.** The agent should produce three parsed JSON files matching `worked-example/parsed/*.json` in shape. Review them, flip `status` to `reviewed`, and observe the agent post them and update `status` to `posted`. Inspect the resulting transactions in the Balaka web UI — they should be indistinguishable from transactions created through the web form, with identical journal entries and identical audit log entries.

Known reproducibility friction: the IT Services seed pack ships a single combined `Bayar BPJS` DETAILED template with two named variables (`bpjsKesehatan`, `bpjsTenagakerja`), not two single-variable templates. Institutions that post BPJS Kesehatan and Ketenagakerjaan on different days must either adopt a combined-payment workflow or customise the template library before running the pipeline. This is noted in `worked-example/03-bpjs-payment.md` and in `runbook.template.json` under `setupChecklist`.

## 8. License

Apache License 2.0, matching the rest of the Balaka repository. See the top-level [`LICENSE`](../../LICENSE) file for the full text.
