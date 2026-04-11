# Agent Invocation Recipe

This document describes how an autonomous agent runtime — Claude Code, Cowork, Antigravity, or any program that can read local files and make authenticated HTTP calls — drives a Balaka instance to process the pipeline described in `README.md`. It is a recipe, not code. The intent is to give an agent-runtime integrator enough detail to wire up their own implementation without prescribing a specific language or framework.

## 1. Authentication

Balaka implements the OAuth 2.0 Device Authorisation Grant ([RFC 8628](https://datatracker.ietf.org/doc/html/rfc8628)) for headless clients that cannot host a browser. The flow is the same one a TV app or a CLI tool would use. The relevant endpoints are on `DeviceAuthApiController` at `/api/device/*` and are documented in the live OpenAPI specification at `/v3/api-docs` (or browsable via Swagger UI at `/swagger-ui.html`).

The sequence:

1. **Request a device code.** `POST /api/device/code` with a JSON body `{"clientId": "<agent-client-id>"}`. Balaka responds with a `deviceCode`, a short `userCode` (displayed to the human), a `verificationUri` (to open in a browser), a `verificationUriComplete` (with the code pre-filled), an `expiresIn` window in seconds, and a polling `interval` in seconds.
2. **Display the user code.** The agent prints or displays the `userCode` and the `verificationUri` to the human operator, who opens the URL in any authenticated browser session and approves the request.
3. **Poll for the token.** The agent polls `POST /api/device/token` with `{"deviceCode": "<deviceCode>"}` every `interval` seconds. While the human has not approved yet, Balaka returns `400 authorization_pending`. Once approved, Balaka returns an access token with `tokenType: Bearer`, an `expiresIn` window (30 days by default), and a `scope` string.
4. **Store the token.** The agent uses `Authorization: Bearer <accessToken>` on all subsequent API calls. Tokens are revocable from the Device Tokens screen in the web UI — a human operator can cut off an agent's access at any time without restarting Balaka.

All other endpoints the agent uses are protected and require the bearer token. `DeviceAuthApiController` itself is the one exception and requires no token, for obvious reasons.

When the agent requests a device code, it should ask for the narrowest scope set its task requires. Balaka catalogues eight scopes in `src/main/resources/openapi/extensions.json` under `x-authentication.scopes` (`drafts:create`, `drafts:read`, `drafts:approve`, `transactions:post`, `analysis:read`, `analysis:write`, `data:import`, `tax-export:read`). The transaction-recording pipeline described here needs exactly two: `drafts:create` to write a draft transaction, and `drafts:read` to fetch the draft library and verify state. It should not request `drafts:approve`, `transactions:post`, or any of the other six. The approval step is performed by a human operator through the Balaka web UI — the agent prepares, the human commits, and the pipeline is structured this way deliberately. This is a protocol-level safety property: even if the agent's logic is wrong, even if its parser is fooled, even if it produces a draft for a transaction that should never have existed, it is mechanically incapable of approving or posting that draft, because the bearer token it holds does not carry the relevant scopes. The property does not depend on the agent's discipline or on the reviewer's vigilance — only on the scope set the institution selects when it approves the authorisation request, which the agent cannot exceed afterwards under any circumstances.

## 2. Discovery

Before processing any document, the agent loads three things:

1. **The runbook.** A local file (this repository ships `runbook.template.json`; the institution maintains its own copy with substituted UUIDs). The agent reads this once at startup and caches it.
2. **The template library.** `GET /api/drafts/templates` returns every journal template in the instance with its `id`, `name`, `category`, `description`, `semanticDescription`, `keywords`, `exampleMerchants`, `typicalAmountMin`, `typicalAmountMax`, and `merchantPatterns`. The agent uses this to resolve `templateName` strings from the runbook into concrete template IDs, and to fall back to semantic matching when a source document matches no runbook entry by pattern but is recognisable by keyword.
3. **The chart of accounts.** `GET /api/drafts/accounts` returns every leaf account with its `id`, `code`, `name`, and `type`. The agent uses this to resolve bank account names from the runbook into concrete account UUIDs, and to validate any account override it supplies in an `accountSlots` map.

Discovery happens once per agent session. Templates and accounts change rarely; the agent can cache them until a subsequent API call returns a validation error that implicates stale data, at which point it refreshes and retries.

## 3. Pipeline Loop

Plain-English pseudocode for the per-document loop. An implementation fills in the details; the state transitions and the API-call boundary are fixed.

```
for each file in inbox/:
    document = read(file)

    extracted = parse(document)
        # Extract: vendor name, amounts, dates, reference numbers,
        # any structured fields the document carries.

    match = lookup(runbook, extracted.vendor, extracted.text)
        # First try runbook.vendorMappings matchPatterns.
        # If no match, fall back to semantic search over the template
        # library using keywords and exampleMerchants. If still no
        # match, record status="error" with a note and move on.

    if match is in runbook.skipPatterns:
        write parsed/<name>.json with status="skipped", note=reason
        continue

    draft = build_draft_payload(match, extracted)
        # Populate CreateDraftRequest fields:
        #   templateId       — from match
        #   description      — human-readable, includes vendor and period
        #   amount           — from extracted total; for DETAILED templates
        #                      set amount = sum of variable values
        #   transactionDate  — bank-debit date, not document-issue date
        #   accountSlots     — {match.bankLineOrder: <operating-bank-uuid>}
        #   variables        — only for DETAILED templates

    write parsed/<name>.json with:
        status           = "parsed"
        source           = <file>
        parsedAt         = today
        runbookMatch     = match.vendor
        templateName     = match.templateName
        draft            = <draft payload>

    # The agent stops here. Posting happens only after human review.

# Separately, when the human flips a parsed file's status to "reviewed":

for each file in parsed/ where status == "reviewed":
    response = POST /api/drafts with file.draft

    if response is 200 or 201:
        update file:
            status              = "posted"
            postedTransactionId = response.transactionId
    else:
        update file:
            status = "error"
            note   = response.error
```

Two invariants:

- **The agent never writes to the ledger without an explicit human `reviewed` gate.** Parse and review are separated by a human step. The pipeline fails closed: a parsed draft that is never reviewed is never posted.
- **Posting always goes through the API.** There is no code path in which the agent touches the database directly. The audit log, fiscal-period lock, and template-validation machinery therefore apply uniformly to agent-posted and human-posted transactions.

## 4. Error Handling

The agent encounters four broad failure modes. Each has a specific response.

| Failure mode | What it looks like | Response |
|---|---|---|
| No runbook match | The source document names a vendor or references a pattern no runbook entry covers, and semantic fallback against the template library produces nothing above a reasonable confidence bar | Write the parsed file with `status = "error"` and `note = "No matching runbook entry for vendor <X>. Add a vendorMappings entry or refine matchPatterns."`. Leave `draft = null`. The human reviews the error file, extends the runbook, and reruns the parse step for that file. |
| Ambiguous parse | The OCR or text extraction produces multiple plausible amounts, dates, or vendor identifications | Write the parsed file with `status = "error"` and `note` describing both candidates. The human decides and re-parses. Do not guess. |
| API validation error | `POST /api/drafts` returns 400 with a field-level validation error (missing required account, invalid template state, fiscal-period locked) | Update the parsed file with `status = "error"` and `note = <error>`. Do not retry automatically. The human inspects the draft, fixes the underlying issue (usually a missing `accountSlots` override or a mislabelled template), and reruns the post step. |
| API infrastructure error | `POST /api/drafts` returns 5xx or times out | Exponential backoff with a small retry budget (e.g. three attempts over one minute). If still failing, update the parsed file with `status = "error"` and `note = <error>`, and surface an alert to the human. |

Two escalation paths sit above the above four:

- **Missing API feature.** If the agent cannot perform an operation because Balaka exposes no endpoint for it, file a feature request on the Balaka GitHub repository. Do not work around the gap by manipulating state through an unintended path — doing so silently drifts the institution's ledger off the API contract.
- **API bug.** If the agent encounters an API error that looks like a server-side defect (inconsistent response, crash, wrong status code), file a bug report on the same repository. Capture the request and response verbatim.

## 5. Integration With Specific Agent Runtimes

The pipeline is intentionally runtime-agnostic. The shape of the integration is the same across runtimes: give the agent filesystem access to an `inbox/`, `parsed/`, and `posted/` directory; give it network access to the Balaka instance; give it the device-flow client ID as configuration; point it at `runbook.template.json`. Brief notes on the three runtimes named in the paper follow.

**Claude Code.** Runs as a CLI in the user's working directory. A pipeline folder in the working directory is visible to Claude Code by default. The device-flow client ID goes in a local config file the CLI reads on startup. Claude Code can be invoked interactively (human types "process the inbox") or non-interactively via scheduled invocations.

**Cowork.** A collaborative agent environment. The integration shape is the same as for any other runtime: mount the pipeline folder into the workspace, register the Balaka base URL as configuration, and store the device-flow client ID where the runtime can read it on startup.

**Antigravity.** An editor-integrated agent that sees the project tree directly. The pipeline folder lives in the project; the agent reads and writes files in place. The device-flow handshake runs the first time the agent makes an API call, after which the token is cached in the project's local state.

None of these runtimes require custom Balaka integration code. The commitment in `README.md` — that every operation available through the web interface is available through the same REST API a third-party integration would use — is what makes runtime portability possible. An agent runtime that can make authenticated HTTP calls and read local files has everything it needs.
