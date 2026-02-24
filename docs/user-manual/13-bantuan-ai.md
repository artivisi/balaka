# Bantuan AI untuk Pencatatan Transaksi

Aplikasi ini mendukung pencatatan transaksi dengan bantuan AI assistant seperti Claude Code, Gemini CLI, atau asisten AI lainnya. AI assistant dapat membaca struk, invoice, atau dokumen keuangan lainnya, kemudian secara otomatis membuat transaksi akuntansi yang sesuai.

> **Fitur Utama:**
> - Autentikasi aman via OAuth 2.0 Device Flow
> - AI membaca dan menganalisis dokumen (struk, invoice, bank statement)
> - Template matching otomatis berdasarkan metadata
> - User approval sebelum transaksi diposting
> - Transaksi langsung tercatat (tanpa draft)
> - Analisis keuangan: AI membaca laporan keuangan dan memberikan insight

---

## Cara Kerja

### Alur Umum

```
1. AI membaca capabilities.json (GET /api/capabilities.json)
   ↓
2. AI melakukan autentikasi (OAuth 2.0 Device Flow)
   ↓
3. User mengirim struk/dokumen ke AI assistant
   ↓
4. AI menganalisis dokumen (merchant, jumlah, tanggal)
   ↓
5. AI mencocokkan template journal yang sesuai
   ↓
6. AI menampilkan preview dan meminta persetujuan user
   ↓
7. User menyetujui → AI posting transaksi ke aplikasi
   ↓
8. Transaksi tercatat di aplikasi
```

### Keuntungan

- **Cepat**: Tidak perlu input manual di web
- **Akurat**: AI membaca struk dengan akurasi tinggi
- **Konsisten**: Template journal dipilih otomatis
- **Audit Trail**: Semua API call tercatat

---

## API Discovery via capabilities.json

Sebelum mulai berinteraksi dengan API, AI assistant sebaiknya membaca file **capabilities.json** yang mendeskripsikan seluruh kemampuan API aplikasi ini. File ini tersedia tanpa autentikasi.

### Endpoint

```bash
GET /api/capabilities.json
# Tidak perlu Authorization header
```

### Isi capabilities.json

| Bagian | Deskripsi |
|--------|-----------|
| `authentication` | Alur OAuth 2.0 Device Flow lengkap (step 1-3), daftar scope, expiry |
| `endpointGroups` | Semua endpoint dikelompokkan per domain (Draft Transactions, Transactions, Templates, Financial Analysis, Analysis Reports, Data Import, Bank Reconciliation) |
| `workflows` | Alur kerja end-to-end (receipt-based, text-based, direct posting, financial analysis, bank reconciliation, client onboarding, correction workflows) |
| `csvFiles` | Spesifikasi CSV untuk data import (nama kolom, tipe data, catatan) |
| `industries` | Daftar kode industri yang didukung (`it-service`, `online-seller`, `coffee-shop`, `campus`) |
| `errorCodes` | Daftar kode error dan HTTP status |

### Cara AI Menggunakan capabilities.json

```
1. AI membaca capabilities.json (GET /api/capabilities.json)
   ↓
2. AI memahami:
   - Endpoint apa saja yang tersedia
   - Field apa yang wajib/opsional per endpoint
   - Scope apa yang diperlukan
   - Alur kerja (workflow) yang benar
   ↓
3. AI mengikuti alur autentikasi dari capabilities.json
   ↓
4. AI memanggil endpoint sesuai workflow
```

### Keuntungan

- **Self-describing API**: AI tidak perlu dokumentasi terpisah — semua informasi ada di capabilities.json
- **Selalu sinkron**: capabilities.json diperbarui bersama kode, sehingga selalu akurat
- **Tanpa autentikasi**: AI dapat membaca capabilities.json sebelum melakukan device flow

> **Untuk pengembang AI assistant**: Langkah pertama integrasi adalah selalu `GET /api/capabilities.json` untuk mengetahui endpoint, field, dan workflow yang tersedia.

---

## Setup Autentikasi

AI assistant memerlukan autentikasi untuk mengakses aplikasi Anda. Proses ini menggunakan **OAuth 2.0 Device Flow** yang aman.

### Langkah 1: AI Meminta Kode

AI assistant akan meminta device code dari aplikasi:

```bash
POST /api/device/code
{
  "clientId": "claude-code"
}
```

Response:
```json
{
  "deviceCode": "a79d766e4972e61d...",
  "userCode": "MBJN-KRFJ",
  "verificationUri": "http://localhost:8080/device",
  "interval": 5,
  "expiresIn": 900
}
```

### Langkah 2: User Otorisasi di Browser

AI akan menampilkan URL dan kode:

```
Please authorize this device:
  URL: http://localhost:8080/device
  Code: MBJN-KRFJ
```

Buka URL tersebut di browser, login, dan masukkan kode:

![Halaman Otorisasi Device](screenshots/ai-transaction/00-device-authorization.png)

**Langkah otorisasi:**
1. Login dengan username dan password Anda
2. Verifikasi kode perangkat yang ditampilkan
3. (Opsional) Beri nama perangkat, misalnya: "Claude Code di MacBook"
4. Klik **"Otorisasi Perangkat"**

> **Keamanan**: Kode device hanya berlaku 15 menit dan hanya bisa digunakan sekali.

### Langkah 3: AI Mendapat Access Token

Setelah Anda otorisasi, AI akan mendapat access token:

```json
{
  "accessToken": "15d07ef9030cba7b...",
  "tokenType": "Bearer",
  "expiresIn": 2592000
}
```

Token ini berlaku 30 hari. AI akan menyimpannya untuk digunakan di request berikutnya.

---

## Template Journal dengan Metadata

AI memilih template journal berdasarkan **metadata semantik** yang terdapat pada setiap template.

### Contoh Response Template API

```bash
GET /api/templates
Authorization: Bearer {accessToken}
```

Response (contoh 1 template, field `lines` disertakan):

```json
[
  {
    "id": "1bbc7ccc-4e8f-44ef-87d3-0cd011fbc56d",
    "name": "Bayar Beban Listrik",
    "category": "EXPENSE",
    "description": "Pembayaran listrik kantor",
    "semanticDescription": "Gunakan template ini untuk mencatat pembayaran tagihan listrik bulanan ke PLN atau penyedia listrik lainnya.",
    "keywords": ["listrik", "electricity", "pln", "token", "utility"],
    "exampleMerchants": ["PLN", "PLN Mobile", "Tokopedia PLN"],
    "typicalAmountMin": 50000,
    "typicalAmountMax": 5000000,
    "merchantPatterns": [".*pln.*", ".*listrik.*"],
    "lines": [
      {
        "lineOrder": 1,
        "position": "DEBIT",
        "accountId": "uuid-akun-beban-listrik",
        "accountCode": "5.1.05",
        "accountName": "Beban Listrik",
        "accountHint": null,
        "formula": "AMOUNT",
        "description": null
      },
      {
        "lineOrder": 2,
        "position": "CREDIT",
        "accountId": null,
        "accountCode": null,
        "accountName": null,
        "accountHint": "Kas / Bank",
        "formula": "AMOUNT",
        "description": null
      }
    ]
  }
]
```

