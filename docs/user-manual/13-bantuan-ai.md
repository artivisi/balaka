# Operasi Aplikasi dengan Bantuan AI

Aplikasi ini dirancang untuk dioperasikan sepenuhnya oleh AI assistant (Claude Code, Gemini CLI, atau AI lainnya). AI dapat menangani seluruh siklus hidup aplikasi — mulai dari instalasi server, migrasi data dari Excel, pencatatan transaksi harian, penggajian, pelaporan keuangan, hingga pelaporan pajak.

> **Cakupan AI:**
> - Instalasi dan deploy ke VPS via Ansible
> - Analisis data Excel existing (transaksi, saldo awal) dan generate seed data
> - Pencatatan transaksi harian (struk, invoice, text input)
> - Penggajian dan PPh 21
> - Tagihan vendor (bills)
> - Rekonsiliasi bank
> - Laporan keuangan dan analisis bisnis
> - Pelaporan pajak (PPN, PPh 21, PPh Badan)

---

## Gambaran Umum

### Siklus Hidup Operasi AI

```
1. Instalasi & Deploy
   AI clone repo GitHub → setup VPS via Ansible → aplikasi live
   |
2. Analisis Data Existing
   AI baca file Excel (transaksi, neraca) → analisis struktur akun
   |
3. Inisialisasi Data
   AI generate CSV (COA, template, saldo awal) → ZIP → POST /api/data-import
   |
4. Operasi Harian
   AI baca struk/invoice → matching template → posting transaksi
   AI buat tagihan vendor → approve → mark paid
   AI import mutasi bank → auto-match → rekonsiliasi
   |
5. Penggajian Bulanan
   AI buat payroll run → hitung PPh 21 → approve → posting ke jurnal
   |
6. Laporan & Analisis
   AI baca laporan keuangan → analisis → publikasi insight
   |
7. Pelaporan Pajak
   AI export e-Faktur, Bukti Potong → generate SPT → koreksi fiskal
```

### Prasyarat

- **AI assistant** dengan kemampuan HTTP API (Claude Code, Gemini CLI, atau tool dengan `curl`/HTTP client)
- **VPS** dengan Ubuntu 22.04+ dan akses SSH (untuk instalasi)
- **Domain** dengan DNS pointing ke VPS (untuk HTTPS)
- Aplikasi sudah ter-deploy dan dapat diakses via HTTPS

---

## API Discovery via OpenAPI Spec

Sebelum mulai berinteraksi dengan API, AI assistant membaca **OpenAPI spec** yang mendeskripsikan seluruh kemampuan API. Spec ini di-generate otomatis oleh springdoc-openapi dan tersedia tanpa autentikasi.

### Endpoint

```bash
GET /v3/api-docs
# Tidak perlu Authorization header
# Returns OpenAPI 3.x JSON spec
```

Swagger UI juga tersedia di `/swagger-ui.html` untuk eksplorasi interaktif.

### Isi OpenAPI Spec

| Bagian | Deskripsi |
|--------|-----------|
| `paths` | Seluruh endpoint API, auto-generated dari controller annotations |
| `components.schemas` | Request/response schema dari DTO records |
| `security` | Bearer token authentication scheme |
| `x-authentication` | Alur OAuth 2.0 Device Flow lengkap (step 1-3), daftar scope, expiry |
| `x-workflows` | 13 alur kerja end-to-end (receipt-based, text-based, direct posting, financial analysis, bank reconciliation, client onboarding, correction workflows, tax export) |
| `x-csv-files` | 16 spesifikasi CSV untuk data import (nama kolom, tipe data, catatan) |
| `x-industries` | Daftar kode industri yang didukung (`it-service`, `online-seller`, `coffee-shop`, `campus`) |
| `x-error-codes` | 7 kode error dan HTTP status |

### Cara AI Menggunakan OpenAPI Spec

```
1. AI membaca OpenAPI spec (GET /v3/api-docs)
   |
2. AI memahami:
   - Endpoint apa saja yang tersedia (dari paths)
   - Field apa yang wajib/opsional per endpoint (dari schemas)
   - Scope apa yang diperlukan (dari x-authentication)
   - Alur kerja (workflow) yang benar (dari x-workflows)
   |
3. AI mengikuti alur autentikasi dari x-authentication
   |
4. AI memanggil endpoint sesuai x-workflows
```

> **Langkah pertama integrasi**: Selalu `GET /v3/api-docs` untuk mengetahui endpoint, schema, dan workflow yang tersedia. Metadata AI ada di `x-` extensions.

---

## Setup Autentikasi

AI assistant memerlukan autentikasi untuk mengakses aplikasi. Proses ini menggunakan **OAuth 2.0 Device Flow** yang aman.

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

### Scope yang Tersedia

Token yang diterbitkan melalui Device Flow memiliki scope berikut:

| Scope | Hak Akses |
|-------|-----------|
| `drafts:create` | Membuat dan mengedit draft transaksi |
| `drafts:approve` | Approve/reject draft |
| `drafts:read` | Membaca draft |
| `analysis:read` | Membaca laporan keuangan |
| `analysis:write` | Mempublikasikan laporan analisis |
| `transactions:post` | Posting, koreksi, dan hapus transaksi DRAFT |
| `data:import` | Import data dari file ZIP |
| `bills:read` | Membaca vendor bills |
| `bills:create` | Membuat vendor bills |
| `bills:approve` | Approve dan mark-paid vendor bills |
| `bank-recon:read` | Membaca data rekonsiliasi bank |
| `bank-recon:write` | Import statement, matching, complete reconciliation |
| `tax-export:read` | Export data pajak dan membaca data karyawan/payroll |

Tanpa scope yang sesuai, request akan ditolak dengan HTTP 403.

---

## Instalasi dan Deploy

AI assistant dapat menginstal aplikasi dari nol ke VPS kosong menggunakan Ansible playbook yang tersedia di repository.

### Prasyarat

- VPS dengan Ubuntu 22.04+ (minimal 2GB RAM, 20GB disk)
- Domain yang sudah pointing ke IP VPS
- SSH key access ke VPS (root atau user dengan sudo)

### Alur Kerja

```
1. AI clone repository
   git clone https://github.com/artivisi/balaka.git
   |
2. AI konfigurasi inventory Ansible
   Edit deploy/ansible/inventory.ini — set IP, domain, credentials
   |
3. AI jalankan full setup (pertama kali)
   ansible-playbook -i inventory.ini site.yml
   |
   Roles yang dijalankan:
   - common: Java 21, system packages, firewall
   - postgresql: PostgreSQL 17, database + user
   - app: Systemd service, build JAR, Flyway migration
   - nginx: Reverse proxy, HTTPS (Let's Encrypt)
   |
4. AI verifikasi aplikasi berjalan
   curl -s https://domain.example.com/actuator/health
   → {"status":"UP"}
   |
5. Untuk deploy selanjutnya (update kode):
   ansible-playbook -i inventory.ini deploy.yml
```

### Struktur Ansible

```
deploy/ansible/
  site.yml              # Full setup (pertama kali)
  deploy.yml            # Deploy update (build + restart)
  setup-ssl.yml         # Setup Let's Encrypt SSL
  backup.yml            # Backup database
  restore.yml           # Restore database
  requirements.yml      # Ansible Galaxy dependencies
  roles/
    common/tasks/       # Java, packages, firewall
    postgresql/tasks/   # PostgreSQL setup
    app/tasks/          # Application service
    nginx/tasks/        # Reverse proxy
    backup/tasks/       # Automated backup
```

