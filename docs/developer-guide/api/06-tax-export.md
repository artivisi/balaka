# Tax Export API

Endpoints for exporting tax data for SPT preparation. Supports both JSON and Excel (XLSX) output formats. All endpoints require `Authorization: Bearer <token>` with the `tax-export:read` scope.

Fiscal adjustments (`/api/fiscal-adjustments`) and loss carryforwards
(`/api/fiscal-adjustments/loss-carryforward`) are read with `tax-export:read`; creating, updating and
deleting them needs `tax-export:write`, effective only when the token user holds `FISCAL_PERIOD_MANAGE`
(see [Authentication](01-authentication.md#scopes-and-authorization)).

Base URL: `/api/tax-export`

## Coretax Excel Exports

These endpoints produce Coretax-compatible Excel files for direct import.

### e-Faktur Keluaran (Output VAT)

```http
GET /api/tax-export/efaktur-keluaran?startMonth=2025-01&endMonth=2025-12
```

### e-Faktur Masukan (Input VAT)

```http
GET /api/tax-export/efaktur-masukan?startMonth=2025-01&endMonth=2025-12
```

### e-Bupot Unifikasi (PPh Withholding)

```http
GET /api/tax-export/bupot-unifikasi?startMonth=2025-01&endMonth=2025-12
```

All three accept `startMonth` and `endMonth` in `yyyy-MM` format. Response is an Excel file download (`application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`).

## JSON / Excel Dual-Format Endpoints

These endpoints return JSON by default. Add `?format=excel` to download an XLSX file instead.

### PPN Detail Report

```http
GET /api/tax-export/ppn-detail?startDate=2025-01-01&endDate=2025-12-31
GET /api/tax-export/ppn-detail?startDate=2025-01-01&endDate=2025-12-31&format=excel
```

PPN detail with Faktur Keluaran/Masukan breakdown, DPP and PPN totals.

### PPh 23 Detail Report

```http
GET /api/tax-export/pph23-detail?startDate=2025-01-01&endDate=2025-12-31
GET /api/tax-export/pph23-detail?startDate=2025-01-01&endDate=2025-12-31&format=excel
```

### Fiscal Reconciliation

```http
GET /api/tax-export/rekonsiliasi-fiskal?year=2025
GET /api/tax-export/rekonsiliasi-fiskal?year=2025&format=excel
```

Commercial income to taxable income (PKP) reconciliation with positive/negative fiscal adjustments. `pkp` is the net fiscal income before loss compensation; `lossCompensation` is the fiscal loss of the previous five years compensated this year (UU PPh Pasal 6 ayat 2, capped at `pkp`); `pkpAfterLoss` is the PKP that `pphBadan` is computed on.

### PPh Badan (Corporate Income Tax)

```http
GET /api/tax-export/pph-badan?year=2025
```

JSON only. Returns PKP (after fiscal loss compensation), PPh terutang, kredit pajak (PPh 23/25), and PPh 29 calculation including Pasal 31E facility.

## SPT Tahunan Badan Exports

Annual corporate tax return data in Coretax format. All accept `year` parameter and optional `format=excel`.

### Lampiran I -- Fiscal Reconciliation

```http
GET /api/tax-export/spt-tahunan/l1?year=2025
GET /api/tax-export/spt-tahunan/l1?year=2025&format=excel
```

### Lampiran IV -- Final Income (PPh 4(2))

```http
GET /api/tax-export/spt-tahunan/l4?year=2025
GET /api/tax-export/spt-tahunan/l4?year=2025&format=excel
```

### Transkrip 8A -- Financial Statements

```http
GET /api/tax-export/spt-tahunan/transkrip-8a?year=2025
GET /api/tax-export/spt-tahunan/transkrip-8a?year=2025&format=excel
```

Balance sheet + income statement in Coretax 8A layout.

### Lampiran 9 -- Depreciation & Amortization

```http
GET /api/tax-export/spt-tahunan/l9?year=2025
GET /api/tax-export/spt-tahunan/l9?year=2025&format=excel
```

Fixed asset depreciation in DJP converter format.

### e-Bupot PPh 21 Annual (1721-A1)

```http
GET /api/tax-export/ebupot-pph21?year=2025
GET /api/tax-export/ebupot-pph21?year=2025&format=excel
```

Annual PPh 21 reconciliation for all employees. Excel output uses DJP BPA1 converter format.

### Consolidated Lampiran

```http
GET /api/tax-export/spt-tahunan/lampiran?year=2025
```

JSON only. Returns all lampiran data (L1, L4, L9, Transkrip 8A, BPA1) mapped to Coretax field numbers in a single response (`SptLampiranReport`).

### Financial Statements PDF

```http
GET /api/tax-export/financial-statements/pdf?year=2025
```

Combined Balance Sheet and Income Statement as a PDF file for Coretax SPT upload. Response content type: `application/pdf`.

## Coretax SPT Badan Export

```http
GET /api/tax-export/coretax/spt-badan?year=2025
```

Structured JSON matching Coretax form fields. All values are plain numbers for direct entry into the Coretax web form (`CoretaxSptBadanExport`).

Response:

```json
{
  "reportType": "coretax-spt-badan",
  "generatedAt": "2025-12-31T10:00:00",
  "parameters": { "year": "2025" },
  "data": {
    ...
  },
  "metadata": {
    "description": "Coretax-compatible SPT Badan export...",
    "currency": "IDR"
  }
}
```

## Date Format Reference

| Parameter | Format | Example |
|-----------|--------|---------|
| `startMonth`, `endMonth` | `yyyy-MM` | `2025-01` |
| `startDate`, `endDate` | `yyyy-MM-dd` | `2025-01-01` |
| `year` | integer | `2025` |

---

Next: [Pagination](07-pagination.md)