Setiap template menyertakan array `lines` yang menunjukkan struktur jurnal:
- **accountId/accountCode/accountName**: Akun tetap (sudah ditentukan di template)
- **accountHint**: Akun yang perlu dipilih saat transaksi (gunakan `accountSlots` dengan accountHint sebagai key untuk menentukan akun)

> **Catatan**: Semua template dari seed pack (IT Service, Online Seller, Coffee Shop, Campus) sudah dilengkapi metadata semantik untuk AI matching.

### Cara AI Matching Template

AI menggunakan metadata untuk mencocokkan template:

1. **Keyword matching**: Cocokkan kategori/deskripsi dengan array `keywords`
2. **Merchant matching**: Cocokkan nama merchant dengan `exampleMerchants`
3. **Regex pattern**: Cocokkan dengan `merchantPatterns`
4. **Amount range**: Validasi jumlah dalam rentang `typicalAmountMin` - `typicalAmountMax`
5. **Semantic similarity**: Cocokkan deskripsi dengan `semanticDescription`

**Contoh matching:**
- User: "Bayar listrik PLN 350 ribu"
- AI matching:
  - Keyword: "listrik" ✓
  - Merchant: "PLN" ✓
  - Amount: 350000 (dalam range 50k-5jt) ✓
  - **Result**: Template "Bayar Beban Listrik"

---

## Posting Transaksi Langsung

Setelah AI matching template dan user approve, AI langsung posting transaksi (tanpa draft).

### Request

```bash
POST /api/transactions
Authorization: Bearer {accessToken}
Content-Type: application/json
```

**Body:**
```json
{
  "templateId": "9aa9c785-bb32-4e1e-80db-1c30a10aa3e3",
  "merchant": "Starbucks Grand Indonesia",
  "amount": 85000,
  "transactionDate": "2026-02-12",
  "currency": "IDR",
  "description": "Team coffee break at Starbucks",
  "category": "Food & Beverage",
  "items": ["Caffe Latte Grande", "Blueberry Muffin"],
  "source": "claude-code",
  "userApproved": true
}
```

Untuk template DETAILED yang menggunakan formula non-standar, tambahkan `variables`:
```json
{
  "templateId": "UUID-template-pembelian-aset",
  "merchant": "Tokopedia",
  "amount": 3681200,
  "transactionDate": "2026-02-12",
  "description": "Pembelian laptop Lenovo",
  "source": "claude-code",
  "userApproved": true,
  "accountSlots": {
    "ASET_TETAP": "UUID-akun-peralatan",
    "BANK": "UUID-akun-bank"
  },
  "variables": {
    "assetCost": 3681200
  }
}
```

### Response

```json
{
  "transactionId": "83da31e4-acdc-4ca9-898e-df2cefbe95c7",
  "transactionNumber": "TRX-2026-0001",
  "status": "POSTED",
  "merchant": "Team coffee break at Starbucks",
  "amount": 85000,
  "transactionDate": "2026-02-12",
  "description": "Team coffee break at Starbucks",
  "journalEntries": [
    {
      "journalNumber": "JE-2026-0001-01",
      "accountCode": "5.1.01",
      "accountName": "Beban Gaji",
      "debitAmount": 85000,
      "creditAmount": 0
    },
    {
      "journalNumber": "JE-2026-0001-02",
      "accountCode": "2.1.10",
      "accountName": "Hutang Gaji",
      "debitAmount": 0,
      "creditAmount": 85000
    }
  ]
}
```

### Verifikasi di Web UI

Setelah transaksi diposting, Anda dapat melihatnya di halaman Transaksi:

![Daftar Transaksi](screenshots/ai-transaction/04-transactions-list.png)

Transaksi yang dibuat via AI akan memiliki:
- **Transaction Number**: TRX-2026-0001 (auto-generated)
- **Status**: POSTED (langsung posted, bukan draft)
- **Created By**: Nama user yang otorisasi device

---

## Buat Draft Langsung via API

Selain alur dari-teks/dari-struk, AI juga dapat membuat transaksi DRAFT langsung dengan template dan account overrides dalam satu panggilan. Alur ini cocok ketika AI sudah tahu template mana yang harus dipakai.

### Request

```bash
POST /api/drafts
Authorization: Bearer {accessToken}
Content-Type: application/json
```

**Body:**
```json
{
  "templateId": "UUID-template",
  "description": "Bayar listrik Januari 2026",
  "amount": 350000,
  "transactionDate": "2026-02-10",
  "accountSlots": {
    "Kas / Bank": "UUID-akun-bank-bca"
  }
}
```

Field `accountSlots` memetakan `accountHint` template ke akun yang dipilih. Key = string `accountHint` dari template line, Value = UUID akun. Gunakan `GET /api/templates` untuk melihat `accountHint` mana yang perlu diisi.

#### Template DETAILED (Formula Non-Standar)

Beberapa template menggunakan formula selain `amount` (contoh: template "Pembelian Aset Tetap" menggunakan formula `assetCost`). Untuk template seperti ini, tambahkan field `variables` yang memetakan nama variabel formula ke nilainya:

```json
{
  "templateId": "UUID-template-pembelian-aset",
  "description": "Pembelian laptop Lenovo untuk operasional kantor",
  "amount": 3681200,
  "transactionDate": "2026-02-10",
  "accountSlots": {
    "ASET_TETAP": "UUID-akun-peralatan-kantor",
    "BANK": "UUID-akun-bank-bca"
  },
  "variables": {
    "assetCost": 3681200
  }
}
```

Field `variables` hanya diperlukan jika template menggunakan formula selain `amount`. Cek field `formula` di template lines (`GET /api/templates`) untuk mengetahui variabel yang dibutuhkan.

### Response

```json
{
  "transactionId": "uuid-transaksi-baru",
  "transactionNumber": null,
  "status": "DRAFT",
  "amount": 350000,
  "transactionDate": "2026-02-10",
  "description": "Bayar listrik Januari 2026",
  "journalEntries": []
}
```

Transaksi dibuat dalam status **DRAFT**. Langkah selanjutnya:
1. (Opsional) Preview jurnal: `GET /api/transactions/{id}/journal-preview`
2. (Opsional) Koreksi: `PUT /api/transactions/{id}`
3. Posting: `POST /api/transactions/{id}/post`

---

## Preview Jurnal Sebelum Posting

Sebelum mem-posting transaksi DRAFT, AI dapat melihat preview jurnal yang akan dihasilkan:

```bash
GET /api/transactions/{id}/journal-preview
Authorization: Bearer {accessToken}
```

**Response:**
```json
{
  "valid": true,
  "errors": [],
  "entries": [
    {
      "accountCode": "5.1.05",
      "accountName": "Beban Listrik",
      "debitAmount": 350000,
      "creditAmount": 0
    },
    {
      "accountCode": "1.1.02",
      "accountName": "Bank BCA",
      "debitAmount": 0,
      "creditAmount": 350000
    }
  ],
  "totalDebit": 350000,
  "totalCredit": 350000
}
```

Jika `valid` bernilai `false`, field `errors` berisi daftar masalah (contoh: akun belum dipilih untuk line dengan accountHint).

---

## Koreksi Transaksi via AI

