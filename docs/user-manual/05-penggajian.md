# Penggajian

Panduan lengkap pengelolaan payroll, BPJS, dan PPh 21 karyawan.

## Setup Komponen Gaji

### Melihat Komponen Gaji

Buka menu **Penggajian** > **Komponen Gaji**.

![Daftar Komponen Gaji](screenshots/salary-components-list.png)

### Jenis Komponen

| Tipe | Contoh | Pengaruh ke Gaji |
|------|--------|------------------|
| **Pendapatan** | Gaji Pokok, Tunjangan | Menambah |
| **Potongan** | BPJS Karyawan, PPh 21 | Mengurangi |

### Menambah Komponen

1. Klik **Komponen Baru**

![Form Komponen Gaji](screenshots/salary-components-form.png)

2. Isi:
   - Nama komponen
   - Tipe (Pendapatan/Potongan)
   - Basis perhitungan (Fixed/Percentage)
   - Kena pajak (Ya/Tidak)
3. Klik **Simpan**

### Komponen Standar (dari Seed)

**Pendapatan:**
- Gaji Pokok
- Tunjangan Jabatan
- Tunjangan Kehadiran
- Tunjangan Makan
- Tunjangan Transport

**Potongan:**
- BPJS Kesehatan (Karyawan)
- BPJS Ketenagakerjaan JHT (Karyawan)
- BPJS Ketenagakerjaan JP (Karyawan)
- PPh 21

---

## Kelola Karyawan

### Melihat Daftar Karyawan

Buka menu **Penggajian** > **Karyawan**.

![Daftar Karyawan](screenshots/employees-list.png)

### Menambah Karyawan

1. Klik **Karyawan Baru**

![Form Karyawan](screenshots/employees-form.png)

2. Isi data:

**Data Pribadi:**
- NIK (Nomor Induk Karyawan)
- Nama lengkap
- Email, telepon, alamat

**Data Pajak:**
- NPWP
- Status PTKP

**Data Kepegawaian:**
- Jabatan, departemen
- Tanggal bergabung
- Tipe (Tetap/Kontrak)

**Data Bank:**
- Nama bank
- Nomor rekening

**Data BPJS:**
- No. BPJS Kesehatan
- No. BPJS Ketenagakerjaan

3. Klik **Simpan**

### Assign Komponen Gaji ke Karyawan

1. Buka detail karyawan
2. Tab **Komponen Gaji**
3. Klik **Tambah Komponen**
4. Pilih komponen, isi nilai, tanggal efektif, dan tanggal berakhir (opsional)
5. Klik **Simpan**

Satu komponen bisa di-assign lebih dari satu kali ke karyawan yang sama, selama **periode tidak tumpang tindih**. Ini berguna untuk mencatat perubahan gaji:

- Gaji Pokok Rp 5.000.000 (1 Jan 2025 – 30 Apr 2025)
- Gaji Pokok Rp 6.000.000 (1 Mei 2025 – seterusnya)

Jika tanggal berakhir tidak diisi, assignment berlaku tanpa batas waktu. Sistem akan menolak assignment baru yang periodenya tumpang tindih dengan assignment yang sudah ada.

---

## BPJS

### Tarif BPJS 2024

**BPJS Kesehatan:**

| Pihak | Tarif | Batas UMR |
|-------|-------|-----------|
| Perusahaan | 4% | Maks 12 juta |
| Karyawan | 1% | Maks 12 juta |

**BPJS Ketenagakerjaan:**

| Program | Perusahaan | Karyawan |
|---------|------------|----------|
| JHT | 3.7% | 2% |
| JKK | 0.24-1.74% | - |
| JKM | 0.3% | - |
| JP | 2% | 1% |

### Kalkulator BPJS

Buka menu **Penggajian** > **Kalkulator BPJS**.

![Kalkulator BPJS](screenshots/bpjs-calculator.png)

