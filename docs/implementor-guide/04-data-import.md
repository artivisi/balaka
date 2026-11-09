# Import Data

Panduan menggunakan fitur import data untuk memasukkan data awal dan migrasi dari sistem lain.

## Halaman Import Data

Akses halaman import melalui menu **Pengaturan > Import Data**.

Halaman ini menerima file ZIP yang berisi kumpulan file CSV bernomor urut. Setiap file CSV merepresentasikan satu tabel data.

## Format File ZIP

File ZIP harus berisi file-file CSV dengan penamaan bernomor urut:

```
seed-data/
├── 01_company_config.csv
├── 02_chart_of_accounts.csv
├── 03_salary_components.csv
├── 04_journal_templates.csv
├── 05_journal_template_lines.csv
├── 06_journal_template_tags.csv
├── 07_clients.csv
├── 08_projects.csv
├── 11_fiscal_periods.csv
├── 12_tax_deadlines.csv
├── 13_company_bank_accounts.csv
├── 15_employees.csv
├── 16_employee_salary_components.csv
├── 34_asset_categories.csv
└── MANIFEST.md
```

Nomor urut menentukan urutan import. File diproses secara berurutan karena ada dependensi antar data (misalnya template jurnal bergantung pada COA yang sudah ada).

## File CSV per Entity

### 01_company_config.csv — Konfigurasi Perusahaan

```csv
company_name,company_address,company_phone,company_email,tax_id,npwp,nitku,fiscal_year_start_month,currency_code,signing_officer_name,signing_officer_title,company_logo_path,established_date,is_pkp,pkp_since,industry
PT Maju Teknologi,Jl. Sudirman 10 Jakarta,021-5551234,admin@maju.co.id,,01.234.567.8-012.345,0000000000000001,1,IDR,Budi Santoso,Direktur,,2020-05-01,true,2023-01-15,IT_SERVICE
```

| Kolom | Keterangan |
|-------|-----------|
| company_name | Nama resmi perusahaan |
| npwp | NPWP format 16 digit |
| nitku | NITKU |
| fiscal_year_start_month | Bulan awal tahun fiskal (1 = Januari) |
| is_pkp | `true` atau `false` |
| pkp_since | Tanggal pengukuhan PKP (YYYY-MM-DD), kosongkan jika non-PKP |
| industry | `IT_SERVICE`, `ONLINE_SELLER`, `COFFEE_SHOP`, atau `CAMPUS` |

### 02_chart_of_accounts.csv — Bagan Akun

```csv
account_code,account_name,account_type,parent_code,normal_balance,active,is_permanent,fiscal_treatment
1,ASET,ASSET,,DEBIT,true,true
1.1,Aset Lancar,ASSET,1,DEBIT,true,true
1.1.01,Kas,ASSET,1.1,DEBIT,true,true
1.1.02,Bank BCA,ASSET,1.1,DEBIT,true,true
```

| Kolom | Keterangan |
|-------|-----------|
| account_code | Kode akun (hierarchical, pakai titik) |
| account_type | `ASSET`, `LIABILITY`, `EQUITY`, `REVENUE`, `EXPENSE` |
| parent_code | Kode akun induk (kosong untuk akun level tertinggi) |
| normal_balance | `DEBIT` atau `CREDIT` |
| is_permanent | `true` untuk akun neraca, `false` untuk akun laba rugi |
| fiscal_treatment | `DEDUCTIBLE`, `NON_DEDUCTIBLE`, `CONDITIONAL`, atau kosong. Hanya untuk akun beban yang bukan header |

### 04_journal_templates.csv — Template Jurnal

```csv
template_name,description,template_type,category,is_system,active
Bayar Beban Sewa,Pembayaran sewa kantor,SIMPLE,EXPENSE,false,true
Penjualan Tokopedia,Penjualan via Tokopedia,DETAILED,INCOME,false,true
```

| Kolom | Keterangan |
|-------|-----------|
| template_type | `SIMPLE` (satu input) atau `DETAILED` (multi input) |
| category | `INCOME`, `EXPENSE`, `PAYMENT`, `RECEIPT`, `TRANSFER` |
| is_system | `true` untuk template yang dijalankan otomatis oleh fitur tertentu |

### 05_journal_template_lines.csv — Baris Template Jurnal

```csv
template_name,line_order,account_code,account_hint,debit_formula,credit_formula,description_template
Bayar Beban Sewa,1,5.1.05,,amount,,Sewa bulan {month}
Bayar Beban Sewa,2,,BANK,,amount,Transfer dari {bank}
```

| Kolom | Keterangan |
|-------|-----------|
| template_name | Nama template (harus cocok dengan 04_journal_templates.csv) |
| account_code | Kode akun tetap (kosong jika pakai hint) |
| account_hint | `BANK`, `PENDAPATAN`, `BEBAN`, dll. (kosong jika pakai account_code) |
| debit_formula | Formula untuk sisi debit (kosong jika kredit) |
| credit_formula | Formula untuk sisi kredit (kosong jika debit) |