Jika AI salah mengklasifikasi transaksi atau ada kesalahan pada draft/transaksi, AI dapat memperbaikinya tanpa perlu intervensi manual di web UI.

### Koreksi Draft (PENDING)

Draft yang masih berstatus PENDING dapat dikoreksi sebelum di-approve:

```bash
PATCH /api/drafts/{id}
Authorization: Bearer {accessToken}
Content-Type: application/json
```

**Body** (semua field opsional, hanya field yang dikirim yang diperbarui):
```json
{
  "merchantName": "Starbucks Reserve",
  "amount": 95000,
  "description": "Coffee meeting with client",
  "suggestedTemplateId": "UUID-template-lain",
  "category": "Entertainment"
}
```

### Approve Draft → transactionId

Setelah approve, response menyertakan `transactionId` dari transaksi yang dibuat:

```bash
POST /api/drafts/{id}/approve
```

Response:
```json
{
  "draftId": "uuid-draft",
  "status": "APPROVED",
  "transactionId": "uuid-transaksi-yang-dibuat",
  "merchant": "Starbucks Reserve",
  "amount": 95000
}
```

> **Catatan**: Draft endpoints mengembalikan `draftId` (bukan `id`) untuk membedakan konteks draft dari transaksi.

### Koreksi Transaksi DRAFT (Belum Posted)

Transaksi yang sudah di-approve dari draft tetapi belum di-post (status DRAFT) dapat dikoreksi:

```bash
PUT /api/transactions/{id}
Authorization: Bearer {accessToken}
Content-Type: application/json
```

**Body** (semua field opsional):
```json
{
  "templateId": "UUID-template-yang-benar",
  "description": "Deskripsi yang diperbaiki",
  "amount": 350000,
  "transactionDate": "2026-02-10",
  "accountSlots": {
    "Kas / Bank": "UUID-akun-bank-lain"
  }
}
```

Validasi:
- Hanya transaksi berstatus **DRAFT** yang bisa dikoreksi
- Tanggal transaksi tidak boleh di masa depan
- `accountSlots` dapat dikirim berulang kali (idempotent) — slot sebelumnya akan diganti

### Hapus Transaksi DRAFT

Transaksi DRAFT yang salah total dapat dihapus:

```bash
DELETE /api/transactions/{id}
Authorization: Bearer {accessToken}
```

Response: `204 No Content`

Hanya transaksi berstatus **DRAFT** yang bisa dihapus. Transaksi yang sudah POSTED tidak bisa dihapus via API.

### Alur Koreksi

```
Skenario 1: Koreksi sebelum approve
  PATCH /api/drafts/{id}  →  POST /api/drafts/{id}/approve

Skenario 2: Buat draft langsung, preview, lalu post
  POST /api/drafts  →  GET /api/transactions/{id}/journal-preview
  →  PUT /api/transactions/{id} (opsional)  →  POST /api/transactions/{id}/post

Skenario 3: Koreksi setelah approve, sebelum post
  PUT /api/transactions/{id}  →  POST /api/transactions/{id}/post

Skenario 4: Hapus dan buat ulang
  DELETE /api/transactions/{id}  →  (buat draft/transaksi baru)

Skenario 5: Void transaksi yang sudah POSTED
  POST /api/transactions/{id}/void  →  (status jadi VOID, reversing entries dibuat otomatis)
```

---

## Analisis Keuangan via AI

Selain mencatat transaksi, AI assistant juga dapat **menganalisis data keuangan** perusahaan Anda. Aplikasi menyediakan 10 endpoint read-only di bawah `/api/analysis` yang mengembalikan data terstruktur — AI yang menginterpretasikan datanya.

### Endpoint Analisis

| Endpoint | Deskripsi | Parameter |
|----------|-----------|-----------|
| `GET /api/analysis/snapshot` | KPI bulanan (revenue, expense, profit, cash) | `month` (yyyy-MM) |
| `GET /api/analysis/trial-balance` | Neraca saldo | `asOfDate` (yyyy-MM-dd) |
| `GET /api/analysis/income-statement` | Laporan laba rugi | `startDate`, `endDate` |
| `GET /api/analysis/balance-sheet` | Neraca | `asOfDate` |
| `GET /api/analysis/cash-flow` | Laporan arus kas | `startDate`, `endDate` |
| `GET /api/analysis/tax-summary` | Ringkasan pajak (PPN, PPh) | `startDate`, `endDate` |
| `GET /api/analysis/receivables` | Piutang usaha | `asOfDate` |
| `GET /api/analysis/payables` | Hutang usaha | `asOfDate` |
| `GET /api/analysis/accounts` | Daftar akun (COA) | — |
| `GET /api/analysis/accounts/{id}/ledger` | Buku besar per akun (running balance) | `startDate`, `endDate` |
| `GET /api/analysis/drafts` | Draft transaksi pending | — |

Semua parameter **wajib** (tidak ada nilai default). Request tanpa parameter yang diperlukan akan mengembalikan HTTP 400.

### Format Response

Semua endpoint mengembalikan format `AnalysisResponse` yang konsisten:

```json
{
  "reportType": "trial-balance",
  "generatedAt": "2026-02-17T14:30:00",
  "parameters": {
    "asOfDate": "2026-01-31"
  },
  "data": { ... },
  "metadata": {
    "currency": "IDR",
    "accountingBasis": "accrual",
    "description": "Trial balance as of 2026-01-31..."
  }
}
```

Field `metadata` memberikan konteks yang membantu AI memahami data (mata uang, basis akuntansi, penjelasan arti debit/credit, dll).

### Contoh: KPI Snapshot

```bash
GET /api/analysis/snapshot?month=2026-01
Authorization: Bearer {accessToken}
```

Response:
```json
{
  "reportType": "snapshot",
  "generatedAt": "2026-02-17T14:30:00",
  "parameters": { "month": "2026-01" },
  "data": {
    "month": "2026-01",
    "revenue": 50000000,
    "revenueChange": 12.5,
    "expense": 35000000,
    "expenseChange": -3.2,
    "netProfit": 15000000,
    "profitChange": 8.7,
    "profitMargin": 30.0,
    "marginChange": 2.1,
    "cashBalance": 125000000,
    "receivablesBalance": 20000000,
    "payablesBalance": 15000000,
    "transactionCount": 47,
    "cashBankItems": [
      { "accountName": "Kas", "balance": 25000000 },
      { "accountName": "Bank BCA", "balance": 100000000 }
    ]
  },
  "metadata": {
    "currency": "IDR",
    "accountingBasis": "accrual",
    "description": "Financial KPI snapshot for 2026-01. Change percentages are vs previous month."
  }
}
```

### Contoh: Neraca Saldo

```bash
GET /api/analysis/trial-balance?asOfDate=2026-01-31
Authorization: Bearer {accessToken}
```

Response (ringkasan):
```json
{
  "reportType": "trial-balance",
  "data": {
    "items": [
      {
        "accountCode": "1.1.01",
        "accountName": "Kas",
        "accountType": "ASSET",
        "normalBalance": "DEBIT",
        "debitBalance": 25000000,
        "creditBalance": 0
      },
      {
        "accountCode": "4.1.01",
        "accountName": "Pendapatan Jasa",
        "accountType": "REVENUE",
        "normalBalance": "CREDIT",
        "debitBalance": 0,
        "creditBalance": 50000000
      }
    ],
    "totalDebit": 85000000,
    "totalCredit": 85000000
  }
}
```

