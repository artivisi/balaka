# Tax Filings API

Register of tax records that belong to a **tax period** rather than to a journal entry.

Coretax paperwork splits in two. Transaction-linked documents — faktur pajak keluaran, bukti potong PPh 23, the kode billing for a setoran that was booked — already have a home on `POST /api/transactions/{id}/documents` and `POST /api/transactions/{id}/tax-details`. Period-linked documents have none: an SPT Masa PPN, its BPE, an STP, a surat teguran, or an SP2DK attaches to a masa pajak, and a masa has no journal entry to hang on. This API is that home.

All endpoints require `Authorization: Bearer <token>`. Read endpoints need the `tax-filings:read` scope; write endpoints need `tax-filings:write`.

Base URL: `/api/tax-filings`

## The Period Key

A filing is identified by `(taxType, period, pembetulanNo)`. Registering the same key twice returns `409`.

| `taxType` | Filed | `period` format |
|-----------|-------|-----------------|
| `PPN` | per masa | `YYYY-MM` |
| `PPH21` | per masa | `YYYY-MM` |
| `PPH23_UNIFIKASI` | per masa | `YYYY-MM` |
| `PPH_BADAN` | per year | `YYYY` |

Using the wrong shape for the type — `PPN` with `2026`, or `PPH_BADAN` with `2026-04` — returns `400`.

`pembetulanNo` defaults to `0` (SPT normal). A pembetulan is a separate record: register it with `pembetulanNo: 1` and set the original's `status` to `CORRECTED`.

## Status

`status` is supplied by the caller — the register records what happened at DJP, it does not infer it from deadlines.

| Status | Meaning |
|--------|---------|
| `NOT_FILED` | Period is known, SPT not yet submitted |
| `FILED` | Submitted on time |
| `LATE` | Submitted after the deadline |
| `CORRECTED` | Superseded by a pembetulan |

## List Filings

```http
GET /api/tax-filings?taxType=PPN&year=2026
```

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `taxType` | enum | no | `PPN`, `PPH21`, `PPH23_UNIFIKASI`, `PPH_BADAN` |
| `year` | int | no | Tax year |

Newest period first. Returns an array of filing objects (no documents).

## Get Filing Detail

```http
GET /api/tax-filings/{id}
```

```json
{
  "filing": {
    "id": "…",
    "taxType": "PPN",
    "period": "2025-04",
    "pembetulanNo": 0,
    "status": "LATE",
    "filedDate": "2025-06-12",
    "bpeNumber": "BPE-25040012345",
    "bpeDate": "2025-06-12",
    "kurangBayar": 1250000,
    "lebihBayar": null,
    "nihil": false,
    "billingCode": "820250612001234",
    "ntpn": "0912B4C7D1E2F3A4",
    "paidDate": "2025-06-11",
    "paymentTransactionId": "…",
    "paymentTransactionNumber": "TRX-2025-0431",
    "notes": null
  },
  "documents": [ … ]
}
```

## Register a Filing

```http
POST /api/tax-filings
```

```json
{
  "taxType": "PPN",
  "period": "2025-04",
  "pembetulanNo": 0,
  "status": "LATE",
  "filedDate": "2025-06-12",
  "bpeNumber": "BPE-25040012345",
  "bpeDate": "2025-06-12",
  "kurangBayar": 1250000,
  "billingCode": "820250612001234",
  "ntpn": "0912B4C7D1E2F3A4",
  "paidDate": "2025-06-11",
  "paymentTransactionId": "…",
  "notes": "Terlambat karena menunggu faktur pengganti"
}
```

| Field | Required | Notes |
|-------|----------|-------|
| `taxType`, `period`, `status` | yes | See above |
| `pembetulanNo` | no | Defaults to `0` |
| `filedDate`, `bpeNumber`, `bpeDate` | no | Filled once the SPT is submitted |
| `kurangBayar`, `lebihBayar` | no | Mutually exclusive; both positive returns `400` |
| `nihil` | no | `true` with a positive `kurangBayar`/`lebihBayar` returns `400` |
| `billingCode`, `ntpn`, `paidDate` | no | Setoran trail |
| `paymentTransactionId` | no | Links the booked setoran; unknown id returns `404` |

`paymentTransactionId` is deliberately optional: a nihil masa has nothing to pay, and an FP-03 (BUMN pemungut) masa is report-only — the BUMN remits the PPN, so no setoran exists in the ledger.

Returns `201` with the same shape as the detail endpoint.

## Update a Filing

```http
PUT /api/tax-filings/{id}
```

