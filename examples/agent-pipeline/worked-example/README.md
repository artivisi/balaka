# Worked Example

Three synthetic source documents and the parsed JSON an agent would produce from each after applying `../runbook.template.json`. The example illustrates the `parsed` → `reviewed` → `posted` lifecycle end to end for a small IT services consultancy, **PT Sarana Teknologi Informatika**, running on the `it-service` industry seed pack.

## Contents

| File | What it shows |
|---|---|
| `01-cloud-invoice.md` | A cloud-hosting invoice (DigitalOcean) as the agent receives it |
| `01-cloud-invoice.json` | The parsed draft payload derived from that invoice |
| `02-electricity-bill.md` | A PLN payment confirmation as the agent receives it |
| `02-electricity-bill.json` | The parsed draft payload derived from that confirmation |
| `03-bpjs-payment.md` | A combined BPJS Kesehatan + Ketenagakerjaan payment confirmation |
| `03-bpjs-payment.json` | The parsed draft payload — note the `variables` map, because `Bayar BPJS` is a DETAILED template |

All source-document files carry an explicit "Synthetic example for documentation purposes — not real" notice at the top and use clearly-fictitious company, invoice, and customer numbers. Do not treat any amount, reference number, or customer ID in these files as a real record.

## Why three documents

The three cover the three accounting-flow categories described in `../runbook.template.json`:

| Document | Flow | Template kind | Why it is interesting |
|---|---|---|---|
| 01 — Cloud invoice | `bankExpense` | SIMPLE | The baseline: an operating expense paid by bank transfer, with the bank line overridden via `accountSlots` |
| 02 — Electricity bill | `bankExpense` | SIMPLE | A variant with a small bank admin surcharge the reviewer must decide whether to split |
| 03 — BPJS payment | `bpjsPayment` | DETAILED | The only case that exercises the `variables` map, and the only one whose BANK line is at `lineOrder` 3 rather than 2 |

Between them they exercise both template shapes a Balaka instance ships (SIMPLE with `amount`, DETAILED with named variables) and both of the account-slot scenarios an agent will encounter (single BANK override on a two-line template, single BANK override on a three-line template).

## Running the example

The JSON files in `parsed/` use placeholder UUIDs (`11111111-…`, `22222222-…`, `33333333-…` for the three templates and `aaaaaaaa-…` for the operating bank account). To run the example against a live Balaka instance:

1. Start a Balaka instance and import the `it-service` seed pack via the Data Import screen (or `POST /api/data-import`).
2. Call `GET /api/drafts/templates` and note the real UUIDs of `Bayar Beban Cloud & Server`, `Bayar Beban Listrik`, and `Bayar BPJS`. Substitute them for the `11111111-…`, `22222222-…`, and `33333333-…` placeholders respectively.
3. Call `GET /api/drafts/accounts` and note the real UUID of the operating bank leaf account under `Aset Lancar` — the one the institution actually uses to pay these vendors. Substitute it for `aaaaaaaa-…`.
4. `POST /api/drafts` with each JSON payload's `draft` object as the request body.
5. Review the response and call `POST /api/drafts/{id}/approve` to commit each draft to the ledger.

Step 4 and step 5 are both agent-initiated in the pipeline model — step 4 happens at parse time, step 5 happens after the human operator flips the `status` field from `parsed` to `reviewed`. The human never calls the API directly.

## What the reviewer owns

Each source-document file has a "Reviewer checklist" section listing exactly what the human verifies before promoting `parsed` to `reviewed`. Read those first if you are auditing whether the human/agent boundary is drawn where you expect.