### Contoh: Draft Transaksi Pending

```bash
GET /api/analysis/drafts
Authorization: Bearer {accessToken}
```

Response:
```json
{
  "reportType": "drafts",
  "data": {
    "items": [
      {
        "id": "a1b2c3d4-...",
        "status": "PENDING",
        "merchantName": "Starbucks Grand Indonesia",
        "amount": 85000,
        "currency": "IDR",
        "transactionDate": "2026-02-12",
        "source": "API",
        "apiSource": "claude-code",
        "overallConfidence": 0.95,
        "suggestedTemplateName": "Konsumsi Karyawan",
        "createdBy": "admin",
        "createdAt": "2026-02-12T14:30:00"
      }
    ],
    "pendingCount": 1
  }
}
```

AI dapat menggunakan data ini untuk merekomendasikan approval draft dengan confidence tinggi, atau menandai draft yang perlu review manual.

### Contoh Interaksi Analisis

**User:**
```
Bagaimana kondisi keuangan bulan Januari?
```

**AI:**
```
Berdasarkan data keuangan Januari 2026:

Revenue:     Rp 50.000.000 (+12.5% vs Desember)
Expense:     Rp 35.000.000 (-3.2% vs Desember)
Net Profit:  Rp 15.000.000 (+8.7%)
Margin:      30.0%

Kas & Bank:
  - Kas:      Rp 25.000.000
  - Bank BCA: Rp 100.000.000

Piutang:     Rp 20.000.000
Hutang:      Rp 15.000.000

Bulan ini cukup baik — revenue naik, expense turun, dan
profit margin meningkat 2.1 poin.
```

**User:**
```
Ada pajak yang perlu dibayar?
```

**AI:**
```
Berdasarkan ringkasan pajak Januari 2026:

PPN Keluaran:  Rp 5.000.000
PPN Masukan:   Rp 2.000.000
PPN Kurang Bayar: Rp 3.000.000

PPh 23:        Rp 1.500.000 (sudah dipotong)
PPh 21:        Rp 2.800.000 (gaji karyawan)

Total kewajiban pajak bulan ini: Rp 7.300.000
Batas setor PPN: 31 Februari 2026
```

### Otorisasi Scope

Token yang diterbitkan melalui Device Flow secara otomatis memiliki scope berikut:
- `drafts:create` — membuat dan mengedit draft transaksi
- `drafts:approve` — approve/reject draft
- `drafts:read` — membaca draft
- `analysis:read` — membaca laporan keuangan
- `analysis:write` — mempublikasikan laporan analisis
- `transactions:post` — posting, koreksi, dan hapus transaksi DRAFT
- `data:import` — import data dari file ZIP
- `bills:read` — membaca vendor bills
- `bills:create` — membuat vendor bills
- `bills:approve` — approve vendor bills

Tanpa scope yang sesuai, request akan ditolak dengan HTTP 403.

---

## Publikasi Laporan Analisis

Setelah AI membaca data keuangan via endpoint `/api/analysis/*`, AI dapat menganalisis data tersebut dan mempublikasikan hasilnya kembali ke aplikasi. Laporan yang dipublikasikan langsung tersedia di web UI sehingga seluruh tim (akuntan, pemilik, manajer) dapat membacanya.

### Konfigurasi Industri

Sebelum AI melakukan analisis, pastikan industri perusahaan sudah diatur di **Pengaturan** > **Perusahaan** > **Industri**. AI membaca konfigurasi ini via `GET /api/analysis/company` untuk menentukan metrik dan analisis yang relevan.

Industri yang didukung:

| Kode | Nama | Fokus Analisis |
|------|------|----------------|
| `it-service` | Jasa IT & Konsultasi | Utilisasi, DSO, margin proyek, konsentrasi klien |
| `online-seller` | Toko Online / E-Commerce | Margin per channel, inventory turnover, fee marketplace, cash conversion cycle |
| `coffee-shop` | Kedai Kopi / F&B | Food cost %, labor cost %, prime cost, waste %, BOM costing |
| `campus` | Kampus / Pendidikan | Kolektibilitas SPP, rasio biaya akademik, beasiswa, deferred revenue |

### Struktur Laporan

Laporan analisis terdiri dari 5 bagian terstruktur:

| Bagian | Deskripsi |
|--------|-----------|
| **Ringkasan Eksekutif** | Paragraf singkat kondisi keuangan periode ini |
| **Indikator Utama** | Metrik KPI spesifik industri dengan perubahan vs periode sebelumnya |
| **Temuan** | Hal-hal yang perlu diperhatikan, dengan kategori dan severity |
| **Rekomendasi** | Langkah-langkah yang disarankan, dengan prioritas dan dampak |
| **Penilaian Risiko** | Risiko yang teridentifikasi beserta mitigasi |

### API Publikasi

```bash
POST /api/analysis/reports
Authorization: Bearer {accessToken}
Content-Type: application/json
```

Field `industry` diisi sesuai konfigurasi perusahaan. Field `reportType` diisi sesuai jenis analisis per industri (lihat tabel di bawah).

### Membaca Konfigurasi Perusahaan

```bash
GET /api/analysis/company
Authorization: Bearer {accessToken}
```

Response berisi `industry`, `currencyCode`, `fiscalYearStartMonth`, dan `isPkp` — informasi yang AI butuhkan untuk memilih metrik yang tepat.

### Daftar Laporan via API

```bash
GET /api/analysis/reports
Authorization: Bearer {accessToken}
```

Mengembalikan semua laporan yang sudah dipublikasikan, terbaru lebih dulu.

---

### Analisis per Industri: Jasa IT & Konsultasi (`it-service`)

#### Tipe Laporan

| reportType | Kegunaan | Frekuensi |
|------------|----------|-----------|
| `utilization-review` | Review utilisasi billable konsultan | Bulanan |
| `project-profitability` | Analisis margin per proyek | Per proyek / bulanan |
| `receivables-aging` | Aging piutang & DSO analysis | Bulanan |
| `client-concentration` | Analisis konsentrasi pendapatan per klien | Kuartalan |
| `tax-withholding-recon` | Rekonsiliasi Bukti Potong PPh 23 | Bulanan |

#### Metrik Utama

| Metrik | Formula | Benchmark |
|--------|---------|-----------|
| Utilisasi Billable | Jam billable / Total jam tersedia | 70-80% (optimal 75%) |
| DSO (Days Sales Outstanding) | (Piutang / Pendapatan) x Hari | < 45 hari |
| Rasio Biaya Tenaga Kerja | Total gaji / Pendapatan | 55-65% |
| Revenue per Konsultan | Pendapatan / Jumlah konsultan | Track trend |
| Margin Proyek | (Revenue proyek - Biaya langsung) / Revenue proyek | > 15% sehat, > 25% kuat |
| Konsentrasi Klien Top-3 | Revenue 3 klien terbesar / Total revenue | < 60% sehat |
| Realization Rate | Revenue terkumpul / (Jam billable x Tarif standar) | > 80% |

