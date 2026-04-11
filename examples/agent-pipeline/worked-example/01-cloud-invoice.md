> **Synthetic example for documentation purposes — not real.** All names, amounts, and identifiers below are fictitious. This file exists to illustrate the shape of a cloud-hosting invoice that an agent would ingest during the parse step.

# 01 — Cloud Hosting Invoice

## Source

In practice this document arrives as a PDF attachment to a monthly billing email. The agent receives the PDF (or a plaintext extract of it) and produces `parsed/01-cloud-invoice.json`.

## Consumer

**PT Sarana Teknologi Informatika** — a small Indonesian IT services consultancy. Operates a handful of production droplets for client deliverables.

## Literal document text

```
-------------------------------------------------------------
DigitalOcean, LLC
101 Avenue of the Americas
New York, NY 10013, USA

INVOICE
-------------------------------------------------------------

Invoice number:     DO-2026-01-4821093
Invoice date:       January 5, 2026
Billing period:     December 1, 2025 — December 31, 2025
Due date:           January 15, 2026

Bill to:
  PT Sarana Teknologi Informatika
  Jakarta, Indonesia

-------------------------------------------------------------
Description                                         Amount
-------------------------------------------------------------
Droplet — s-2vcpu-4gb (sti-prod-01)                IDR 225,000
Droplet — s-1vcpu-2gb (sti-prod-02)                IDR 115,000
Managed Database — db-s-1vcpu-1gb                  IDR  85,000
Bandwidth overage                                  IDR  25,000
-------------------------------------------------------------
Subtotal                                           IDR 450,000
Tax                                                IDR       0
-------------------------------------------------------------
TOTAL DUE                                          IDR 450,000
-------------------------------------------------------------

Payment was automatically charged to the bank account on file
on January 8, 2026. Thank you for your business.
```

## What the agent extracts

| Field | Value | Notes |
|---|---|---|
| Vendor | DigitalOcean | Matches `cloud-hosting` entry in `runbook.template.json` |
| Runbook category | `cloud-server` | |
| Template | `Bayar Beban Cloud & Server` | From the matched runbook entry |
| Amount | 450000 | Total invoice amount, in IDR |
| Transaction date | 2026-01-08 | Date the bank actually debited the account, not the invoice issue date |
| Bank line override | `{"2": "<operating-bank-account-id>"}` | Line order 2 is the BANK credit line on this template |
| Description | `DigitalOcean — droplet hosting (January 2026)` | |

The resulting draft payload is in `parsed/01-cloud-invoice.json`.

## Reviewer checklist

Before promoting this draft from `parsed` to `reviewed`, the human operator checks:

1. The amount matches the invoice's TOTAL DUE line.
2. The transaction date corresponds to the actual bank debit, not the invoice date.
3. The matched template is appropriate for a cloud-hosting charge and not, for example, a more specific subcategory the institution uses.
4. The bank account in `accountSlots` is the account that was actually debited.

On a clean review the operator flips `status` from `parsed` to `reviewed`; the agent then calls `POST /api/drafts` followed by `POST /api/drafts/{id}/approve` to commit the transaction and transitions status to `posted`.