1. Masukkan gaji pokok
2. Sistem menghitung:
   - BPJS Kes (perusahaan + karyawan)
   - BPJS TK (JHT, JKK, JKM, JP)
   - Total beban perusahaan
   - Total potongan karyawan

---

## PPh 21 Karyawan

### Metode Perhitungan: TER (PMK 168/2023)

Aplikasi menggunakan metode **TER (Tarif Efektif Rata-rata)** sesuai PMK 168/2023, berlaku mulai Januari 2024.

**Januari–November:** PPh 21 bulanan = Gaji Bruto × Tarif TER

**Desember:** Rekonsiliasi tahunan — PPh 21 Desember = PPh 21 terutang setahun (tarif progresif PP 58/2023) dikurangi total PPh 21 Jan–Nov (TER).

#### Kategori TER

Status PTKP menentukan kategori TER:

| Kategori | Status PTKP |
|----------|-------------|
| **A** | TK/0, TK/1, K/0 |
| **B** | TK/2, TK/3, K/1, K/2 |
| **C** | K/3, K/I/0, K/I/1, K/I/2, K/I/3 |

Tarif TER ditentukan berdasarkan bracket penghasilan bruto bulanan dalam kategori tersebut (lihat PMK 168/2023 Lampiran A/B/C).

#### Contoh Perhitungan

Karyawan dengan status K/2, gaji bruto Rp 11.253.000:
- Kategori TER: **B**
- Tarif TER: **2,50%**
- PPh 21 bulanan: Rp 11.253.000 × 2,50% = **Rp 281.325**

### Kalkulator PPh 21

Buka menu **Penggajian** > **Kalkulator PPh 21**.

![Kalkulator PPh 21](screenshots/pph21-calculator.png)

1. Masukkan:
   - Gaji bruto bulanan
   - Status PTKP
2. Sistem menghitung:
   - Kategori TER (A / B / C)
   - Tarif TER berdasarkan bracket penghasilan
   - PPh 21 bulanan (Bruto × Tarif TER)

### Referensi

- PMK 168/2023 — Tarif Efektif Rata-rata PPh 21
- PP 58/2023 — Rekonsiliasi tahunan (tarif progresif)
- PTKP per status (lihat [Perpajakan](04-perpajakan.md))

---

## Kapan Menggunakan Fitur Payroll?

### Gunakan Fitur Payroll Untuk:

✅ Gaji karyawan tetap/kontrak dengan kewajiban BPJS
✅ Perusahaan wajib potong PPh 21
✅ Butuh bukti potong 1721-A1 untuk karyawan
✅ Karyawan perlu akses slip gaji online
✅ Tracking komponen gaji detail (tunjangan, potongan)

### Gunakan Transaksi "Bayar Beban Gaji" Untuk:

✅ Bayar kontraktor lepas (tanpa BPJS/PPh 21)
✅ Bonus di luar payroll reguler
✅ Penarikan dana pemilik (bukan gaji karyawan)
✅ Pembayaran ad-hoc yang tidak perlu slip gaji

### Perbedaan Akuntansi

| Aspek | Fitur Payroll | Transaksi Simple |
|-------|---------------|------------------|
| **Jurnal** | 5 baris (pisah hutang) | 2 baris (langsung bayar) |
| **Beban** | Gaji bruto + BPJS perusahaan | Nominal transfer saja |
| **Kewajiban** | Hutang Gaji, BPJS, PPh 21 | Tidak ada hutang |
| **Kalkulasi** | Otomatis BPJS + PPh 21 | Manual |
| **Tax compliance** | Otomatis hitung & lapor | Tanggung jawab manual |
| **Slip gaji** | Generate otomatis | Tidak ada |
| **Bukti potong** | Generate 1721-A1 | Tidak ada |

### Contoh Jurnal: Payroll vs Simple