#### Kategori Temuan

`utilisasi`, `piutang`, `profitabilitas-proyek`, `klien`, `pajak`, `bench-time`

#### Risiko Spesifik
- Konsentrasi klien (1 klien > 20% revenue)
- Bench time (konsultan idle = pure cost)
- DSO creep (klien korporat bayar Net-60 sampai Net-90)
- Bukti Potong PPh 23 yang hilang (kehilangan kredit pajak 2%)
- Key-person dependency (revenue bergantung pada beberapa senior)

---

### Analisis per Industri: Toko Online / E-Commerce (`online-seller`)

#### Tipe Laporan

| reportType | Kegunaan | Frekuensi |
|------------|----------|-----------|
| `marketplace-profitability` | P&L per marketplace (Tokopedia, Shopee, dll) | Bulanan |
| `inventory-health` | Turnover, dead stock, aging persediaan | Bulanan |
| `cash-conversion-cycle` | Analisis siklus konversi kas | Bulanan |
| `channel-comparison` | Perbandingan profitabilitas antar channel | Kuartalan |
| `shipping-cost-analysis` | Analisis biaya pengiriman vs pendapatan | Bulanan |

#### Metrik Utama

| Metrik | Formula | Benchmark |
|--------|---------|-----------|
| Gross Margin per Channel | (Revenue - HPP - Fee marketplace) / Revenue | > 30% |
| Inventory Turnover | HPP / Rata-rata persediaan | 4-8x/tahun |
| Dead Stock Ratio | Nilai stok mati / Total nilai persediaan | < 10% |
| Fee Marketplace Ratio | Total fee marketplace / Revenue | < 12% |
| Shipping Cost Ratio | Biaya kirim / Revenue | < 10% |
| Return Rate | Jumlah retur / Jumlah order | < 10% |
| Cash Conversion Cycle | DIO + DSO - DPO | < 60 hari |
| ROAS (Return on Ad Spend) | Revenue dari iklan / Belanja iklan | > 3:1 |

#### Kategori Temuan

`margin`, `persediaan`, `arus-kas`, `marketplace`, `retur`, `pengiriman`, `iklan`

#### Risiko Spesifik
- Kenaikan fee marketplace (platform menaikkan komisi secara berkala)
- Dead stock (modal terjebak di persediaan tidak laku > 180 hari)
- Cash conversion cycle panjang (bayar supplier duluan, pencairan marketplace 7-21 hari)
- Ketergantungan pada satu marketplace
- Subsidi ongkir menggerus margin pada produk AOV rendah

---

### Analisis per Industri: Kedai Kopi / F&B (`coffee-shop`)

#### Tipe Laporan

| reportType | Kegunaan | Frekuensi |
|------------|----------|-----------|
| `food-cost-review` | Analisis food cost % dan variance resep | Bulanan |
| `labor-efficiency` | Rasio biaya tenaga kerja dan produktivitas per shift | Bulanan |
| `menu-engineering` | Profitabilitas dan popularitas per menu item | Kuartalan |
| `production-cost` | Analisis biaya produksi BOM vs harga jual | Bulanan |
| `daily-sales-analysis` | Analisis penjualan harian, peak hours, average check | Mingguan |

#### Metrik Utama

| Metrik | Formula | Benchmark |
|--------|---------|-----------|
| Food Cost % | HPP / Revenue | 20-30% |
| Labor Cost % | Total biaya tenaga kerja / Revenue | 25-30% |
| Prime Cost % | (HPP + Biaya tenaga kerja) / Revenue | < 60% |
| Gross Margin | (Revenue - HPP) / Revenue | 70-80% |
| Waste % | Nilai bahan terbuang / Total HPP | < 2-4% |
| Average Transaction Value | Revenue / Jumlah transaksi | Track trend |
| Inventory Turnover (perishable) | HPP perishable / Rata-rata stok perishable | > 4x/bulan |
| Variance Resep | (HPP aktual - HPP teoritis) / HPP teoritis | < 2% |

#### Kategori Temuan

`biaya-bahan`, `tenaga-kerja`, `produksi`, `penjualan`, `persediaan`, `waste`

#### Risiko Spesifik
- Spoilage bahan perishable (susu, roti — shelf life pendek)
- Over-portioning (resep tidak terstandar, HPP aktual > teoritis)
- Kenaikan harga komoditas (kopi arabica, butter volatil)
- Prime cost > 65% (biaya bahan + tenaga kerja melampaui batas sehat)
- Ketergantungan lokasi (revenue sangat terkait foot traffic)

---

### Analisis per Industri: Kampus / Pendidikan (`campus`)

#### Tipe Laporan

| reportType | Kegunaan | Frekuensi |
|------------|----------|-----------|
| `tuition-collection` | Analisis kolektibilitas SPP per angkatan | Per semester |
| `budget-realization` | Realisasi RKAT vs anggaran | Per semester |
| `scholarship-impact` | Dampak beasiswa terhadap net tuition revenue | Per semester |
| `faculty-cost-analysis` | Analisis biaya dosen tetap vs tidak tetap | Per semester |
| `enrollment-forecast` | Proyeksi pendapatan berdasarkan tren pendaftaran | Tahunan |

#### Metrik Utama

| Metrik | Formula | Benchmark |
|--------|---------|-----------|
| SPP Collection Rate | SPP terkumpul / SPP tertagih | > 90% |
| Tuition Dependency Ratio | Pendapatan SPP / Total pendapatan | < 85% (semakin rendah semakin sehat) |
| Operating Margin | (Total pendapatan - Total beban) / Total pendapatan | > 5% |
| Biaya per Mahasiswa | Total beban operasional / Jumlah mahasiswa | Track trend |
| Days Cash on Hand | Kas / (Total beban / 365) | > 90 hari |
| Rasio Biaya Akademik | Beban akademik / Total beban operasional | 30-40% |
| Rasio Biaya Administratif | Beban administratif / Total beban operasional | < 30% |
| Scholarship Expense Ratio | Total beban beasiswa / Gross SPP | Track (jaga agar tidak > 35%) |
| Piutang SPP > 90 Hari | Piutang SPP aging > 90 hari / Total piutang SPP | < 5% |

#### Kategori Temuan

`penerimaan-spp`, `beasiswa`, `biaya-akademik`, `biaya-administratif`, `aset-tetap`, `tren-pendaftaran`

#### Risiko Spesifik
- Penurunan jumlah mahasiswa baru (dampak langsung ke uang pangkal dan pipeline SPP)
- Ketergantungan tinggi pada SPP (> 90%) tanpa diversifikasi pendapatan
- Cash flow musiman (penerimaan terkonsentrasi di awal semester, beban tersebar sepanjang tahun)
- Deferred maintenance gedung dan laboratorium (tropical climate mempercepat deteriorasi)
- Piutang SPP yang tidak tertagih dari mahasiswa non-aktif

---

### Format Field API

#### Indikator Utama (metrics)