### Konfigurasi Inventory

AI perlu membuat file `inventory.ini` dengan konfigurasi server:

```ini
[app]
server1 ansible_host=IP_VPS ansible_user=root

[app:vars]
app_domain=balaka.example.com
db_name=accountingdb
db_user=akunting
db_password=SECURE_PASSWORD
```

### Verifikasi

Setelah deploy, AI memverifikasi:

```bash
# Health check
curl -s https://balaka.example.com/actuator/health

# OpenAPI spec tersedia
curl -s https://balaka.example.com/v3/api-docs | head -c 200

# Login page accessible
curl -s -o /dev/null -w "%{http_code}" https://balaka.example.com/login
# → 200
```

---

## Analisis Data Existing

Sebelum menginisialisasi aplikasi, AI menganalisis data keuangan yang sudah ada (biasanya dalam format Excel) untuk merancang struktur Chart of Accounts (COA) dan template jurnal yang sesuai.

### Alur Kerja

```
1. User memberikan file Excel ke AI
   - Laporan keuangan (neraca, laba rugi)
   - Buku besar / general ledger
   - Data transaksi harian
   - Daftar aset tetap
   - Data karyawan dan gaji
   |
2. AI menganalisis struktur data
   - Mengidentifikasi akun-akun yang digunakan
   - Mengelompokkan ke ASSET, LIABILITY, EQUITY, REVENUE, EXPENSE
   - Menentukan parent-child hierarchy
   - Mengidentifikasi pola transaksi berulang
   |
3. AI merancang COA
   - Memetakan akun Excel ke format aplikasi
   - Menentukan account_code, account_type, normal_balance
   - Mengatur hierarki (parent_code)
   |
4. AI merancang template jurnal
   - Mengidentifikasi pola transaksi berulang (listrik, gaji, penjualan, dll)
   - Membuat template dengan formula yang sesuai
   - Mengisi metadata AI (keywords, merchant patterns, semantic description)
   |
5. AI menghitung saldo awal
   - Mengambil saldo per akun dari neraca terakhir
   - Membuat transaksi saldo awal (template "Saldo Awal")
   |
6. AI generate CSV files → ZIP → POST /api/data-import
```

### Contoh Analisis Excel

**Input:** File Excel "Buku Besar 2025.xlsx" dengan kolom:
```
Tanggal | Kode Akun | Nama Akun | Debit | Kredit | Keterangan
```

**Output AI:**

1. **COA yang diidentifikasi:**
   - `1.1.01` Kas (ASSET, DEBIT)
   - `1.1.02` Bank BCA (ASSET, DEBIT)
   - `2.1.01` Hutang Usaha (LIABILITY, CREDIT)
   - `4.1.01` Pendapatan Jasa (REVENUE, CREDIT)
   - `5.1.01` Beban Gaji (EXPENSE, DEBIT)
   - ... dst

2. **Template yang disarankan:**
   - "Pendapatan Jasa Konsultasi" — pola: klien bayar ke bank, keywords: konsultasi, jasa, proyek
   - "Bayar Beban Listrik" — pola: kas/bank keluar ke PLN, keywords: listrik, pln
   - "Bayar Gaji Karyawan" — pola: kas/bank keluar untuk gaji, keywords: gaji, salary
   - ... dst

3. **Saldo awal per 31 Desember 2025:**
   - Kas: Rp 25.000.000 (D)
   - Bank BCA: Rp 100.000.000 (D)
   - Hutang Usaha: Rp 15.000.000 (C)
   - Modal: Rp 110.000.000 (C)

### Referensi Industry Seed Pack

Untuk mempercepat analisis, AI dapat melihat contoh seed data per industri di repository:

```
industry-seed/
  it-service/seed-data/       # Jasa IT & Konsultasi
  online-seller/seed-data/    # Toko Online / E-Commerce
  coffee-shop/seed-data/      # Kedai Kopi / F&B
  campus/seed-data/           # Kampus / Pendidikan
```

Setiap seed pack berisi file CSV lengkap (COA, template, produk, BOM, dll) yang bisa dijadikan referensi struktur.

---

## Inisialisasi Data

Setelah menganalisis data existing, AI membuat file CSV dan mengirimnya ke aplikasi via `POST /api/data-import`.

### Alur Kerja

```
1. AI generate file CSV sesuai format yang diharapkan
   |
2. AI membuat ZIP dari file-file CSV
   |
3. AI mengirim ZIP ke POST /api/data-import
   |
4. Aplikasi memproses ZIP: truncate tabel yang ada CSV-nya, lalu import
   |
5. AI memverifikasi data via GET /api/analysis/accounts
   |
6. AI membuat transaksi saldo awal jika diperlukan
```

### Format CSV

ZIP berisi file CSV dengan nama berurut (dependency order). File utama untuk onboarding:

**`01_company_config.csv`** -- Konfigurasi perusahaan (1 baris data):

```
company_name,company_address,company_phone,company_email,tax_id,npwp,nitku,fiscal_year_start_month,currency_code,signing_officer_name,signing_officer_title,company_logo_path,established_date,is_pkp,pkp_since,industry
```

**`02_chart_of_accounts.csv`** -- Daftar akun:

```
account_code,account_name,account_type,parent_code,normal_balance,active,is_permanent
```

- `account_type`: `ASSET`, `LIABILITY`, `EQUITY`, `REVENUE`, `EXPENSE`
- `normal_balance`: `DEBIT` atau `CREDIT`
- `parent_code`: kosong jika akun top-level, isi kode parent jika sub-akun

**`04_journal_templates.csv`** -- Template jurnal:

```
template_name,category,cash_flow_category,template_type,description,is_system,active,version,usage_count,last_used_at,semantic_description,keywords,example_merchants,typical_amount_min,typical_amount_max,merchant_patterns
```

- `category`: `REVENUE`, `EXPENSE`, `TRANSFER`, `ADJUSTMENT`, `PAYROLL`, `TAX`, `RECEIVABLE`, `PAYABLE`, `ASSET_PURCHASE`, `ASSET_DEPRECIATION`
- `cash_flow_category`: `OPERATING`, `INVESTING`, `FINANCING`
- `template_type`: `SIMPLE`, `VARIABLE`, `SPLIT`, `MULTI_LINE`
- `keywords`, `example_merchants`, `merchant_patterns`: nilai dipisahkan dengan `|` (pipe)

**`05_journal_template_lines.csv`** -- Baris template jurnal:

```
template_name,line_order,account_code,account_hint,position,formula,description
```

- `position`: `DEBIT` atau `CREDIT`
- `formula`: `AMOUNT` (nilai penuh), `AMOUNT * 0.11` (PPN 11%), dsb.

### API Import

```bash
curl -X POST https://balaka.example.com/api/data-import \
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

### Populasi Saldo Awal

Setelah COA dan template ter-import, AI membuat transaksi saldo awal:

```bash
POST /api/transactions
Authorization: Bearer {accessToken}
Content-Type: application/json
```

```json
{
  "templateId": "UUID-template-saldo-awal",
  "description": "Saldo awal per 31 Desember 2025",
  "amount": 125000000,
  "transactionDate": "2025-12-31",
  "source": "claude-code",
  "userApproved": true
}
```

Ulangi untuk setiap akun yang memiliki saldo awal. Atau gunakan data import dengan file `18_transactions.csv` yang berisi transaksi saldo awal.

### Verifikasi

```bash
# Cek COA ter-import
GET /api/analysis/accounts