**Fitur Payroll** (otomatis):
```
Dr. Beban Gaji              30.000.000
Dr. Beban BPJS Perusahaan    3.432.000
    Cr. Hutang Gaji             26.250.000
    Cr. Hutang BPJS              6.432.000
    Cr. Hutang PPh 21              750.000
```

**Transaksi Simple**:
```
Dr. Beban Gaji              10.000.000
    Cr. Bank                    10.000.000
```

**Kesimpulan:** Gunakan fitur payroll untuk karyawan tetap. Gunakan transaksi simple hanya untuk pembayaran ad-hoc yang tidak perlu tracking BPJS/PPh 21.

---

## Proses Penggajian

### Melihat Daftar Payroll

Buka menu **Penggajian** > **Payroll**.

![Daftar Payroll](screenshots/payroll-list.png)

### Membuat Payroll Baru

1. Klik **Payroll Baru**

![Form Payroll](screenshots/payroll-form.png)

2. Isi:
   - Periode (bulan/tahun)
   - Tanggal pembayaran
3. Klik **Buat**
4. Sistem generate slip gaji untuk semua karyawan aktif

### Workflow Payroll

```
DRAFT → CALCULATED → APPROVED → POSTED
```

| Status | Aksi |
|--------|------|
| DRAFT | Edit komponen individual |
| CALCULATED | Review perhitungan |
| APPROVED | Siap bayar |
| POSTED | Jurnal gaji dibuat |

### Melihat Detail Payroll

Klik payroll untuk melihat detail:

![Detail Payroll](screenshots/payroll-detail.png)

**Informasi per karyawan:**
- Gaji pokok
- Tunjangan (jabatan, kehadiran, makan, transport)
- Total pendapatan (bruto)
- BPJS Karyawan
- PPh 21
- Total potongan
- Gaji bersih (take home pay)

### Posting Payroll

1. Pastikan status APPROVED
2. Klik **Posting**
3. Sistem membuat jurnal:
   ```
   Dr. Beban Gaji              xxx
   Dr. Beban BPJS Perusahaan   xxx
       Cr. Hutang Gaji             xxx
       Cr. Hutang BPJS             xxx
       Cr. Hutang PPh 21           xxx
   ```

### Membayar Gaji

Setelah transfer ke rekening karyawan:

1. Buka menu **Transaksi** > **Transaksi Baru**
2. Pilih template **Bayar Gaji**
3. Isi jumlah total gaji bersih
4. Posting

Jurnal:
```
Dr. Hutang Gaji             xxx
    Cr. Bank                    xxx
```

---

## Pembayaran Kewajiban Payroll

Setelah posting payroll, ada 3 kewajiban yang harus dibayar:

### 1. Bayar Gaji ke Karyawan

**Kapan:** 1-5 hari kerja setelah akhir bulan
**Template:** Bayar Hutang Gaji

1. Buka menu **Transaksi** > **Transaksi Baru**
2. Pilih template **Bayar Hutang Gaji**
3. Isi:
   - Tanggal pembayaran
   - Jumlah: Total gaji bersih (dari payroll detail)
   - Deskripsi: "Transfer gaji [bulan] [tahun]"
   - Referensi: Nomor transaksi bank
4. Pilih akun bank
5. Klik **Simpan & Posting**

Jurnal:
```
Dr. Hutang Gaji             xxx
    Cr. Bank                    xxx
```

### 2. Bayar BPJS ke Institusi

**Kapan:** Maksimal tanggal 10 bulan berikutnya
**Template:** Bayar Hutang BPJS

1. Buka menu **Transaksi** > **Transaksi Baru**
2. Pilih template **Bayar Hutang BPJS**
3. Isi:
   - Tanggal pembayaran (maks tgl 10)
   - Jumlah: Total BPJS (perusahaan + karyawan)
   - Deskripsi: "Pembayaran BPJS [bulan] [tahun]"
   - Referensi: Nomor billing BPJS
4. Pilih akun bank
5. Klik **Simpan & Posting**