| Field | Wajib | Deskripsi |
|-------|-------|-----------|
| `name` | Ya | Nama metrik sesuai industri |
| `value` | Ya | Nilai saat ini |
| `previousValue` | Tidak | Nilai periode sebelumnya |
| `change` | Tidak | Perubahan (contoh: "+12.5%", "+4pp", "+14 hari") |
| `status` | Tidak | `positive` (hijau), `negative` (merah), `warning` (kuning), `neutral` (abu-abu) |

#### Temuan (findings)

| Field | Wajib | Deskripsi |
|-------|-------|-----------|
| `category` | Ya | Kategori sesuai industri (lihat tabel per industri di atas) |
| `severity` | Ya | `info` (biru), `warning` (kuning), `critical` (merah) |
| `description` | Ya | Deskripsi temuan |

#### Rekomendasi (recommendations)

| Field | Wajib | Deskripsi |
|-------|-------|-----------|
| `priority` | Ya | `high` (merah), `medium` (kuning), `low` (abu-abu) |
| `description` | Ya | Deskripsi rekomendasi |
| `impact` | Tidak | Dampak yang diharapkan |

#### Risiko (risks)

| Field | Wajib | Deskripsi |
|-------|-------|-----------|
| `severity` | Ya | `high` (merah), `medium` (kuning), `low` (abu-abu) |
| `description` | Ya | Deskripsi risiko |
| `mitigation` | Tidak | Langkah mitigasi |

### Melihat Laporan di Web UI

Laporan yang sudah dipublikasikan dapat dilihat di menu **Laporan** > **Analisis AI**.

#### Halaman Daftar Laporan

Halaman daftar menampilkan semua laporan terbaru dengan:
- **Badge industri** (Jasa IT, Toko Online, Kedai Kopi, Kampus) dengan warna berbeda per industri
- **Tipe laporan** dan **judul**
- **Ringkasan eksekutif** (2 baris pertama)
- **Periode**, **sumber AI**, dan **jumlah metrik/temuan**

#### Halaman Detail Laporan

Klik laporan untuk melihat detail lengkap. Setiap industri menampilkan metrik KPI yang berbeda sesuai karakteristik bisnisnya.

**Bagian atas:** Badge industri, tipe laporan, header, ringkasan eksekutif, dan grid indikator utama.

**Bagian bawah:** Tabel temuan dengan severity badge, daftar rekomendasi yang diurutkan berdasarkan prioritas, dan penilaian risiko dengan langkah mitigasi.

#### Menghapus Laporan

Klik tombol **Hapus** di halaman detail untuk menghapus laporan (soft delete). Laporan yang dihapus tidak akan muncul di daftar.

#### Contoh Laporan per Industri

##### Jasa IT & Konsultasi

Metrik utama:
- Pendapatan Bulanan
- Beban Operasional
- Laba Bersih
- Margin Laba
- Saldo Kas
- Rasio Beban/Pendapatan

![Daftar Laporan - Jasa IT](screenshots/analysis-reports/list.png)

![Detail Laporan - Jasa IT (Header dan Metrik)](screenshots/analysis-reports/detail-top.png)

![Detail Laporan - Jasa IT (Temuan dan Rekomendasi)](screenshots/analysis-reports/detail-bottom.png)

---

##### Toko Online / E-Commerce

Metrik utama:
- Penjualan Bersih
- HPP (Harga Pokok)
- Laba Kotor
- Gross Margin
- Biaya Marketplace
- Biaya Pengiriman

![Daftar Laporan - Toko Online](screenshots/analysis-reports/seller-list.png)

![Detail Laporan - Toko Online](screenshots/analysis-reports/seller-detail.png)

---

##### Kedai Kopi / F&B

Metrik utama:
- Pendapatan
- Food Cost %
- Labor Cost %
- Prime Cost %
- Laba Bersih
- Net Margin

![Daftar Laporan - Kedai Kopi](screenshots/analysis-reports/coffee-list.png)

![Detail Laporan - Kedai Kopi](screenshots/analysis-reports/coffee-detail.png)

---

##### Kampus / Pendidikan

Metrik utama:
- Pendapatan Total
- Pendapatan SPP
- Biaya Dosen & Akademik
- Rasio Beasiswa
- Surplus Operasional
- Margin Operasional

![Daftar Laporan - Kampus](screenshots/analysis-reports/campus-list.png)

![Detail Laporan - Kampus](screenshots/analysis-reports/campus-detail.png)

### Contoh Alur Lengkap

```
1. AI membaca data keuangan
   GET /api/analysis/snapshot?month=2026-01
   GET /api/analysis/income-statement?startDate=2026-01-01&endDate=2026-01-31
   GET /api/analysis/receivables?asOfDate=2026-01-31
   ↓
2. AI menganalisis data dan membuat laporan terstruktur
   ↓
3. AI mempublikasikan laporan
   POST /api/analysis/reports
   ↓
4. Tim membuka menu Laporan > Analisis AI di web browser
   ↓
5. Akuntan membaca temuan, rekomendasi, dan risiko
   ↓
6. Tindak lanjut: buat jurnal penyesuaian, tagih piutang, dll.
```

---

## Inisialisasi Data Klien Baru

Untuk onboarding klien baru, AI membaca laporan keuangan klien (XLS/PDF neraca, laba rugi), mengekstrak struktur Chart of Accounts dan template jurnal, lalu menginisialisasi aplikasi secara programatis via `POST /api/data-import`.

### Alur Kerja

```
1. AI menerima file XLS/PDF laporan keuangan klien
   ↓
2. AI mengekstrak struktur COA, template jurnal, dan data awal
   ↓
3. AI menghasilkan file CSV sesuai format yang diharapkan
   ↓
4. AI membuat ZIP dari file-file CSV tersebut
   ↓
5. AI mengirim ZIP ke POST /api/data-import
   ↓
6. Aplikasi memproses ZIP: truncate tabel yang ada data CSV-nya, lalu import
   ↓
7. Klien baru siap menggunakan aplikasi dengan COA dan template yang sesuai
```

### Format CSV

ZIP berisi file CSV dengan nama berurut (dependency order). File yang paling umum untuk onboarding klien baru:

**`01_company_config.csv`** — Konfigurasi perusahaan (1 baris data):

```
company_name,company_address,company_phone,company_email,tax_id,npwp,nitku,fiscal_year_start_month,currency_code,signing_officer_name,signing_officer_title,company_logo_path,established_date,is_pkp,pkp_since,industry
```

**`02_chart_of_accounts.csv`** — Daftar akun:

```
account_code,account_name,account_type,parent_code,normal_balance,active,is_permanent
```

- `account_type`: `ASSET`, `LIABILITY`, `EQUITY`, `REVENUE`, `EXPENSE`
- `normal_balance`: `DEBIT` atau `CREDIT`
- `parent_code`: kosong jika akun top-level, isi kode parent jika sub-akun

**`04_journal_templates.csv`** — Template jurnal:

```
template_name,category,cash_flow_category,template_type,description,is_system,active,version,usage_count,last_used_at,semantic_description,keywords,example_merchants,typical_amount_min,typical_amount_max,merchant_patterns
```