# Cek saldo awal tercatat
GET /api/analysis/trial-balance?asOfDate=2025-12-31

# Cek neraca balance
GET /api/analysis/balance-sheet?asOfDate=2025-12-31
```

### Catatan

- Import mengganti (truncate) data pada tabel yang memiliki CSV di dalam ZIP. Tabel tanpa CSV di ZIP tidak terpengaruh
- File CSV boleh hanya berisi header (tanpa data) -- tabel tersebut akan dilewati
- Lihat direktori `industry-seed/` di source code untuk contoh lengkap seed data per industri

---

## Pencatatan Transaksi

AI assistant mencatat transaksi akuntansi melalui API -- baik dari struk, invoice, text input, maupun input langsung.

### Template Journal dengan Metadata

AI memilih template journal berdasarkan metadata semantik. Untuk melihat template yang tersedia:

```bash
GET /api/templates
Authorization: Bearer {accessToken}
```

Response (contoh 1 template):

```json
[
  {
    "id": "1bbc7ccc-4e8f-44ef-87d3-0cd011fbc56d",
    "name": "Bayar Beban Listrik",
    "category": "EXPENSE",
    "semanticDescription": "Gunakan template ini untuk mencatat pembayaran tagihan listrik bulanan ke PLN.",
    "keywords": ["listrik", "electricity", "pln", "token", "utility"],
    "exampleMerchants": ["PLN", "PLN Mobile", "Tokopedia PLN"],
    "typicalAmountMin": 50000,
    "typicalAmountMax": 5000000,
    "merchantPatterns": [".*pln.*", ".*listrik.*"],
    "lines": [
      {
        "lineOrder": 1,
        "position": "DEBIT",
        "accountCode": "5.1.05",
        "accountName": "Beban Listrik",
        "accountHint": null,
        "formula": "AMOUNT"
      },
      {
        "lineOrder": 2,
        "position": "CREDIT",
        "accountId": null,
        "accountCode": null,
        "accountName": null,
        "accountHint": "Kas / Bank",
        "formula": "AMOUNT"
      }
    ]
  }
]
```

Keterangan:
- **accountCode/accountName**: Akun tetap (sudah ditentukan di template)
- **accountHint**: Akun yang perlu dipilih saat transaksi (gunakan `accountSlots` dengan accountHint sebagai key)

AI mencocokkan template berdasarkan: keyword matching, merchant matching, regex pattern (`merchantPatterns`), amount range, dan semantic similarity.

### Posting Transaksi Langsung

Setelah matching template dan user approve:

```bash
POST /api/transactions
Authorization: Bearer {accessToken}
Content-Type: application/json
```

```json
{
  "templateId": "UUID-template",
  "merchant": "PLN",
  "amount": 350000,
  "transactionDate": "2026-02-10",
  "description": "Bayar listrik Januari 2026",
  "source": "claude-code",
  "userApproved": true,
  "accountSlots": {
    "Kas / Bank": "UUID-akun-bank-bca"
  }
}
```

`accountSlots` memetakan `accountHint` dari template ke UUID akun yang dipilih.

Untuk template DETAILED (formula non-standar), tambahkan `variables`:

```json
{
  "templateId": "UUID-template-pembelian-aset",
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

Response:

```json
{
  "transactionId": "83da31e4-acdc-4ca9-898e-df2cefbe95c7",
  "transactionNumber": "TRX-2026-0001",
  "status": "POSTED",
  "amount": 350000,
  "journalEntries": [
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
  ]
}
```

Transaksi yang dibuat via AI terlihat di halaman Transaksi:

![Daftar Transaksi](screenshots/ai-transaction/04-transactions-list.png)

### Alur Draft (Review Sebelum Posting)

Jika AI ingin user review sebelum posting, gunakan alur draft:

```
POST /api/drafts              → buat DRAFT
GET /api/transactions/{id}/journal-preview  → preview jurnal
PUT /api/transactions/{id}    → koreksi (opsional)
POST /api/transactions/{id}/post            → posting
```

**Buat draft:**

```bash
POST /api/drafts
```

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

**Preview jurnal:**

```bash
GET /api/transactions/{id}/journal-preview
```

```json
{
  "valid": true,
  "errors": [],
  "entries": [
    { "accountCode": "5.1.05", "accountName": "Beban Listrik", "debitAmount": 350000, "creditAmount": 0 },
    { "accountCode": "1.1.02", "accountName": "Bank BCA", "debitAmount": 0, "creditAmount": 350000 }
  ],
  "totalDebit": 350000,
  "totalCredit": 350000
}
```

Jika `valid` bernilai `false`, field `errors` berisi daftar masalah.

**Posting:**

```bash
POST /api/transactions/{id}/post
```

**Batch posting:**

```bash
POST /api/transactions/bulk-post
```

```json
{
  "transactionIds": ["uuid-1", "uuid-2", "uuid-3"]
}
```

### Koreksi dan Pembatalan

```
Koreksi draft PENDING:
  PATCH /api/drafts/{id}

Koreksi transaksi DRAFT (belum posted):
  PUT /api/transactions/{id}

Hapus transaksi DRAFT:
  DELETE /api/transactions/{id}

Void transaksi POSTED (reversing entries otomatis):
  POST /api/transactions/{id}/void
```

**Koreksi transaksi DRAFT:**

```json
PUT /api/transactions/{id}
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
- `accountSlots` dapat dikirim berulang kali (idempotent) -- slot sebelumnya akan diganti

### Template CRUD via API

AI juga dapat membuat dan mengelola template jurnal:

```
GET /api/templates           → list semua template
GET /api/templates/{id}      → detail template
POST /api/templates          → buat template baru
PUT /api/templates/{id}      → update template
DELETE /api/templates/{id}   → hapus (soft delete)
```

---

## Penggajian (Payroll)

AI mengelola siklus penggajian lengkap -- dari setup karyawan hingga posting jurnal gaji dan perhitungan PPh 21.

### Alur Kerja

```
1. Setup komponen gaji
   POST /api/salary-components (Gaji Pokok, Tunjangan Transport, dll)
   |
2. Setup karyawan
   POST /api/employees
   POST /api/employees/{id}/salary-components (assign komponen + nominal)
   |
3. Buat payroll run bulanan
   POST /api/payroll { "month": 1, "year": 2026, "description": "Gaji Januari 2026" }
   |
4. Hitung PPh 21
   POST /api/payroll/{id}/calculate
   → Sistem menghitung: bruto → biaya jabatan → neto → PTKP → PKP → tarif progresif
   |
5. Review dan approve
   GET /api/payroll/{id} (review detail per karyawan)
   POST /api/payroll/{id}/approve
   |
6. Posting ke jurnal akuntansi
   POST /api/payroll/{id}/post
   |
7. Generate data pajak
   GET /api/payroll/employees/{id}/1721-a1?year=2026
   GET /api/payroll/pph21/summary?year=2026
```

### Komponen Gaji

```bash
# List komponen aktif
GET /api/salary-components

# Buat komponen
POST /api/salary-components
{
  "name": "Gaji Pokok",
  "type": "EARNING",
  "taxable": true,
  "description": "Gaji pokok bulanan"
}

# Update
PUT /api/salary-components/{id}

# Nonaktifkan
DELETE /api/salary-components/{id}
```

### Karyawan

```bash
# List karyawan (filter: active, status)
GET /api/employees?active=true

# Buat karyawan
POST /api/employees
{
  "employeeId": "EMP-001",
  "fullName": "Ahmad Fauzi",
  "email": "ahmad@example.com",
  "joinDate": "2024-01-15",
  "ptkpStatus": "K_1",
  "npwp": "12.345.678.9-012.000",
  "bankAccountNumber": "1234567890",
  "bankName": "BCA"
}

# Assign komponen gaji
POST /api/employees/{id}/salary-components
{
  "salaryComponentId": "UUID-gaji-pokok",
  "amount": 8000000,
  "effectiveDate": "2024-01-15"
}
```

### Payroll Run

```bash
# Buat payroll (DRAFT)
POST /api/payroll
{
  "month": 1,
  "year": 2026,
  "description": "Gaji Januari 2026"
}

# Hitung PPh 21 (annualized progressive rate)
POST /api/payroll/{id}/calculate

# Review detail
GET /api/payroll/{id}
# → Response berisi detail per karyawan: bruto, potongan, neto, PPh 21

# Approve
POST /api/payroll/{id}/approve

# Post ke jurnal akuntansi
POST /api/payroll/{id}/post

# Hapus (hanya DRAFT)
DELETE /api/payroll/{id}
```

### PPh 21

Perhitungan PPh 21 menggunakan metode annualisasi (Pasal 17 UU HPP):

| Lapisan PKP | Tarif |
|-------------|-------|
| 0 - 60 juta | 5% |
| 60 - 250 juta | 15% |
| 250 - 500 juta | 25% |
| 500 juta - 5 miliar | 30% |
| > 5 miliar | 35% |

PTKP (PMK 101/2016): TK/0 = Rp 54.000.000, K/0 = Rp 58.500.000, dst.

```bash
# Data 1721-A1 per karyawan
GET /api/payroll/employees/{id}/1721-a1?year=2026
# → Monthly breakdown: bruto, biaya jabatan, neto, PTKP, PKP, PPh 21

# Summary PPh 21 seluruh karyawan
GET /api/payroll/pph21/summary?year=2026
```

---

## Tagihan Vendor (Bills)

AI membuat tagihan vendor dari invoice/dokumen yang diterima, lalu melacak pembayarannya.

### Alur Kerja

```
1. AI menerima invoice vendor (PDF/foto)
   |
2. AI ekstrak data: vendor, tanggal, item, nominal
   |
3. AI buat bill
   POST /api/bills
   |
4. Review dan approve
   POST /api/bills/{id}/approve
   |
5. Setelah dibayar
   POST /api/bills/{id}/mark-paid
```

### API

```bash
# List bills (filter: status, vendor, date range)
GET /api/bills?status=DRAFT&vendorId=UUID&dateFrom=2026-01-01&dateTo=2026-01-31

# Detail bill
GET /api/bills/{id}

# Buat bill
POST /api/bills
{
  "vendorName": "PT Telkom Indonesia",
  "billDate": "2026-02-01",
  "dueDate": "2026-02-28",
  "vendorInvoiceNumber": "INV-TLK-2026-001",
  "notes": "Tagihan internet Februari 2026",
  "lines": [
    {
      "description": "Internet Dedicated 100 Mbps",
      "quantity": 1,
      "unitPrice": 5500000,
      "taxRate": 11,
      "expenseAccountCode": "5.1.06"
    }
  ]
}

# Approve
POST /api/bills/{id}/approve

# Mark paid
POST /api/bills/{id}/mark-paid
```

`vendorName` akan otomatis membuat vendor baru jika belum ada di database.

---

## Rekonsiliasi Bank

AI mengimpor mutasi bank, mencocokkan dengan transaksi di buku, dan menyelesaikan rekonsiliasi.

### Alur Kerja

```
1. Setup parser config (format CSV bank)
   GET /api/bank-reconciliation/parser-configs
   POST /api/bank-reconciliation/parser-configs (jika belum ada)
   |
2. Import statement bank
   POST /api/bank-reconciliation/statements/import
   |
3. Buat reconciliation session
   POST /api/bank-reconciliation/reconciliations
   |
4. Auto-match (3 pass: exact → fuzzy date → keyword)
   POST /api/bank-reconciliation/reconciliations/{id}/auto-match
   |
5. Manual match untuk item yang tidak ter-match otomatis
   POST /api/bank-reconciliation/reconciliations/{id}/match
   |
6. Tandai item bank-only atau book-only
   POST /api/bank-reconciliation/reconciliations/{id}/mark-bank-only
   POST /api/bank-reconciliation/reconciliations/{id}/mark-book-only
   |
7. Buat transaksi dari item bank yang belum tercatat
   POST /api/bank-reconciliation/reconciliations/{id}/create-transaction
   |
8. Review summary
   GET /api/bank-reconciliation/reconciliations/{id}/summary
   |
9. Complete reconciliation
   POST /api/bank-reconciliation/reconciliations/{id}/complete
```

### Import Statement

```bash
POST /api/bank-reconciliation/statements/import
Content-Type: multipart/form-data

# Form fields:
# - file: CSV file mutasi bank
# - bankAccountId: UUID akun bank (dari COA)
# - parserConfigId: UUID parser config
# - statementDate: tanggal statement (yyyy-MM-dd)
```

### Auto-Match

```bash
POST /api/bank-reconciliation/reconciliations/{id}/auto-match
```

Sistem menjalankan 3 pass:
1. **Exact match**: Jumlah dan tanggal persis sama
2. **Fuzzy date**: Jumlah sama, tanggal selisih 1-3 hari
3. **Keyword**: Deskripsi bank mengandung keyword transaksi

### Manual Match

```bash
POST /api/bank-reconciliation/reconciliations/{id}/match
{
  "bankStatementItemId": "UUID-item-bank",
  "transactionId": "UUID-transaksi"
}
```

### Buat Transaksi dari Item Bank

Untuk mutasi bank yang belum ada transaksinya di buku:

```bash
POST /api/bank-reconciliation/reconciliations/{id}/create-transaction
{
  "bankStatementItemId": "UUID-item-bank",
  "templateId": "UUID-template",
  "description": "Deskripsi transaksi"
}
```

### Outstanding Items

```bash
# Item yang belum ter-match
GET /api/bank-reconciliation/reconciliations/{id}/unmatched-book

# Outstanding items (bank vs book)
GET /api/bank-reconciliation/reconciliations/{id}/outstanding
```

---

## Laporan Keuangan dan Analisis

AI membaca laporan keuangan terstruktur via `/api/analysis/*`, menganalisis data, dan mempublikasikan insight kembali ke aplikasi.

### Endpoint Laporan

| Endpoint | Deskripsi | Parameter |
|----------|-----------|-----------|
| `GET /api/analysis/company` | Konfigurasi perusahaan (industry, currency, fiscal year) | -- |
| `GET /api/analysis/snapshot` | KPI bulanan (revenue, expense, profit, cash) | `month` (yyyy-MM) |
| `GET /api/analysis/trial-balance` | Neraca saldo | `asOfDate` (yyyy-MM-dd) |
| `GET /api/analysis/income-statement` | Laporan laba rugi | `startDate`, `endDate` |
| `GET /api/analysis/balance-sheet` | Neraca | `asOfDate` |
| `GET /api/analysis/cash-flow` | Laporan arus kas | `startDate`, `endDate` |
| `GET /api/analysis/tax-summary` | Ringkasan pajak (PPN, PPh) | `startDate`, `endDate` |
| `GET /api/analysis/receivables` | Piutang usaha | `asOfDate` |
| `GET /api/analysis/payables` | Hutang usaha | `asOfDate` |
| `GET /api/analysis/accounts` | Daftar akun (COA) | -- |
| `GET /api/analysis/accounts/{id}/ledger` | Buku besar per akun (running balance) | `startDate`, `endDate` |
| `GET /api/analysis/drafts` | Draft transaksi pending | -- |
| `GET /api/analysis/transactions` | List transaksi (filter: status, category, date, search) | multiple |
| `GET /api/analysis/transactions/{id}` | Detail transaksi dengan jurnal entries | -- |

Semua parameter **wajib** (tidak ada nilai default). Request tanpa parameter mengembalikan HTTP 400.

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

Field `metadata` memberikan konteks yang membantu AI memahami data (mata uang, basis akuntansi, penjelasan arti debit/credit).

### Contoh: KPI Snapshot

```bash
GET /api/analysis/snapshot?month=2026-01
Authorization: Bearer {accessToken}
```

```json
{
  "reportType": "snapshot",
  "data": {
    "month": "2026-01",
    "revenue": 50000000,
    "revenueChange": 12.5,
    "expense": 35000000,
    "expenseChange": -3.2,
    "netProfit": 15000000,
    "profitMargin": 30.0,
    "cashBalance": 125000000,
    "receivablesBalance": 20000000,
    "payablesBalance": 15000000,
    "transactionCount": 47,
    "cashBankItems": [
      { "accountName": "Kas", "balance": 25000000 },
      { "accountName": "Bank BCA", "balance": 100000000 }
    ]
  }
}
```

### Publikasi Laporan Analisis

Setelah AI menganalisis data, hasilnya dipublikasikan kembali ke aplikasi agar tim dapat membacanya di web UI (menu **Laporan** > **Analisis AI**).

```bash
POST /api/analysis/reports
Authorization: Bearer {accessToken}
Content-Type: application/json
```

Laporan terdiri dari 5 bagian terstruktur:

| Bagian | Deskripsi |
|--------|-----------|
| **Ringkasan Eksekutif** | Paragraf singkat kondisi keuangan periode ini |
| **Indikator Utama** | Metrik KPI spesifik industri dengan perubahan vs periode sebelumnya |
| **Temuan** | Hal-hal yang perlu diperhatikan, dengan kategori dan severity |
| **Rekomendasi** | Langkah-langkah yang disarankan, dengan prioritas dan dampak |
| **Penilaian Risiko** | Risiko yang teridentifikasi beserta mitigasi |

Sebelum analisis, AI membaca konfigurasi industri via `GET /api/analysis/company` untuk menentukan metrik yang relevan.

### Analisis per Industri

#### Jasa IT & Konsultasi (`it-service`)

**Tipe laporan:** `utilization-review`, `project-profitability`, `receivables-aging`, `client-concentration`, `tax-withholding-recon`

| Metrik | Formula | Benchmark |
|--------|---------|-----------|
| Utilisasi Billable | Jam billable / Total jam tersedia | 70-80% |
| DSO (Days Sales Outstanding) | (Piutang / Pendapatan) x Hari | < 45 hari |
| Rasio Biaya Tenaga Kerja | Total gaji / Pendapatan | 55-65% |
| Margin Proyek | (Revenue proyek - Biaya langsung) / Revenue proyek | > 15% sehat, > 25% kuat |
| Konsentrasi Klien Top-3 | Revenue 3 klien terbesar / Total revenue | < 60% sehat |

Risiko: konsentrasi klien, bench time, DSO creep, Bukti Potong PPh 23 hilang, key-person dependency.

#### Toko Online / E-Commerce (`online-seller`)

**Tipe laporan:** `marketplace-profitability`, `inventory-health`, `cash-conversion-cycle`, `channel-comparison`, `shipping-cost-analysis`

| Metrik | Formula | Benchmark |
|--------|---------|-----------|
| Gross Margin per Channel | (Revenue - HPP - Fee marketplace) / Revenue | > 30% |
| Inventory Turnover | HPP / Rata-rata persediaan | 4-8x/tahun |
| Dead Stock Ratio | Nilai stok mati / Total nilai persediaan | < 10% |
| Fee Marketplace Ratio | Total fee marketplace / Revenue | < 12% |
| Cash Conversion Cycle | DIO + DSO - DPO | < 60 hari |

Risiko: kenaikan fee marketplace, dead stock, cash conversion cycle panjang, ketergantungan satu marketplace.

#### Kedai Kopi / F&B (`coffee-shop`)

**Tipe laporan:** `food-cost-review`, `labor-efficiency`, `menu-engineering`, `production-cost`, `daily-sales-analysis`

| Metrik | Formula | Benchmark |
|--------|---------|-----------|
| Food Cost % | HPP / Revenue | 20-30% |
| Labor Cost % | Total biaya tenaga kerja / Revenue | 25-30% |
| Prime Cost % | (HPP + Biaya tenaga kerja) / Revenue | < 60% |
| Waste % | Nilai bahan terbuang / Total HPP | < 2-4% |
| Variance Resep | (HPP aktual - HPP teoritis) / HPP teoritis | < 2% |

Risiko: spoilage bahan perishable, over-portioning, kenaikan harga komoditas, prime cost > 65%.

#### Kampus / Pendidikan (`campus`)

**Tipe laporan:** `tuition-collection`, `budget-realization`, `scholarship-impact`, `faculty-cost-analysis`, `enrollment-forecast`

| Metrik | Formula | Benchmark |
|--------|---------|-----------|
| SPP Collection Rate | SPP terkumpul / SPP tertagih | > 90% |
| Tuition Dependency Ratio | Pendapatan SPP / Total pendapatan | < 85% |
| Operating Margin | (Total pendapatan - Total beban) / Total pendapatan | > 5% |
| Days Cash on Hand | Kas / (Total beban / 365) | > 90 hari |
| Piutang SPP > 90 Hari | Piutang aging > 90 hari / Total piutang SPP | < 5% |

Risiko: penurunan mahasiswa baru, ketergantungan SPP > 90%, cash flow musiman, piutang SPP tidak tertagih.

### Format Field Laporan Analisis

**Indikator Utama (metrics):**

| Field | Wajib | Deskripsi |
|-------|-------|-----------|
| `name` | Ya | Nama metrik sesuai industri |
| `value` | Ya | Nilai saat ini |
| `previousValue` | Tidak | Nilai periode sebelumnya |
| `change` | Tidak | Perubahan (contoh: "+12.5%", "+4pp") |
| `status` | Tidak | `positive` (hijau), `negative` (merah), `warning` (kuning), `neutral` |

**Temuan (findings):**

| Field | Wajib | Deskripsi |
|-------|-------|-----------|
| `category` | Ya | Kategori sesuai industri |
| `severity` | Ya | `info`, `warning`, `critical` |
| `description` | Ya | Deskripsi temuan |

**Rekomendasi (recommendations):**

| Field | Wajib | Deskripsi |
|-------|-------|-----------|
| `priority` | Ya | `high`, `medium`, `low` |
| `description` | Ya | Deskripsi rekomendasi |
| `impact` | Tidak | Dampak yang diharapkan |

**Risiko (risks):**

| Field | Wajib | Deskripsi |
|-------|-------|-----------|
| `severity` | Ya | `high`, `medium`, `low` |
| `description` | Ya | Deskripsi risiko |
| `mitigation` | Tidak | Langkah mitigasi |

### Melihat Laporan di Web UI

Laporan yang sudah dipublikasikan tersedia di menu **Laporan** > **Analisis AI**.

![Daftar Laporan Analisis](screenshots/analysis-reports/list.png)

![Detail Laporan - Header dan Metrik](screenshots/analysis-reports/detail-top.png)

![Detail Laporan - Temuan dan Rekomendasi](screenshots/analysis-reports/detail-bottom.png)

### Alur Lengkap

```
1. AI membaca data keuangan
   GET /api/analysis/snapshot?month=2026-01
   GET /api/analysis/income-statement?startDate=2026-01-01&endDate=2026-01-31
   GET /api/analysis/receivables?asOfDate=2026-01-31
   |
2. AI menganalisis data dan membuat laporan terstruktur
   |
3. AI mempublikasikan laporan
   POST /api/analysis/reports
   |
4. Tim membuka menu Laporan > Analisis AI di web browser
   |
5. Akuntan membaca temuan, rekomendasi, dan risiko
   |
6. Tindak lanjut: buat jurnal penyesuaian, tagih piutang, dll.
```

---

## Perpajakan

AI mengelola pelaporan pajak -- dari pengisian detail pajak per transaksi hingga export data untuk e-Faktur, e-Bupot, dan SPT.

### Alur Kerja Pajak Bulanan

```
1. Transaksi harian sudah ter-posting dengan template yang benar
   (template PPN otomatis menghitung PPN 11%)
   |
2. AI mengisi detail pajak per transaksi
   POST /api/transactions/{id}/tax-details
   |
3. AI upload dokumen pendukung (faktur, bukti potong)
   POST /api/transactions/{id}/documents
   |
4. Akhir bulan: AI export data pajak
   GET /api/tax-export/efaktur-keluaran?startMonth=2026-01&endMonth=2026-01&format=json
   GET /api/tax-export/efaktur-masukan?startMonth=2026-01&endMonth=2026-01&format=json
   GET /api/tax-export/bupot-unifikasi?startMonth=2026-01&endMonth=2026-01&format=json
   |
5. AI review ringkasan
   GET /api/tax-export/ppn-detail?startMonth=2026-01&endMonth=2026-01
   GET /api/tax-export/pph23-detail?startMonth=2026-01&endMonth=2026-01
   |
6. AI generate data SPT PPh Badan (tahunan)
   GET /api/tax-export/pph-badan?year=2025
   GET /api/tax-export/rekonsiliasi-fiskal?year=2025
```

### Detail Pajak per Transaksi

Setiap transaksi PPN/PPh perlu dilengkapi detail pajak (nomor faktur, NPWP lawan transaksi, dll):

```bash
# List tax details
GET /api/transactions/{id}/tax-details

# Tambah detail
POST /api/transactions/{id}/tax-details
{
  "taxType": "PPN_KELUARAN",
  "taxNumber": "010.000-26.00000001",
  "counterpartyNpwp": "01.234.567.8-901.000",
  "counterpartyName": "PT Klien ABC",
  "taxBase": 10000000,
  "taxAmount": 1100000,
  "taxDate": "2026-01-15"
}

# Update
PUT /api/transactions/{id}/tax-details/{detailId}

# Hapus
DELETE /api/transactions/{id}/tax-details/{detailId}

# Bulk operations
POST /api/tax-details/bulk
```

### Dokumen Pendukung

```bash
# Upload dokumen
POST /api/transactions/{id}/documents
Content-Type: multipart/form-data
# Form field: file (PDF, image)

# List dokumen
GET /api/transactions/{id}/documents

# Download
GET /api/transactions/{id}/documents/{docId}

# Hapus
DELETE /api/transactions/{id}/documents/{docId}
```

### Export Pajak

Semua endpoint export mendukung `format=json` (untuk AI) dan `format=excel` (untuk upload ke DJP):

```bash
# e-Faktur Keluaran (PPN yang dipungut)
GET /api/tax-export/efaktur-keluaran?startMonth=2026-01&endMonth=2026-01&format=json

# e-Faktur Masukan (PPN yang dibayar)
GET /api/tax-export/efaktur-masukan?startMonth=2026-01&endMonth=2026-01&format=json

# Bukti Potong Unifikasi (PPh 23/26)
GET /api/tax-export/bupot-unifikasi?startMonth=2026-01&endMonth=2026-01&format=json

# Detail PPN (ringkasan Keluaran vs Masukan)
GET /api/tax-export/ppn-detail?startMonth=2026-01&endMonth=2026-12

# Detail PPh 23 (ringkasan potongan)
GET /api/tax-export/pph23-detail?startMonth=2026-01&endMonth=2026-12
```

### Koreksi Fiskal (SPT PPh Badan)

Untuk penyusunan SPT PPh Badan tahunan, AI mengelola koreksi fiskal:

```bash
# List koreksi fiskal per tahun
GET /api/fiscal-adjustments?year=2025

# Buat koreksi
POST /api/fiscal-adjustments
{
  "year": 2025,
  "category": "ENTERTAINMENT",
  "direction": "POSITIVE",
  "amount": 5000000,
  "description": "Biaya entertainment tanpa daftar nominatif",
  "notes": "Pasal 6 ayat 1 huruf a UU PPh"
}

# Update
PUT /api/fiscal-adjustments/{id}

# Hapus
DELETE /api/fiscal-adjustments/{id}
```

### Rekonsiliasi Fiskal dan PPh Badan

```bash
# Rekonsiliasi fiskal (laba komersial → laba fiskal)
GET /api/tax-export/rekonsiliasi-fiskal?year=2025
# → Laba komersial, koreksi positif, koreksi negatif, laba fiskal

# Perhitungan PPh Badan
GET /api/tax-export/pph-badan?year=2025
# → PKP, tarif, PPh terutang, kredit pajak, kurang/lebih bayar
```

### PPh 21 Karyawan

Data PPh 21 tersedia via Payroll API (lihat bagian Penggajian):

```bash
# 1721-A1 per karyawan
GET /api/payroll/employees/{id}/1721-a1?year=2026

# Summary PPh 21 seluruh karyawan
GET /api/payroll/pph21/summary?year=2026
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

Setiap user dapat melihat dan mencabut device token miliknya di halaman **Perangkat API**.

**Lokasi:** Sidebar > Master Data > **Perangkat API**, atau langsung buka `/settings/devices`

![Halaman Perangkat API](screenshots/settings/devices.png)

**Informasi per token:**

| Kolom | Deskripsi |
|-------|-----------|
| Perangkat | Nama device (jika diisi saat otorisasi) |
| Client ID | Identifier aplikasi (contoh: `claude-code`) |
| Scope | Hak akses token (contoh: `drafts:create,analysis:read`) |
| Status | Aktif (hijau) atau Kedaluwarsa (abu-abu) |
| Dibuat | Waktu token diterbitkan |
| Terakhir Digunakan | Waktu terakhir token digunakan |
| IP Terakhir | IP address terakhir yang menggunakan token |

**Aksi:**
- **Cabut** (per token): Mencabut satu token tertentu
- **Cabut Semua**: Mencabut semua token aktif sekaligus

> **Catatan**: Setelah token dicabut, AI assistant yang menggunakan token tersebut tidak dapat mengakses API lagi. User perlu melakukan device flow ulang untuk mendapatkan token baru.

**Admin view**: Administrator juga dapat melihat dan mencabut token dari halaman detail pengguna di menu **Pengguna** > klik user > bagian **Sesi Perangkat Aktif**.

---

## API Reference

### Discovery (public, tanpa autentikasi)

| Method | Endpoint | Deskripsi |
|--------|----------|-----------|
| GET | `/v3/api-docs` | OpenAPI spec -- seluruh endpoint, schema, x- extensions |
| GET | `/swagger-ui.html` | Swagger UI -- eksplorasi API interaktif |

### Autentikasi (public)

| Method | Endpoint | Deskripsi |
|--------|----------|-----------|
| POST | `/api/device/code` | Request device code |
| POST | `/api/device/token` | Poll for access token |

### Draft Transaksi (scope: `drafts:*`, `transactions:post`)

| Method | Endpoint | Deskripsi |
|--------|----------|-----------|
| POST | `/api/drafts` | Buat DRAFT transaction dengan template + overrides |
| POST | `/api/drafts/from-receipt` | Buat draft dari struk |
| POST | `/api/drafts/from-text` | Buat draft dari teks |
| GET | `/api/drafts/{id}` | Get draft by ID |
| PATCH | `/api/drafts/{id}` | Koreksi draft PENDING |
| POST | `/api/drafts/{id}/approve` | Approve draft |
| POST | `/api/drafts/{id}/reject` | Reject draft |

### Template (scope: `drafts:read`, `drafts:create`)

| Method | Endpoint | Deskripsi |
|--------|----------|-----------|
| GET | `/api/templates` | List templates dengan metadata dan lines |
| GET | `/api/templates/{id}` | Get single template dengan lines |
| POST | `/api/templates` | Buat template baru |
| PUT | `/api/templates/{id}` | Update template |
| DELETE | `/api/templates/{id}` | Hapus template (soft delete) |

### Transaksi (scope: `transactions:post`)

| Method | Endpoint | Deskripsi |
|--------|----------|-----------|
| POST | `/api/transactions` | Post transaction langsung |
| PUT | `/api/transactions/{id}` | Koreksi transaksi DRAFT |
| DELETE | `/api/transactions/{id}` | Hapus transaksi DRAFT |
| POST | `/api/transactions/{id}/post` | Post satu transaksi DRAFT |
| POST | `/api/transactions/{id}/void` | Void transaksi POSTED |
| GET | `/api/transactions/{id}/journal-preview` | Preview jurnal entries |
| POST | `/api/transactions/bulk-post` | Batch post transaksi DRAFT |

### Analisis Keuangan (scope: `analysis:read`)

| Method | Endpoint | Deskripsi |
|--------|----------|-----------|
| GET | `/api/analysis/company` | Konfigurasi perusahaan |
| GET | `/api/analysis/snapshot` | KPI bulanan |
| GET | `/api/analysis/trial-balance` | Neraca saldo |
| GET | `/api/analysis/income-statement` | Laporan laba rugi |
| GET | `/api/analysis/balance-sheet` | Neraca |
| GET | `/api/analysis/cash-flow` | Laporan arus kas |
| GET | `/api/analysis/tax-summary` | Ringkasan pajak |
| GET | `/api/analysis/receivables` | Piutang usaha |
| GET | `/api/analysis/payables` | Hutang usaha |
| GET | `/api/analysis/accounts` | Daftar akun (COA) |
| GET | `/api/analysis/accounts/{id}/ledger` | Buku besar per akun |
| GET | `/api/analysis/drafts` | Draft transaksi pending |
| GET | `/api/analysis/transactions` | List transaksi dengan filter |
| GET | `/api/analysis/transactions/{id}` | Detail transaksi + jurnal entries |
| GET | `/api/analysis/reports` | Daftar laporan analisis |

### Publikasi Laporan Analisis (scope: `analysis:write`)

| Method | Endpoint | Deskripsi |
|--------|----------|-----------|
| POST | `/api/analysis/reports` | Publikasi laporan analisis terstruktur |

### Tagihan Vendor (scope: `bills:*`)

| Method | Endpoint | Deskripsi |
|--------|----------|-----------|
| GET | `/api/bills` | List bills dengan filter |
| GET | `/api/bills/{id}` | Detail bill |
| POST | `/api/bills` | Buat bill baru |
| POST | `/api/bills/{id}/approve` | Approve bill |
| POST | `/api/bills/{id}/mark-paid` | Mark bill as paid |

### Rekonsiliasi Bank (scope: `bank-recon:*`)

| Method | Endpoint | Deskripsi |
|--------|----------|-----------|
| GET | `/api/bank-reconciliation/parser-configs` | List parser configs |
| POST | `/api/bank-reconciliation/parser-configs` | Buat parser config |
| POST | `/api/bank-reconciliation/statements/import` | Import statement CSV |
| GET | `/api/bank-reconciliation/statements/{id}` | Detail statement |
| GET | `/api/bank-reconciliation/statements/{id}/items` | Items statement |
| POST | `/api/bank-reconciliation/reconciliations` | Buat reconciliation session |
| GET | `/api/bank-reconciliation/reconciliations/{id}` | Detail reconciliation |
| POST | `/api/bank-reconciliation/reconciliations/{id}/auto-match` | Auto-match 3 pass |
| POST | `/api/bank-reconciliation/reconciliations/{id}/match` | Manual match |
| POST | `/api/bank-reconciliation/reconciliations/{id}/mark-bank-only` | Mark bank-only |
| POST | `/api/bank-reconciliation/reconciliations/{id}/mark-book-only` | Mark book-only |
| DELETE | `/api/bank-reconciliation/reconciliations/{id}/items/{itemId}` | Remove match |
| POST | `/api/bank-reconciliation/reconciliations/{id}/create-transaction` | Create from bank item |
| POST | `/api/bank-reconciliation/reconciliations/{id}/complete` | Complete reconciliation |
| GET | `/api/bank-reconciliation/reconciliations/{id}/unmatched-book` | Unmatched book items |
| GET | `/api/bank-reconciliation/reconciliations/{id}/summary` | Reconciliation summary |
| GET | `/api/bank-reconciliation/reconciliations/{id}/statement` | Statement data |
| GET | `/api/bank-reconciliation/reconciliations/{id}/outstanding` | Outstanding items |

### Komponen Gaji (scope: `tax-export:read`)

| Method | Endpoint | Deskripsi |
|--------|----------|-----------|
| GET | `/api/salary-components` | Daftar komponen gaji aktif |
| POST | `/api/salary-components` | Buat komponen gaji |
| PUT | `/api/salary-components/{id}` | Update komponen gaji |
| DELETE | `/api/salary-components/{id}` | Nonaktifkan komponen gaji |

### Karyawan (scope: `tax-export:read`)

| Method | Endpoint | Deskripsi |
|--------|----------|-----------|
| GET | `/api/employees` | Daftar karyawan |
| POST | `/api/employees` | Buat karyawan |
| GET | `/api/employees/{id}` | Detail karyawan + komponen gaji |
| PUT | `/api/employees/{id}` | Update data karyawan |
| POST | `/api/employees/{id}/salary-components` | Assign komponen gaji |
| PUT | `/api/employees/{id}/salary-components/{componentId}` | Update assignment |

### Payroll (scope: `tax-export:read`)

| Method | Endpoint | Deskripsi |
|--------|----------|-----------|
| GET | `/api/payroll` | Daftar payroll run |
| POST | `/api/payroll` | Buat payroll run (DRAFT) |
| GET | `/api/payroll/{id}` | Detail payroll + semua detail karyawan |
| POST | `/api/payroll/{id}/calculate` | Hitung PPh 21 |
| POST | `/api/payroll/{id}/approve` | Set APPROVED |
| POST | `/api/payroll/{id}/post` | Posting ke jurnal |
| DELETE | `/api/payroll/{id}` | Hapus payroll DRAFT |
| GET | `/api/payroll/employees/{id}/1721-a1?year=YYYY` | Data 1721-A1 per karyawan |
| GET | `/api/payroll/pph21/summary?year=YYYY` | Ringkasan PPh 21 |

### Detail Pajak (scope: `tax-export:read`)

| Method | Endpoint | Deskripsi |
|--------|----------|-----------|
| GET | `/api/transactions/{id}/tax-details` | List tax details per transaksi |
| GET | `/api/transactions/{id}/tax-details/{detailId}` | Detail satu tax detail |
| POST | `/api/transactions/{id}/tax-details` | Tambah tax detail |
| PUT | `/api/transactions/{id}/tax-details/{detailId}` | Update tax detail |
| DELETE | `/api/transactions/{id}/tax-details/{detailId}` | Hapus tax detail |
| POST | `/api/tax-details/bulk` | Bulk operations |

### Dokumen (scope: `tax-export:read`)

| Method | Endpoint | Deskripsi |
|--------|----------|-----------|
| POST | `/api/transactions/{id}/documents` | Upload dokumen |
| GET | `/api/transactions/{id}/documents` | List dokumen |
| GET | `/api/transactions/{id}/documents/{docId}` | Download dokumen |
| DELETE | `/api/transactions/{id}/documents/{docId}` | Hapus dokumen |

### Export Pajak (scope: `tax-export:read`)

| Method | Endpoint | Deskripsi |
|--------|----------|-----------|
| GET | `/api/tax-export/efaktur-keluaran` | Export e-Faktur Keluaran |
| GET | `/api/tax-export/efaktur-masukan` | Export e-Faktur Masukan |
| GET | `/api/tax-export/bupot-unifikasi` | Export Bukti Potong Unifikasi |
| GET | `/api/tax-export/ppn-detail` | Detail PPN (Keluaran vs Masukan) |
| GET | `/api/tax-export/pph23-detail` | Detail PPh 23 |
| GET | `/api/tax-export/rekonsiliasi-fiskal` | Rekonsiliasi fiskal |
| GET | `/api/tax-export/pph-badan` | Perhitungan PPh Badan |

### Koreksi Fiskal (scope: `tax-export:read`)

| Method | Endpoint | Deskripsi |
|--------|----------|-----------|
| GET | `/api/fiscal-adjustments?year=YYYY` | Daftar koreksi fiskal per tahun |
| POST | `/api/fiscal-adjustments` | Buat koreksi fiskal baru |
| PUT | `/api/fiscal-adjustments/{id}` | Update koreksi fiskal |
| DELETE | `/api/fiscal-adjustments/{id}` | Hapus koreksi fiskal |

### Manajemen Data (scope: `data:import`)

| Method | Endpoint | Deskripsi |
|--------|----------|-----------|
| POST | `/api/data-import` | Import data dari file ZIP |

### Authentication

Endpoint yang **tidak** memerlukan autentikasi:
- `GET /v3/api-docs`
- `GET /swagger-ui.html`
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
| 400 | `MISSING_PARAMETER` | Parameter wajib tidak dikirim |
| 400 | `INVALID_REQUEST` | Request tidak valid |
| 401 | `unauthorized` | Token tidak valid atau expired |
| 403 | -- | Scope tidak memadai |
| 404 | `NOT_FOUND` | Resource tidak ditemukan |
| 409 | `CONFLICT` | State conflict (contoh: transaksi sudah POSTED) |
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
- Claude Code
- Gemini CLI
- ChatGPT dengan plugin
- Custom script (curl, Python, dll)

### Bagaimana jika AI salah pilih template?

1. **Koreksi draft**: `PATCH /api/drafts/{id}` sebelum approve
2. **Koreksi transaksi DRAFT**: `PUT /api/transactions/{id}` sebelum posting
3. **Hapus dan buat ulang**: `DELETE /api/transactions/{id}` lalu buat baru
4. **Void transaksi POSTED**: `POST /api/transactions/{id}/void` (reversing entries otomatis)
5. **Edit di web**: Setelah posted, edit di web UI

### Bagaimana cara menambah metadata ke template?

1. **Web UI**: Buka template > Edit > bagian **Metadata AI** > isi field > Simpan. Lihat [Referensi Template](12-lampiran-template.md#metadata-ai-pada-template) untuk detail field.
2. **API**: `PUT /api/templates/{id}`

### Apakah AI bisa deploy aplikasi dari nol?

Ya. Berikan AI akses SSH ke VPS kosong dan arahkan ke repository GitHub. AI akan menjalankan Ansible playbook untuk instalasi lengkap (Java, PostgreSQL, Nginx, SSL, aplikasi). Lihat bagian "Instalasi dan Deploy".

### Apakah AI bisa migrasi data dari Excel?

Ya. Berikan AI file Excel (buku besar, neraca, data transaksi). AI akan menganalisis struktur akun, merancang COA dan template jurnal, generate file CSV, lalu import ke aplikasi. Lihat bagian "Analisis Data Existing" dan "Inisialisasi Data".

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
1. Refresh template list: `GET /api/templates`
2. Pastikan template ID ada di list
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
- Template tidak balanced (debit != credit)
- Account tidak valid
- Tanggal transaksi invalid

**Solusi**:
1. Cek template lines (harus ada debit dan credit)
2. Cek account mapping
3. Pastikan tanggal tidak di masa depan

### Data import gagal

**Penyebab**: Format CSV tidak sesuai

**Solusi**:
1. Cek nama file CSV (harus berurut: `01_company_config.csv`, `02_chart_of_accounts.csv`, dst)
2. Cek header CSV (harus persis sesuai format yang didokumentasikan)
3. Cek dependency order (template lines membutuhkan template dan COA sudah ter-import)
4. Lihat contoh di `industry-seed/` untuk referensi format

### Ansible deploy gagal

**Penyebab**: Konfigurasi server atau inventory tidak benar

**Solusi**:
1. Verifikasi SSH access: `ssh user@server 'echo ok'`
2. Pastikan server memenuhi prasyarat (Ubuntu 22.04+, 2GB RAM)
3. Cek Ansible inventory file (IP, user, variables)
4. Jika Flyway migration gagal, cek log: `sudo journalctl -u aplikasi-akunting`

---

> **Info**: Dokumentasi ini ditulis berdasarkan test scenario yang sebenarnya. Semua screenshot dan JSON response diambil dari functional test yang berjalan pada aplikasi.