Jurnal:
```
Dr. Hutang BPJS             xxx
    Cr. Bank                    xxx
```

**Catatan:** Bayar ke BPJS Kesehatan dan BPJS Ketenagakerjaan melalui e-DABU atau virtual account.

### 3. Setor PPh 21 ke Kas Negara

**Kapan:** Maksimal tanggal 10 bulan berikutnya
**Template:** Setor PPh 21

1. Buka menu **Transaksi** > **Transaksi Baru**
2. Pilih template **Setor PPh 21**
3. Isi:
   - Tanggal penyetoran (maks tgl 10)
   - Jumlah: Total PPh 21 yang dipotong
   - Deskripsi: "Penyetoran PPh 21 [bulan] [tahun]"
   - Referensi: Nomor bukti setor (BPN)
4. Pilih akun bank
5. Klik **Simpan & Posting**

Jurnal:
```
Dr. Hutang PPh 21           xxx
    Cr. Bank                    xxx
```

**Catatan:** Bayar melalui e-Billing DJP, lalu lapor SPT Masa PPh 21 maks tanggal 20.

### Timeline Kewajiban Payroll

| Tanggal | Aktivitas | Template | Deadline |
|---------|-----------|----------|----------|
| 25-31 | Posting payroll (jurnal dibuat) | Post Gaji Bulanan | Akhir bulan |
| 1-5 | Transfer gaji ke rekening karyawan | Bayar Hutang Gaji | - |
| Maks 10 | Bayar BPJS ke institusi | Bayar Hutang BPJS | **Wajib** |
| Maks 10 | Setor PPh 21 ke kas negara | Setor PPh 21 | **Wajib** |
| Maks 20 | Lapor SPT Masa PPh 21 | (Eksternal DJP) | **Wajib** |

---

## Layanan Mandiri Karyawan

### Fitur Self-Service

Karyawan dengan role EMPLOYEE dapat mengakses:

**Slip Gaji:**

![Self Service Payslips](screenshots/self-service-payslips.png)

**Bukti Potong PPh 21:**

![Self Service Bukti Potong](screenshots/self-service-bukti-potong.png)

**Profil:**

![Self Service Profile](screenshots/self-service-profile.png)

### Mengaktifkan Self-Service

1. Buat user untuk karyawan dengan role EMPLOYEE
2. Link user ke data karyawan
3. Karyawan login dengan kredensialnya

---

## Bukti Potong PPh 21

### Generate Bukti Potong Tahunan (1721-A1)

1. Buka menu **Penggajian** > **Bukti Potong**
2. Pilih tahun pajak
3. Pilih karyawan (atau semua)
4. Klik **Generate**
5. Download PDF

### Isi Bukti Potong

- Identitas pemotong (perusahaan)
- Identitas penerima (karyawan)
- Rincian penghasilan bruto
- BPJS dan biaya jabatan
- Penghasilan neto
- PTKP
- PKP
- PPh 21 terutang
- PPh 21 dipotong

---

## Contoh Lengkap: Proses Payroll Januari 2025

### Skenario

Perusahaan IT memiliki 3 karyawan:
- **Budi Santoso** - Developer, gaji Rp 10.000.000
- **Dewi Lestari** - Designer, gaji Rp 10.000.000
- **Agus Wijaya** - Project Manager, gaji Rp 10.000.000

### Data Payroll

| Item | Rumus | Jumlah |
|------|-------|--------|
| **Gaji Bruto** | 3 × Rp 10.000.000 | Rp 30.000.000 |
| **BPJS Perusahaan** | Kes 4% + TK (JHT 3.7%, JKK 0.24%, JKM 0.3%, JP 2%) | Rp 3.432.000 |
| **BPJS Karyawan** | Kes 1% + JHT 2% + JP 1% | Rp 3.000.000 |
| **PPh 21** | Progresif setelah PTKP | Rp 750.000 |
| **Gaji Bersih** | Bruto - BPJS Karyawan - PPh 21 | Rp 26.250.000 |