- `category`: `REVENUE`, `EXPENSE`, `TRANSFER`, `ADJUSTMENT`, `PAYROLL`, `TAX`, `RECEIVABLE`, `PAYABLE`, `ASSET_PURCHASE`, `ASSET_DEPRECIATION`
- `cash_flow_category`: `OPERATING`, `INVESTING`, `FINANCING`
- `template_type`: `SIMPLE`, `VARIABLE`, `SPLIT`, `MULTI_LINE`
- `keywords`, `example_merchants`, `merchant_patterns`: nilai dipisahkan dengan `|` (pipe)

**`05_journal_template_lines.csv`** — Baris template jurnal:

```
template_name,line_order,account_code,account_hint,position,formula,description
```

- `position`: `DEBIT` atau `CREDIT`
- `formula`: `AMOUNT` (nilai penuh), `AMOUNT * 0.11` (PPN 11%), dsb.

### Contoh Penggunaan API

```bash
curl -X POST https://akunting.example.com/api/data-import \
  -H "Authorization: Bearer {accessToken}" \
  -F "file=@seed-data.zip"
```

Response (HTTP 201):

```json
{
  "totalRecords": 45,
  "documentCount": 0,
  "durationMs": 1234
}
```

### Referensi

- Lihat direktori `industry-seed/` di source code untuk contoh lengkap seed data per industri (it-service, online-seller, coffee-shop)
- Import mengganti (truncate) data pada tabel yang memiliki CSV di dalam ZIP. Tabel tanpa CSV di ZIP tidak terpengaruh
- File CSV boleh hanya berisi header (tanpa data) — tabel tersebut akan dilewati

---

## Contoh Interaksi dengan AI

### Skenario 1: Struk Kopi Starbucks

**User:**
```
[Kirim foto struk Starbucks]
```

**AI:**
```
📄 Saya menganalisis struk:
   • Merchant: Starbucks Grand Indonesia
   • Jumlah: Rp 85.000
   • Tanggal: 12 Feb 2026
   • Item: Caffe Latte Grande, Blueberry Muffin

   Template yang cocok: "Konsumsi Karyawan" (confidence: 0.95)

   Posting transaksi ini?
   ✓ Ya
   ✏️ Edit dulu
   🔄 Pilih template lain
   ✗ Batal
```

**User:**
```
Ya
```

**AI:**
```
✓ Transaksi berhasil diposting!
  Nomor: TRX-2026-0001
  Status: POSTED

  Jurnal:
  [DEBIT]  Beban Konsumsi Karyawan: Rp 85.000
  [CREDIT] Kas: Rp 85.000
```

### Skenario 2: Text Input Listrik

**User:**
```
Bayar listrik PLN 350 ribu
```

**AI:**
```
📄 Saya memahami:
   • Merchant: PLN
   • Jumlah: Rp 350.000
   • Kategori: Utilities

   Template yang cocok: "Bayar Beban Listrik"

   Posting?
```

**User:**
```
Ya, tapi edit deskripsi dulu
```

**AI:**
```
Deskripsi apa yang Anda inginkan?
```

**User:**
```
Listrik bulan Januari 2026
```

**AI:**
```
✓ Siap posting:
  • Merchant: PLN
  • Jumlah: Rp 350.000
  • Deskripsi: "Listrik bulan Januari 2026"
  • Template: Bayar Beban Listrik

  Posting sekarang? (ya/tidak)
```

**User:**
```
ya
```

**AI:**
```
✓ Posted TRX-2026-0002
```

---

## Monitoring & Audit

### Audit Log

Semua API call tercatat di audit log dengan detail:

- **Event Type**: `API_CALL`
- **User**: Username yang otorisasi device
- **IP Address**: IP address request
- **Details**: Source (claude-code), merchant, amount, confidence

### Token Management (Self-Service)

Setiap user dapat melihat dan mencabut device token miliknya sendiri di halaman **Perangkat API**.

**Lokasi:** Sidebar → Master Data → **Perangkat API**, atau langsung buka `/settings/devices`

![Halaman Perangkat API](screenshots/settings/devices.png)

**Informasi yang ditampilkan per token:**

| Kolom | Deskripsi |
|-------|-----------|
| Perangkat | Nama device (jika diisi saat otorisasi) |
| Client ID | Identifier aplikasi (contoh: `claude-code`) |
| Scope | Hak akses token (contoh: `drafts:create,analysis:read`) |
| Status | Aktif (hijau) atau Kedaluwarsa (abu-abu) |
| Dibuat | Waktu token diterbitkan |
| Terakhir Digunakan | Waktu terakhir token digunakan untuk API call |
| IP Terakhir | IP address terakhir yang menggunakan token |

**Aksi yang tersedia:**
- **Cabut** (per token): Mencabut satu token tertentu
- **Cabut Semua**: Mencabut semua token aktif sekaligus

> **Catatan**: Setelah token dicabut, AI assistant yang menggunakan token tersebut tidak dapat mengakses API lagi. User perlu melakukan device flow ulang untuk mendapatkan token baru.

**Admin view**: Administrator juga dapat melihat dan mencabut token dari halaman detail pengguna di menu **Pengguna** > klik user > bagian **Sesi Perangkat Aktif**.

---

## API Reference

### Endpoint Summary

**Discovery (public, tanpa autentikasi):**

| Method | Endpoint | Deskripsi |
|--------|----------|-----------|
| GET | `/api/capabilities.json` | Deskripsi lengkap seluruh API (endpoint, workflow, scope) |

**Autentikasi (public):**

| Method | Endpoint | Deskripsi |
|--------|----------|-----------|
| POST | `/api/device/code` | Request device code |
| POST | `/api/device/token` | Poll for access token |

**Draft Transaksi (scope: `drafts:*`, `transactions:post`):**

| Method | Endpoint | Deskripsi |
|--------|----------|-----------|
| POST | `/api/drafts` | Buat DRAFT transaction langsung dengan template + overrides |
| POST | `/api/drafts/from-receipt` | Buat draft dari struk |
| POST | `/api/drafts/from-text` | Buat draft dari teks |
| GET | `/api/drafts/{id}` | Get draft by ID |
| PATCH | `/api/drafts/{id}` | Koreksi draft PENDING |
| POST | `/api/drafts/{id}/approve` | Approve draft (response menyertakan `transactionId`) |
| POST | `/api/drafts/{id}/reject` | Reject draft |

**Template (scope: `drafts:read`, `drafts:create`):**

| Method | Endpoint | Deskripsi |
|--------|----------|-----------|
| GET | `/api/templates` | List templates dengan metadata dan lines |
| GET | `/api/templates/{id}` | Get single template dengan lines |
| POST | `/api/templates` | Buat template baru |
| PUT | `/api/templates/{id}` | Update template |
| DELETE | `/api/templates/{id}` | Hapus template (soft delete) |

**Transaksi (scope: `transactions:post`):**

| Method | Endpoint | Deskripsi |
|--------|----------|-----------|
| POST | `/api/transactions` | Post transaction langsung |
| PUT | `/api/transactions/{id}` | Koreksi transaksi DRAFT (termasuk accountSlots) |
| DELETE | `/api/transactions/{id}` | Hapus transaksi DRAFT |
| POST | `/api/transactions/{id}/post` | Post satu transaksi DRAFT |
| POST | `/api/transactions/{id}/void` | Void transaksi POSTED (buat reversing entries) |
| GET | `/api/transactions/{id}/journal-preview` | Preview jurnal entries sebelum posting |
| POST | `/api/transactions/bulk-post` | Batch post transaksi DRAFT |

