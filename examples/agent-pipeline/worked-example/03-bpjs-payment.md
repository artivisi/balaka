> **Synthetic example for documentation purposes — not real.** All names, amounts, and identifiers below are fictitious. This file exists to illustrate the shape of a BPJS payment confirmation that an agent would ingest during the parse step.

# 03 — BPJS Monthly Payment

## Source

In practice this document is a bank notification email or a screenshot from a mobile-banking app confirming payment to the BPJS collection accounts. Many Indonesian small institutions pay both BPJS Kesehatan (health) and BPJS Ketenagakerjaan (social security) in a single monthly batch; this example models that case.

## Consumer

**PT Sarana Teknologi Informatika** — same consultancy as Examples 01 and 02. Monthly employer BPJS contributions for four employees.

## Literal document text

```
-------------------------------------------------------------
Konfirmasi Pembayaran — Mobile Banking
-------------------------------------------------------------

Tanggal         : 12 Januari 2026, 11:07 WIB
No. referensi   : MBX-20260112-447193

Pembayaran telah berhasil diproses.

-------------------------------------------------------------
Detail Transaksi 1 — BPJS Kesehatan
-------------------------------------------------------------
Jenis iuran     : IURAN BADAN USAHA
Kode iuran      : 89xxxx0031
Periode         : Januari 2026
Jumlah          : Rp  380,000

-------------------------------------------------------------
Detail Transaksi 2 — BPJS Ketenagakerjaan
-------------------------------------------------------------
Jenis iuran     : JKK + JKM + JHT
Kode iuran      : 20xxxxxxxxx7
Periode         : Januari 2026
Jumlah          : Rp  420,000

-------------------------------------------------------------
Total didebet dari rekening giro        Rp  800,000
-------------------------------------------------------------
```

## What the agent extracts

| Field | Value | Notes |
|---|---|---|
| Vendor | BPJS Kesehatan + BPJS Ketenagakerjaan | Both match the `bpjs` entry in `runbook.template.json` |
| Runbook category | `bpjs` | |
| Template | `Bayar BPJS` | DETAILED template shipped in the IT Services seed pack |
| Template kind | DETAILED | Requires `variables` rather than `amount` alone |
| Variables | `{"bpjsKesehatan": 380000, "bpjsTenagakerja": 420000}` | Variable names are defined by the template — look them up via `GET /api/drafts/templates` for the authoritative list |
| Amount | 800000 | Set to the sum for API validation (amount > 0 is required); the journal amounts are derived from the variables map |
| Transaction date | 2026-01-12 | |
| Bank line override | `{"3": "<operating-bank-account-id>"}` | Line order 3 is the BANK credit line on this template — note this differs from the two-line templates in Examples 01 and 02 |

The resulting draft payload is in `parsed/03-bpjs-payment.json`.

## Reviewer checklist

1. Both component amounts match the confirmation.
2. The total matches the sum of the two components.
3. The payment period is the correct contribution month — BPJS contributions are typically paid for the current month, not the prior one.
4. The bank account in `accountSlots` is the account that was actually debited.
5. If the institution pays Kesehatan and Ketenagakerjaan on different days, the reviewer must decide whether to split into two transactions. The shipped `Bayar BPJS` template is combined, so a clean split requires either customising the template to a two-single-variable pair or posting two separate transactions with the "other" component carrying a placeholder amount — neither is ideal. See `runbook.template.json` → `setupChecklist` for the recommended customisation path.