### Langkah 1: Posting Payroll (31 Januari 2025)

1. Buka **Penggajian** > **Payroll**
2. Klik payroll bulan Januari
3. Pastikan status APPROVED
4. Klik **Posting**

**Jurnal Otomatis:**
```
Dr. Beban Gaji                   30.000.000
Dr. Beban BPJS Perusahaan         3.432.000
    Cr. Hutang Gaji                  26.250.000
    Cr. Hutang BPJS                   6.432.000 (perusahaan + karyawan)
    Cr. Hutang PPh 21                   750.000
```

**Penjelasan:**
- **Beban total perusahaan:** Rp 33.432.000 (masuk P&L bulan Januari)
- **Kewajiban total:** Rp 33.432.000 (hutang di neraca)
- **Uang belum keluar** dari bank (masih status hutang)

### Langkah 2: Bayar Gaji (1 Februari 2025)

1. Buka **Transaksi** > **Transaksi Baru**
2. Pilih template **Bayar Hutang Gaji**
3. Isi:
   - Jumlah: Rp 26.250.000
   - Deskripsi: "Transfer gaji Januari 2025"
   - Pilih Bank BCA
4. Klik **Simpan & Posting**

**Jurnal:**
```
Dr. Hutang Gaji                  26.250.000
    Cr. Bank BCA                     26.250.000
```

**Efek:**
- ✅ Hutang Gaji lunas
- ✅ Karyawan terima transfer
- ⏳ Hutang BPJS masih Rp 6.432.000
- ⏳ Hutang PPh 21 masih Rp 750.000

### Langkah 3: Bayar BPJS (10 Februari 2025)

1. Buka **Transaksi** > **Transaksi Baru**
2. Pilih template **Bayar Hutang BPJS**
3. Isi:
   - Jumlah: Rp 6.432.000
   - Deskripsi: "Pembayaran BPJS Januari 2025"
   - Referensi: Nomor billing BPJS
   - Pilih Bank BCA
4. Klik **Simpan & Posting**

**Jurnal:**
```
Dr. Hutang BPJS                   6.432.000
    Cr. Bank BCA                      6.432.000
```

**Efek:**
- ✅ Hutang BPJS lunas
- ✅ Kewajiban BPJS selesai
- ⏳ Hutang PPh 21 masih Rp 750.000

### Langkah 4: Setor PPh 21 (10 Februari 2025)

1. Buka **Transaksi** > **Transaksi Baru**
2. Pilih template **Setor PPh 21**
3. Isi:
   - Jumlah: Rp 750.000
   - Deskripsi: "Penyetoran PPh 21 Januari 2025"
   - Referensi: Nomor BPN dari e-Billing
   - Pilih Bank BCA
4. Klik **Simpan & Posting**

**Jurnal:**
```
Dr. Hutang PPh 21                   750.000
    Cr. Bank BCA                        750.000
```

**Efek:**
- ✅ Hutang PPh 21 lunas
- ✅ Semua kewajiban payroll selesai

### Ringkasan Kas Keluar

| Tanggal | Item | Bank BCA | Status |
|---------|------|----------|--------|
| 1 Feb | Transfer gaji | (Rp 26.250.000) | ✅ |
| 10 Feb | Bayar BPJS | (Rp 6.432.000) | ✅ |
| 10 Feb | Setor PPh 21 | (Rp 750.000) | ✅ |
| **Total** | **Kas Keluar** | **(Rp 33.432.000)** | - |

**Validasi:** Total kas keluar = Total beban payroll ✅ (akuntansi akrual benar!)

### Langkah 5: Lapor SPT Masa PPh 21 (20 Februari 2025)

1. Login ke **DJP Online** (pajak.go.id)
2. Pilih **e-Filing** > **SPT Masa PPh 21**
3. Input data dari aplikasi:
   - Jumlah pegawai: 3 orang
   - PPh 21 dipotong: Rp 750.000
   - PPh 21 disetor: Rp 750.000 (lihat dari jurnal Setor PPh 21)
   - Nomor BPN: Dari referensi transaksi