Full replacement of the filing fields — omitting `paymentTransactionId` unlinks the setoran. Moving a filing onto a period key another record already holds returns `409`.

## Delete a Filing

```http
DELETE /api/tax-filings/{id}
```

Returns `204`. Returns `409` while documents are still attached: SPT/BPE/STP scans carry a 10-year retention obligation (see `DATA-RETENTION-POLICY.md`), so they are removed one at a time rather than swept away with the parent record.

## Attach a Document

```http
POST /api/tax-filings/{id}/documents
Content-Type: multipart/form-data
```

| Field | Required | Description |
|-------|----------|-------------|
| `file` | yes | PDF, JPEG, PNG, or GIF, max 10 MB |
| `docType` | yes | See table below |
| `docNumber` | no | Document number as printed |
| `docDate` | no | Issue date (`YYYY-MM-DD`) |
| `dueDate` | no | Payment or response deadline |
| `amount` | no | Value stated on the document |
| `referenceNumber` | no | Number of the document this one answers |
| `notes` | no | Free text |

| `docType` | Document |
|-----------|----------|
| `SPT` | Surat Pemberitahuan |
| `BPE` | Bukti Penerimaan Elektronik |
| `STP` | Surat Tagihan Pajak |
| `TEGURAN` | Surat Teguran |
| `SP2DK` | Surat Permintaan Penjelasan atas Data dan/atau Keterangan |
| `SKPLB` | Surat Ketetapan Pajak Lebih Bayar |
| `SKPKPP` | Surat Keputusan Pengembalian Kelebihan Pembayaran Pajak |
| `KODE_BILLING` | Kode billing |
| `BPN` | Bukti Penerimaan Negara |
| `SURAT` | Surat lainnya |

The file goes into the same encrypted store as transaction attachments, with the same SHA-256 checksum and retention handling. The response nests the stored file under `file`:

```json
{
  "id": "…",
  "docType": "TEGURAN",
  "docNumber": "TEG-00112",
  "docDate": "2026-07-14",
  "dueDate": null,
  "amount": null,
  "referenceNumber": "STP-07643",
  "notes": null,
  "file": {
    "id": "…",
    "originalFilename": "teguran-00112.pdf",
    "contentType": "application/pdf",
    "fileSize": 84213,
    "fileSizeFormatted": "82.2 KB",
    "checksumSha256": "…",
    "uploadedAt": "2026-07-15T09:12:44",
    "uploadedBy": "hq-bot"
  }
}
```

### Document Chains

`referenceNumber` records what a document answers, which is what makes an escalation readable after the fact:

```
SPT PPN 2025-04  ──▶  STP-07643        (kurang bayar tagihan)
                       └──▶ TEG-00112  referenceNumber: "STP-07643"
                              └──▶ kode billing ──▶ BPN
```

A duplicate STP for a masa already settled shows up as two `STP` documents on the same filing — one with a `BPN`, one without.

## List, Download, Remove Documents

```http
GET    /api/tax-filings/{id}/documents
GET    /api/tax-filings/{id}/documents/{taxDocumentId}/download
DELETE /api/tax-filings/{id}/documents/{taxDocumentId}
```

`DELETE` removes the metadata and the stored file together, and returns `204`.

The generic `DELETE /api/documents/{docId}` returns `409` for a file owned by this register. Documents are soft-deleted, and a soft-deleted row is invisible to the tax metadata that points at it, so the generic endpoint refuses rather than leaving an unresolvable link.

## Filing Status in the Tax Summary

`GET /api/analysis/tax-summary?startDate=&endDate=` (scope `analysis:read`) now carries a `filings` array alongside the tax account balances, listing every registered filing whose period overlaps the requested range:

```json
{
  "data": {
    "items": [ … ],
    "totalBalance": 4820000,
    "filings": [
      {
        "taxType": "PPN",
        "period": "2025-04",
        "pembetulanNo": 0,
        "status": "LATE",
        "filedDate": "2025-06-12",
        "bpeNumber": "BPE-25040012345",
        "kurangBayar": 1250000,
        "lebihBayar": null,
        "nihil": false,
        "paidDate": "2025-06-11"
      }
    ]
  }
}
```

Only registered filings appear. A masa missing from the array has no record at all — which is the signal that it still needs one.

## Error Responses

| Status | Cause |
|--------|-------|
| `400` | Period shape wrong for the tax type, malformed period, nihil with an amount, or kurang bayar and lebih bayar both positive |
| `404` | Filing, document, or `paymentTransactionId` not found |
| `409` | Period key already registered, or filing still has attached documents |