### 07_clients.csv — Data Klien/Customer

```csv
client_code,client_name,client_type,npwp,address,phone,email
CLI-001,PT Pelanggan Utama,CLIENT,01.234.567.8-012.345,Jl. Gatot Subroto 20,021-5559876,info@pelanggan.co.id
```

### 15_employees.csv — Data Karyawan

```csv
employee_code,employee_name,nik,npwp,ptkp_status,join_date,department,position,bank_name,bank_account,active
EMP-001,Budi Santoso,3201010101010001,01.234.567.8-012.345,TK/0,2020-05-01,IT,Developer,BCA,1234567890,true
```

| Kolom | Keterangan |
|-------|-----------|
| ptkp_status | Status PTKP: `TK/0`, `K/0`, `K/1`, `K/2`, `K/3` |
| join_date | Tanggal masuk kerja (YYYY-MM-DD) |

### 11_fiscal_periods.csv — Periode Fiskal

```csv
year,month,status,month_closed_at,month_closed_by,tax_filed_at,tax_filed_by
2025,1,OPEN,,,,
2025,2,OPEN,,,,
```

### 34_asset_categories.csv — Kategori Aset Tetap

```csv
category_name,useful_life_months,depreciation_method,asset_account_code,accumulated_depreciation_account_code,depreciation_expense_account_code
Komputer,48,STRAIGHT_LINE,1.2.01,1.2.02,5.1.12
Kendaraan,96,STRAIGHT_LINE,1.2.03,1.2.04,5.1.12
```

## Mengimpor Seed Pack

Seed pack yang disediakan Balaka sudah dalam format ZIP yang siap import:

1. Buka menu **Pengaturan > Import Data**
2. Klik **Choose File** dan pilih file ZIP seed pack
3. Klik **Import**
4. Sistem akan memproses semua file CSV secara berurutan
5. Setelah selesai, akan muncul pesan: "Import berhasil: X record data, Y dokumen dalam Z ms"

## Migrasi dari Spreadsheet

Jika klien sudah memiliki pembukuan di Excel/Google Sheets, langkah migrasinya:

### 1. Petakan COA Lama ke COA Balaka

Bandingkan daftar akun yang digunakan klien dengan COA bawaan seed pack. Tentukan:
- Akun yang sudah ada di COA bawaan (langsung pakai)
- Akun yang perlu ditambahkan ke COA Balaka
- Akun lama yang bisa digabungkan ke satu akun Balaka

### 2. Tentukan Tanggal Cutover

Pilih tanggal peralihan — biasanya awal bulan atau awal tahun fiskal. Semua transaksi sebelum tanggal ini diringkas menjadi saldo awal.

### 3. Input Saldo Awal

Gunakan template **Saldo Awal Tahun** atau **Jurnal Manual** untuk memasukkan saldo setiap akun per tanggal cutover.

Contoh: cutover 1 Januari 2025, maka input saldo per 31 Desember 2024:

| Akun | Debit | Kredit |
|------|-------|--------|
| 1.1.01 Kas | 5.000.000 | |
| 1.1.02 Bank BCA | 50.000.000 | |
| 1.1.10 Piutang Usaha | 25.000.000 | |
| 2.1.01 Hutang Usaha | | 15.000.000 |
| 3.1.01 Modal Disetor | | 50.000.000 |
| 3.2.01 Laba Ditahan | | 15.000.000 |

Total debit harus sama dengan total kredit.

### 4. Verifikasi

Setelah input saldo awal:
1. Buka **Laporan > Neraca**
2. Pastikan total Aset = total Liabilitas + Ekuitas
3. Buka **Laporan > Trial Balance**
4. Pastikan total debit = total kredit

## Migrasi dari Aplikasi Lain

Jika klien sebelumnya menggunakan aplikasi akuntansi lain:

1. Export data dari aplikasi lama (biasanya tersedia fitur export CSV)
2. Petakan format kolom ke format CSV Balaka
3. Untuk transaksi historis: cukup input saldo akhir per akun sebagai saldo awal (lebih praktis daripada mengimpor semua transaksi detail)
4. Untuk data master (COA, klien, karyawan): konversi ke format CSV Balaka dan import via ZIP

## Tips Import

- **Backup dulu** — sebelum import, pastikan ArtiVisi sudah memiliki backup database terbaru
- **Test di instance demo** — coba import di instance demo dulu sebelum di instance produksi klien
- **Perhatikan urutan** — file CSV harus bernomor urut karena ada dependensi (COA harus sebelum template)
- **Validasi total** — setelah import saldo awal, selalu cek trial balance dan neraca

## Langkah Selanjutnya

[Konfigurasi Template Jurnal](05-template-config.md)