4. Submit SPT

**Catatan:** Aplikasi belum generate SPT otomatis, input manual ke DJP Online.

---

## Penggajian via API

REST API tersedia untuk integrasi penggajian dengan sistem eksternal.

### Komponen Gaji

```
GET    /api/salary-components           — daftar komponen aktif
POST   /api/salary-components           — buat komponen baru
PUT    /api/salary-components/{id}       — update komponen
DELETE /api/salary-components/{id}       — nonaktifkan komponen
```

### Karyawan

```
GET    /api/employees                    — daftar karyawan (filter: active, status)
POST   /api/employees                    — buat karyawan
GET    /api/employees/{id}               — detail dengan komponen gaji
PUT    /api/employees/{id}               — update data karyawan
POST   /api/employees/{id}/salary-components    — assign komponen gaji (endDate opsional)
PUT    /api/employees/{id}/salary-components/{componentId}  — update assignment
```

### Payroll Run

```
GET    /api/payroll                      — daftar payroll (filter: year, status)
POST   /api/payroll                      — buat payroll baru (DRAFT)
GET    /api/payroll/{id}                 — detail dengan semua detail karyawan
POST   /api/payroll/{id}/calculate       — hitung PPh 21 (TER Jan-Nov / rekonsiliasi Des), set CALCULATED
POST   /api/payroll/{id}/approve         — set APPROVED
POST   /api/payroll/{id}/post            — posting ke jurnal
DELETE /api/payroll/{id}                 — hapus (hanya DRAFT)
```

### 1721-A1 dan Ringkasan PPh 21

```
GET /api/payroll/employees/{id}/1721-a1?year=2025  — data 1721-A1 per karyawan
GET /api/payroll/pph21/summary?year=2025           — ringkasan PPh 21 seluruh karyawan
```

Response 1721-A1 berisi:
- Data karyawan (NPWP, NIK, PTKP, masa kerja)
- Perhitungan (penghasilan bruto, biaya jabatan, neto, PTKP, PKP, PPh 21 terutang)
- Breakdown bulanan (gross salary dan PPh 21 per bulan)

### Skenario: Retrofit Data Payroll 2025

1. Buat komponen gaji: `POST /api/salary-components` (GAJI_POKOK, EARNING, isTaxable=true)
2. Buat karyawan: `POST /api/employees` (nama, NPWP, NIK, PTKP, hireDate)
3. Assign komponen dengan dua periode:
   - `POST /api/employees/{id}/salary-components` (amount=11253000, effectiveDate=2025-01-01, endDate=2025-04-30)
   - `POST /api/employees/{id}/salary-components` (amount=5000000, effectiveDate=2025-05-01)
4. Buat 12 payroll run: `POST /api/payroll` (periode 2025-01 s/d 2025-12)
5. Hitung masing-masing: `POST /api/payroll/{id}/calculate`
6. Generate 1721-A1: `GET /api/payroll/employees/{id}/1721-a1?year=2025`

Autentikasi: Bearer token dengan scope `tax-export:read`.

---

## Tips Penggajian

1. **Setup komponen dulu** - Sebelum input karyawan
2. **Verifikasi BPJS** - Pastikan nomor BPJS valid
3. **Review sebelum posting** - Cek perhitungan PPh 21
4. **Backup sebelum posting** - Jurnal tidak bisa di-reverse
5. **Arsip bukti potong** - Simpan PDF untuk audit

---

## Lihat Juga

- [Perpajakan](04-perpajakan.md) - Tarif PPh 21, PTKP
- [Tutorial Akuntansi](02-tutorial-akuntansi.md) - Jurnal gaji
- [Keamanan](11-keamanan-kepatuhan.md) - Enkripsi data karyawan