**Analisis Keuangan (scope: `analysis:read`):**

| Method | Endpoint | Deskripsi |
|--------|----------|-----------|
| GET | `/api/analysis/company` | Konfigurasi perusahaan (industry, currency, fiscal year) |
| GET | `/api/analysis/snapshot` | KPI bulanan |
| GET | `/api/analysis/trial-balance` | Neraca saldo |
| GET | `/api/analysis/income-statement` | Laporan laba rugi |
| GET | `/api/analysis/balance-sheet` | Neraca |
| GET | `/api/analysis/cash-flow` | Laporan arus kas |
| GET | `/api/analysis/tax-summary` | Ringkasan pajak |
| GET | `/api/analysis/receivables` | Piutang usaha |
| GET | `/api/analysis/payables` | Hutang usaha |
| GET | `/api/analysis/accounts` | Daftar akun (COA) |
| GET | `/api/analysis/accounts/{id}/ledger` | Buku besar per akun (running balance) |
| GET | `/api/analysis/drafts` | Draft transaksi pending |
| GET | `/api/analysis/transactions` | List transaksi dengan filter (status, category, date range, search) |
| GET | `/api/analysis/transactions/{id}` | Detail transaksi dengan jurnal entries |
| GET | `/api/analysis/reports` | Daftar laporan analisis |

**Publikasi Laporan Analisis (scope: `analysis:write`):**

| Method | Endpoint | Deskripsi |
|--------|----------|-----------|
| POST | `/api/analysis/reports` | Publikasi laporan analisis terstruktur |

**Manajemen Data (scope: `data:import`):**

| Method | Endpoint | Deskripsi |
|--------|----------|-----------|
| POST | `/api/data-import` | Import data dari file ZIP (COA, template, transaksi) |

### Authentication

Endpoint yang **tidak** memerlukan autentikasi:
- `GET /api/capabilities.json`
- `POST /api/device/code`
- `POST /api/device/token`

Semua endpoint lainnya memerlukan Bearer token:

```
Authorization: Bearer {accessToken}
```

### Rate Limiting

- **Draft creation**: 10 requests/menit per IP
- **Transaction posting**: 30 requests/menit per IP

### Error Codes

| Code | Error | Deskripsi |
|------|-------|-----------|
| 400 | `MISSING_PARAMETER` | Parameter wajib tidak dikirim (contoh: `asOfDate`) |
| 400 | `INVALID_REQUEST` | Request tidak valid (field required kosong, dll) |
| 401 | `unauthorized` | Token tidak valid atau expired |
| 403 | — | Scope tidak memadai (contoh: token tanpa `analysis:read`) |
| 404 | `NOT_FOUND` | Template/resource tidak ditemukan |
| 409 | `CONFLICT` | State conflict (contoh: transaksi sudah POSTED, tidak bisa dihapus) |
| 429 | `RATE_LIMIT_EXCEEDED` | Terlalu banyak request |
| 500 | `INTERNAL_ERROR` | Server error |

---

## FAQ

### Apakah data saya aman?

Ya. Autentikasi menggunakan OAuth 2.0 Device Flow yang merupakan standard industri. Access token:
- Tersimpan hanya di AI assistant Anda (tidak di server pihak ketiga)
- Berlaku maksimal 30 hari
- Dapat dicabut kapan saja di Settings

### AI mana yang didukung?

API ini generik dan dapat digunakan oleh AI assistant apapun yang mendukung HTTP API:
- ✅ Claude Code
- ✅ Gemini CLI
- ✅ ChatGPT dengan plugin
- ✅ Custom script (curl, Python, dll)

### Bagaimana jika AI salah pilih template?

Anda bisa:
1. **Koreksi draft**: AI menggunakan `PATCH /api/drafts/{id}` untuk memperbaiki draft sebelum approve
2. **Koreksi transaksi DRAFT**: AI menggunakan `PUT /api/transactions/{id}` untuk reklasifikasi template sebelum posting
3. **Hapus dan buat ulang**: AI menggunakan `DELETE /api/transactions/{id}` lalu buat transaksi baru
4. **Void transaksi POSTED**: AI menggunakan `POST /api/transactions/{id}/void` untuk membatalkan transaksi yang sudah posted (reversing entries dibuat otomatis)
5. **Edit di web**: Setelah posted, edit transaksi di web UI

### Apakah bisa batch import banyak transaksi?

Saat ini belum. Fitur batch import via CSV/Excel akan ditambahkan di versi berikutnya.

### Bagaimana cara menambah metadata ke template?

Administrator dapat menambah metadata via:
1. **Web UI**: Buka template > Edit > buka bagian **Metadata AI** > isi field yang diperlukan > Simpan. Lihat [Referensi Template](12-lampiran-template.md#metadata-ai-pada-template) untuk detail field.
2. **API**: PUT /api/templates/{id}

---

## Troubleshooting

### Error: "Authentication required" (401)

**Penyebab**: Token tidak valid atau expired

**Solusi**:
1. Lakukan device flow authentication ulang
2. Pastikan token disimpan dengan benar di AI assistant
3. Cek apakah token sudah expired (30 hari)

### Error: "Template not found" (404)

**Penyebab**: Template ID tidak valid

**Solusi**:
1. Refresh template list: GET /api/templates
2. Pastikan template ID yang dipilih ada di list
3. Jangan hardcode template ID, selalu fetch dari API

### AI matching template yang salah

**Penyebab**: Metadata template belum lengkap atau keyword tidak match

**Solusi**:
1. Lengkapi metadata template: buka template > Edit > bagian **Metadata AI**
2. Tambahkan keyword yang lebih spesifik
3. Tambahkan merchant patterns (regex)
4. User bisa override template saat approve

### Transaction failed to post

**Penyebab**:
- Template tidak balanced (debit ≠ credit)
- Account tidak valid
- Tanggal transaksi invalid

**Solusi**:
1. Cek template lines (harus ada debit dan credit)
2. Cek account mapping
3. Pastikan tanggal tidak di masa depan

---

## Lihat Juga

- [Setup Awal](01-setup-awal.md) - Setup aplikasi pertama kali
- [Tutorial Akuntansi](02-tutorial-akuntansi.md) - Dasar-dasar akuntansi
- [Rekonsiliasi Bank](14-rekonsiliasi-bank.md) - Bank reconciliation (data tersedia via `/api/analysis`)
- [API Documentation](../ai-transaction-api-v2.md) - Technical API reference

---

## Pengembangan Selanjutnya

Fitur yang akan ditambahkan:

- [ ] Batch transaction import (CSV/Excel)
- [x] Template metadata management UI
- [ ] Webhook notifications (transaksi berhasil/gagal)
- [ ] AI confidence feedback loop
- [ ] Multi-currency support dengan auto-conversion
- [ ] PDF invoice parsing
- [ ] Template suggestion improvement (machine learning)

---

> **Info**: Dokumentasi ini ditulis berdasarkan test scenario yang sebenarnya. Semua screenshot dan JSON response diambil dari functional test yang berjalan pada aplikasi versi 2026.01-SNAPSHOT.
