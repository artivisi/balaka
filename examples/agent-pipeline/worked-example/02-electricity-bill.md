> **Synthetic example for documentation purposes — not real.** All names, amounts, and identifiers below are fictitious. This file exists to illustrate the shape of a utility bill that an agent would ingest during the parse step.

# 02 — Electricity Bill

## Source

In practice this document arrives as a screenshot of the PLN Mobile app payment confirmation or as an SMS notification from the operator's bank after a bill-pay transaction. The agent receives either the image (and OCRs it) or the plaintext SMS and produces `parsed/02-electricity-bill.json`.

## Consumer

**PT Sarana Teknologi Informatika** — same consultancy as Example 01. Monthly electricity bill for the office premises.

## Literal document text

```
-------------------------------------------------------------
PLN Mobile — Pembayaran Berhasil
-------------------------------------------------------------

Tanggal transaksi   : 18 Januari 2026, 09:42 WIB
No. referensi       : PLN-202601-88274619

ID Pelanggan        : 54xxxxxxx312
Nama pelanggan      : PT SARANA TEKNOLOGI INF
Tarif / Daya        : B2 / 6600 VA
Periode tagihan     : Desember 2025

Stand meter         : 041582 → 043217
Pemakaian (kWh)     : 1,635

-------------------------------------------------------------
Rincian
-------------------------------------------------------------
Tagihan listrik                          Rp  642,500
Biaya admin                              Rp    7,500
-------------------------------------------------------------
Total dibayar                            Rp  650,000
-------------------------------------------------------------

Sumber dana : Bank Transfer
Status      : BERHASIL

Simpan bukti pembayaran ini sebagai tanda lunas.
```

## What the agent extracts

| Field | Value | Notes |
|---|---|---|
| Vendor | PLN | Matches `electricity` entry in `runbook.template.json` |
| Runbook category | `utility-electricity` | |
| Template | `Bayar Beban Listrik` | |
| Amount | 650000 | Total paid including the bank admin surcharge; see reviewer checklist |
| Transaction date | 2026-01-18 | Payment date from the confirmation |
| Bank line override | `{"2": "<operating-bank-account-id>"}` | |
| Description | `PLN — office electricity bill (December 2025 usage)` | References the usage period, not the payment month |

The resulting draft payload is in `parsed/02-electricity-bill.json`.

## Reviewer checklist

1. The amount matches the confirmed total-paid line.
2. The electricity admin surcharge (`Biaya admin`) is small enough to roll into the expense line rather than splitting into a separate bank-fee transaction. For institutions that prefer split posting, the runbook would need a second entry routing the surcharge to `Beban Admin Bank`; the reviewer decides policy here.
3. The usage period in the description is accurate — auditors find period-accurate descriptions useful when reconciling against utility provider records.
4. The bank account in `accountSlots` is the account that actually paid.
