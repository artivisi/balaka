# Industri Pendidikan

Panduan lengkap untuk institusi pendidikan (Sekolah Tinggi, Universitas, Akademi).

## Karakteristik Industri Pendidikan

### Ciri Khas

- **Revenue berbasis semester** - Pendapatan SPP, Uang Pangkal, Biaya Praktikum
- **Accounts Receivable Management** - Piutang mahasiswa dengan cicilan
- **Scholarship & Discounts** - Beasiswa prestasi dan kurang mampu
- **Program-based Revenue** - Laporan pendapatan per program studi
- **Academic Calendar** - Periode fiskal mengikuti tahun ajaran (Juli-Juni)
- **Payroll Kompleks** - Gaji dosen tetap, honorarium dosen tidak tetap, gaji karyawan

### Alur Bisnis Tipikal

```
Pendaftaran → Tagihan SPP → Pembayaran (Lunas/Cicilan) → Beasiswa → Laporan Piutang
```

**Contoh Kasus: STMIK Tazkia**

Sekolah Tinggi Manajemen Informatika & Komputer dengan:
- 3 Program studi: Teknik Informatika (S1), Sistem Informasi (S1), Manajemen Informatika (D3)
- Pendapatan: SPP, Uang Pangkal, Biaya Praktikum, Wisuda
- Beasiswa: Prestasi (50%), Tidak Mampu (75%)
- Tahun ajaran: Juli-Juni

---

## Chart of Accounts Khusus Pendidikan

### Melihat Daftar Akun

Buka menu **Akuntansi** > **Bagan Akun**.

![Daftar Akun Pendidikan](screenshots/campus/accounts-list.png)

### Akun Piutang Mahasiswa

| Kode | Nama Akun | Normal Balance |
|------|-----------|----------------|
| 1.1.10 | Piutang SPP Mahasiswa | Debit |
| 1.1.11 | Piutang Uang Pangkal | Debit |
| 1.1.12 | Piutang Biaya Praktikum | Debit |
| 1.1.13 | Piutang Wisuda | Debit |

### Akun Pendapatan

| Kode | Nama Akun | Normal Balance |
|------|-----------|----------------|
| 4.1.01 | Pendapatan SPP | Kredit |
| 4.1.02 | Pendapatan Uang Pangkal | Kredit |
| 4.1.03 | Pendapatan Biaya Praktikum | Kredit |
| 4.1.04 | Pendapatan Wisuda | Kredit |
| 4.1.05 | Pendapatan Ujian | Kredit |
| 4.1.06 | Pendapatan Sertifikasi | Kredit |

### Akun Beban Akademik

| Kode | Nama Akun | Normal Balance |
|------|-----------|----------------|
| 5.1.01 | Beban Gaji Dosen Tetap | Debit |
| 5.1.02 | Beban Honorarium Dosen Tidak Tetap | Debit |
| 5.1.10 | Beban Bahan Praktikum | Debit |
| 5.1.11 | Beban Pemeliharaan Lab | Debit |

### Akun Beasiswa

| Kode | Nama Akun | Normal Balance |
|------|-----------|----------------|
| 5.3.01 | Beban Beasiswa Prestasi | Debit |
| 5.3.02 | Beban Beasiswa Tidak Mampu | Debit |

![Akun Beasiswa](screenshots/campus/scholarship-accounts.png)

---

## Template Transaksi Pendidikan

### Melihat Template

Buka menu **Pengaturan** > **Template**.

![Daftar Template Pendidikan](screenshots/campus/billing-templates.png)

### Template Standar

| Template | Kategori | Fungsi |
|----------|----------|--------|
| Tagihan SPP Mahasiswa | INCOME | Membuat piutang SPP per semester |
| Pembayaran SPP | RECEIPT | Mencatat pembayaran SPP (lunas/cicilan) |
| Tagihan Uang Pangkal | INCOME | Membuat piutang uang pangkal mahasiswa baru |
| Pembayaran Uang Pangkal | RECEIPT | Mencatat pembayaran uang pangkal |
| Tagihan Biaya Praktikum | INCOME | Membuat piutang biaya praktikum |
| Pembayaran Biaya Praktikum | RECEIPT | Mencatat pembayaran biaya praktikum |
| Tagihan Wisuda | INCOME | Membuat piutang biaya wisuda |
| Pembayaran Wisuda | RECEIPT | Mencatat pembayaran wisuda |
| Beasiswa Prestasi | EXPENSE | Memberikan beasiswa (mengurangi piutang) |
| Beasiswa Tidak Mampu | EXPENSE | Memberikan beasiswa (mengurangi piutang) |

### Template Gaji & Operasional

| Template | Kategori | Fungsi |
|----------|----------|--------|
| Gaji Dosen Tetap | EXPENSE | Pembayaran gaji bulanan dosen tetap |
| Honorarium Dosen Tidak Tetap | EXPENSE | Honorarium per SKS dosen tidak tetap |
| Gaji Karyawan | EXPENSE | Pembayaran gaji karyawan administrasi |
| Bayar BPJS Dosen | EXPENSE | BPJS Kesehatan & Ketenagakerjaan dosen |
| Bayar BPJS Karyawan | EXPENSE | BPJS Kesehatan & Ketenagakerjaan karyawan |
| Setor PPh 21 | PAYMENT | Penyetoran PPh 21 ke kas negara |

![Template Pembayaran](screenshots/campus/payment-templates.png)

---

## Transaksi Harian: Contoh Praktis

### Transaksi 1: Tagihan SPP Semester Ganjil

**Konteks:** Ahmad Fauzi (NIM 2201010001) mahasiswa Teknik Informatika semester 3, tagihan SPP semester ganjil 2024/2025.

**Data Mahasiswa:**
- **NIM:** 2201010001
- **Nama:** Ahmad Fauzi
- **Program Studi:** Teknik Informatika (S1)
- **Semester:** 3
- **SPP per semester:** Rp 7.500.000

**Langkah:**

Menu **Transaksi** > **Buat Transaksi** > Pilih template **Tagihan SPP Mahasiswa**

**Isi Data:**
- **Tanggal:** 1 Juli 2024
- **Jumlah:** Rp 7.500.000
- **Keterangan:** Tagihan SPP Semester Ganjil 2024/2025 - Ahmad Fauzi (2201010001)
- **Referensi:** INV-SPP-2024-001

**Jurnal Otomatis:**
```
Dr. 1.1.10 Piutang SPP Mahasiswa    Rp 7.500.000
    Cr. 4.1.01 Pendapatan SPP            Rp 7.500.000
```

**Impact:**
- Piutang SPP mahasiswa: +Rp 7.500.000
- Pendapatan SPP diakui: +Rp 7.500.000
- Outstanding receivable untuk Ahmad Fauzi: Rp 7.500.000

---

### Transaksi 2: Pembayaran SPP (Lunas)

**Konteks:** Ahmad Fauzi membayar lunas SPP semester ganjil via transfer bank.

**Langkah:**

Menu **Transaksi** > **Buat Transaksi** > Pilih template **Pembayaran SPP**

**Isi Data:**
- **Tanggal:** 5 Juli 2024
- **Jumlah:** Rp 7.500.000
- **Rekening:** Bank BCA
- **Keterangan:** Pembayaran SPP Lunas - Ahmad Fauzi (2201010001)
- **Referensi:** PMT-SPP-2024-001

**Jurnal Otomatis:**
```
Dr. 1.1.02 Bank BCA                 Rp 7.500.000
    Cr. 1.1.10 Piutang SPP Mahasiswa    Rp 7.500.000
```

**Impact:**
- Kas bertambah: +Rp 7.500.000
- Piutang SPP berkurang: -Rp 7.500.000
- Outstanding receivable Ahmad Fauzi: Rp 0 (LUNAS)

---

### Transaksi 3: Tagihan Uang Pangkal Mahasiswa Baru

**Konteks:** Siti Aminah (NIM 2401010001) mahasiswa baru Sistem Informasi, tagihan uang pangkal.

**Data Mahasiswa:**
- **NIM:** 2401010001
- **Nama:** Siti Aminah
- **Program Studi:** Sistem Informasi (S1)
- **Semester:** 1 (Mahasiswa Baru)
- **Uang Pangkal:** Rp 5.000.000

**Langkah:**

Menu **Transaksi** > **Buat Transaksi** > Pilih template **Tagihan Uang Pangkal**

**Isi Data:**
- **Tanggal:** 10 Juli 2024
- **Jumlah:** Rp 5.000.000
- **Keterangan:** Tagihan Uang Pangkal - Siti Aminah (2401010001) - Sistem Informasi
- **Referensi:** INV-UP-2024-001

**Jurnal Otomatis:**
```
Dr. 1.1.11 Piutang Uang Pangkal     Rp 5.000.000
    Cr. 4.1.02 Pendapatan Uang Pangkal   Rp 5.000.000
```

**Impact:**
- Piutang Uang Pangkal: +Rp 5.000.000
- Pendapatan Uang Pangkal: +Rp 5.000.000
- Outstanding receivable Siti Aminah: Rp 5.000.000

---

### Transaksi 4: Pembayaran Uang Pangkal (Cicilan 1)

**Konteks:** Siti Aminah membayar cicilan pertama Rp 2.500.000 (50% dari total).

**Langkah:**

Menu **Transaksi** > **Buat Transaksi** > Pilih template **Pembayaran Uang Pangkal**

**Isi Data:**
- **Tanggal:** 15 Juli 2024
- **Jumlah:** Rp 2.500.000
- **Rekening:** Bank Mandiri
- **Keterangan:** Pembayaran UP Cicilan 1/2 - Siti Aminah (2401010001)
- **Referensi:** PMT-UP-2024-001-1

**Jurnal Otomatis:**
```
Dr. 1.1.03 Bank Mandiri             Rp 2.500.000
    Cr. 1.1.11 Piutang Uang Pangkal     Rp 2.500.000
```

**Impact:**
- Kas bertambah: +Rp 2.500.000
- Piutang berkurang: -Rp 2.500.000
- Outstanding receivable Siti Aminah: Rp 2.500.000 (masih kurang)

---

### Transaksi 5: Beasiswa Prestasi

**Konteks:** Budi Hartono (NIM 2201020001) mahasiswa MI semester 5 mendapat beasiswa prestasi 50% karena IPK 3.8.

**Data:**
- **NIM:** 2201020001
- **Nama:** Budi Hartono
- **Program:** Manajemen Informatika (D3)
- **Tagihan SPP:** Rp 6.000.000
- **Beasiswa:** 50% = Rp 3.000.000

**Langkah 1: Buat Tagihan SPP**

Template **Tagihan SPP Mahasiswa**
- Jumlah: Rp 6.000.000
- Keterangan: Tagihan SPP Semester Ganjil - Budi Hartono (2201020001)

**Langkah 2: Berikan Beasiswa**

Menu **Transaksi** > **Buat Transaksi** > Pilih template **Beasiswa Prestasi**

**Isi Data:**
- **Tanggal:** 1 Juli 2024
- **Jumlah:** Rp 3.000.000
- **Keterangan:** Beasiswa Prestasi 50% IPK 3.8 - Budi Hartono (2201020001)
- **Referensi:** BEASISWA-PRESTASI-001

**Jurnal Otomatis:**
```
Dr. 5.3.01 Beban Beasiswa Prestasi  Rp 3.000.000
    Cr. 1.1.10 Piutang SPP Mahasiswa    Rp 3.000.000
```

**Impact:**
- Beban beasiswa: +Rp 3.000.000
- Piutang SPP berkurang: -Rp 3.000.000
- Outstanding receivable Budi: Rp 3.000.000 (setelah beasiswa)

![Template Beasiswa](screenshots/campus/scholarship-templates.png)

---

### Transaksi 6: Beasiswa Tidak Mampu

**Konteks:** Agus Wijaya (NIM 2301010003) mahasiswa SI semester 3 mendapat beasiswa kurang mampu 75%.

**Data:**
- **NIM:** 2301010003
- **Nama:** Agus Wijaya
- **Program:** Sistem Informasi (S1)
- **Tagihan SPP:** Rp 7.500.000
- **Beasiswa:** 75% = Rp 5.625.000

**Langkah 1: Buat Tagihan SPP**

Template **Tagihan SPP Mahasiswa**
- Jumlah: Rp 7.500.000
- Keterangan: Tagihan SPP Semester Ganjil - Agus Wijaya (2301010003)

**Langkah 2: Berikan Beasiswa**

Menu **Transaksi** > **Buat Transaksi** > Pilih template **Beasiswa Tidak Mampu**

**Isi Data:**
- **Tanggal:** 1 Juli 2024
- **Jumlah:** Rp 5.625.000
- **Keterangan:** Beasiswa Tidak Mampu 75% - Agus Wijaya (2301010003)
- **Referensi:** BEASISWA-TM-001

**Jurnal Otomatis:**
```
Dr. 5.3.02 Beban Beasiswa Tidak Mampu  Rp 5.625.000
    Cr. 1.1.10 Piutang SPP Mahasiswa       Rp 5.625.000
```

**Impact:**
- Beban beasiswa: +Rp 5.625.000
- Piutang SPP berkurang: -Rp 5.625.000
- Outstanding receivable Agus: Rp 1.875.000 (setelah beasiswa 75%)

---

### Transaksi 7: Gaji Dosen Tetap

**Konteks:** Pembayaran gaji bulanan Agustus 2024 untuk Dr. Siti Nurjanah M.Kom (dosen tetap TI).

**Data Dosen:**
- **NIK:** EMP-D001
- **Nama:** Dr. Siti Nurjanah M.Kom
- **Jabatan:** Dosen Tetap Teknik Informatika
- **Gaji Pokok:** Rp 8.500.000
- **Tunjangan Fungsional:** Rp 2.000.000
- **Total Gaji:** Rp 10.500.000

**Langkah:**

Menu **Transaksi** > **Buat Transaksi** > Pilih template **Gaji Dosen Tetap**

**Isi Data:**
- **Tanggal:** 25 Agustus 2024
- **Jumlah:** Rp 10.500.000
- **Rekening:** Bank BCA
- **Keterangan:** Gaji Agustus 2024 - Dr. Siti Nurjanah M.Kom (EMP-D001)
- **Referensi:** PAYROLL-DSN-202408-001

**Jurnal Otomatis:**
```
Dr. 5.1.01 Beban Gaji Dosen Tetap   Rp 10.500.000
    Cr. 1.1.02 Bank BCA                  Rp 10.500.000
```

**Catatan:** Untuk penggajian lengkap dengan BPJS dan PPh 21, lihat [Bab 5: Penggajian](05-penggajian.md).

---

### Transaksi 8: Honorarium Dosen Tidak Tetap

**Konteks:** Pembayaran honorarium untuk dosen tidak tetap yang mengajar 4 SKS di bulan Agustus.

**Data:**
- **Nama:** Ir. Hendra Saputra M.T
- **Mata Kuliah:** Algoritma & Pemrograman (4 SKS)
- **Honorarium:** Rp 150.000/SKS
- **Total:** 4 SKS × Rp 150.000 = Rp 600.000

**Langkah:**

Menu **Transaksi** > **Buat Transaksi** > Pilih template **Honorarium Dosen Tidak Tetap**

**Isi Data:**
- **Tanggal:** 30 Agustus 2024
- **Jumlah:** Rp 600.000
- **Rekening:** Bank BCA
- **Keterangan:** Honorarium 4 SKS Agustus 2024 - Ir. Hendra Saputra M.T
- **Referensi:** HON-DSN-202408-001

**Jurnal Otomatis:**
```
Dr. 5.1.02 Beban Honorarium Dosen Tidak Tetap  Rp 600.000
    Cr. 1.1.02 Bank BCA                             Rp 600.000
```

### Daftar Transaksi

Semua transaksi yang telah dibuat dapat dilihat di menu **Akuntansi** > **Transaksi**.

![Daftar Transaksi](screenshots/campus/transaction-list.png)

---

## Laporan Keuangan Khusus Pendidikan

### Laporan Laba Rugi

Buka menu **Laporan** > **Laporan Laba Rugi**.

![Laporan Laba Rugi](screenshots/campus/report-income-statement.png)

**Pendapatan Operasional:**
- Pendapatan SPP
- Pendapatan Uang Pangkal
- Pendapatan Biaya Praktikum
- Pendapatan Wisuda

**Beban Akademik:**
- Beban Gaji Dosen Tetap
- Beban Honorarium Dosen Tidak Tetap
- Beban Bahan Praktikum
- Beban Pemeliharaan Lab

**Beban Kemahasiswaan:**
- Beban Beasiswa Prestasi
- Beban Beasiswa Tidak Mampu

![Laporan Beban Beasiswa](screenshots/campus/report-scholarship-expenses.png)

---

### Neraca (Balance Sheet)

Buka menu **Laporan** > **Neraca**.

![Neraca](screenshots/campus/report-balance-sheet.png)

**Aset Lancar - Piutang:**
- Piutang SPP Mahasiswa
- Piutang Uang Pangkal
- Piutang Biaya Praktikum
- Piutang Wisuda

![Laporan Piutang Mahasiswa](screenshots/campus/report-receivables.png)

**Interpretasi:**
- **Piutang SPP tinggi** → Banyak mahasiswa belum bayar/cicilan
- **Piutang Uang Pangkal tinggi** → Mahasiswa baru yang belum lunas
- **Aging Analysis** → Perlu strategi penagihan untuk piutang > 90 hari

---

### Laporan Pendapatan per Program Studi

**Konsep:**

Laporan ini memecah pendapatan berdasarkan program studi untuk analisis profitabilitas.

**Cara Manual (via Keterangan Transaksi):**

Pastikan setiap transaksi pendapatan mencantumkan kode program di keterangan:
- `[TI]` untuk Teknik Informatika
- `[SI]` untuk Sistem Informasi
- `[MI]` untuk Manajemen Informatika

**Contoh:**
```
Tagihan SPP Semester Ganjil 2024/2025 - Ahmad Fauzi (2201010001) - [TI]
```

**Export & Analisis:**
1. Buka **Laporan** > **Buku Besar** > Akun 4.1.01 (Pendapatan SPP)
2. Filter periode (1 semester)
3. Export ke Excel
4. Pivot table dengan kolom:
   - Program (extract dari keterangan)
   - Total Pendapatan
   - Jumlah Mahasiswa
   - Rata-rata per Mahasiswa

**Hasil Analisis Contoh:**

| Program | Total Pendapatan SPP | Jumlah Mahasiswa | Avg per Mhs |
|---------|---------------------|------------------|-------------|
| Teknik Informatika (S1) | Rp 375.000.000 | 50 | Rp 7.500.000 |
| Sistem Informasi (S1) | Rp 300.000.000 | 40 | Rp 7.500.000 |
| Manajemen Informatika (D3) | Rp 180.000.000 | 30 | Rp 6.000.000 |
| **Total** | **Rp 855.000.000** | **120** | **Rp 7.125.000** |

![Laporan Pendapatan Program](screenshots/campus/report-revenue.png)

---

## Laporan Piutang Mahasiswa

### Receivables Aging Report

**Konsep:**

Analisis umur piutang untuk identifikasi mahasiswa yang perlu ditagih.

**Kategori:**
- **Current (0-30 hari):** Baru jatuh tempo
- **30-60 hari:** Perlu reminder
- **60-90 hari:** Perlu teguran
- **> 90 hari:** Perlu tindakan tegas (surat peringatan, blokir KRS)

**Cara Manual:**

1. Buka **Laporan** > **Buku Besar** > Akun 1.1.10 (Piutang SPP)
2. Export transaksi
3. Analisis di Excel:
   - Hitung selisih hari dari tanggal tagihan
   - Group berdasarkan aging bucket
   - Sort by amount descending

**Contoh Hasil:**

| NIM | Nama | Jumlah Piutang | Tgl Tagihan | Umur (Hari) | Status |
|-----|------|----------------|-------------|-------------|--------|
| 2201010005 | Rina Wati | Rp 7.500.000 | 01-Jul-24 | 95 | Overdue |
| 2301010007 | Hendra Saputra | Rp 3.750.000 | 15-Jul-24 | 80 | Overdue |
| 2401010002 | Dewi Lestari | Rp 7.500.000 | 01-Aug-24 | 35 | Current |

---

## Best Practices: Akuntansi Pendidikan

### 1. Revenue Recognition

**Prinsip:**
- Akui pendapatan **saat tagihan dibuat**, bukan saat kas diterima (accrual basis)
- Tagihan SPP = Piutang + Pendapatan diakui

**Contoh:**
```
1 Juli: Tagihan SPP Rp 100 juta (100 mahasiswa)
→ Dr. Piutang SPP Rp 100 juta
→ Cr. Pendapatan SPP Rp 100 juta

Agustus: Terima pembayaran Rp 80 juta
→ Dr. Kas Rp 80 juta
→ Cr. Piutang SPP Rp 80 juta

Pendapatan tetap Rp 100 juta (sudah diakui Juli)
```

### 2. Scholarship Treatment

**Beasiswa = Expense, bukan Potongan Pendapatan**

✅ **Correct:**
```
Tagihan: Piutang Rp 7.500.000 | Pendapatan Rp 7.500.000
Beasiswa: Beban Rp 3.750.000 | Piutang Rp 3.750.000

Result:
- Pendapatan Rp 7.500.000 (full recognition)
- Beban Beasiswa Rp 3.750.000
- Net Revenue Rp 3.750.000
```

❌ **Incorrect:**
```
Tagihan setelah beasiswa: Piutang Rp 3.750.000 | Pendapatan Rp 3.750.000

Result:
- Understatement pendapatan
- Tidak ada transparansi beasiswa
```

### 3. Receivables Management

**Strategi Penagihan:**

1. **Week 1:** Email reminder otomatis
2. **Week 3:** SMS reminder
3. **Week 5:** Panggilan telepon
4. **Week 7:** Surat peringatan resmi
5. **Week 9:** Blokir akses sistem (KRS, nilai)

**Cicilan:**
- Minimal 25% di awal semester
- Maksimal 4 cicilan per semester
- Harus lunas sebelum UAS

### 4. Fiscal Period

**Tahun Ajaran ≠ Tahun Kalender**

Institusi pendidikan menggunakan tahun ajaran (Juli-Juni):
```
Semester Ganjil: Juli - Desember
Semester Genap: Januari - Juni
```

Atur periode fiskal di aplikasi:
- **Fiscal Year Start Month:** 7 (Juli)
- Tutup buku per 30 Juni

### 5. Tax Compliance

**Institusi Pendidikan & Pajak:**

- **PPN:** Umumnya tidak kena PPN (jasa pendidikan dikecualikan)
- **PPh 21:** Gaji dosen & karyawan dipotong PPh 21
- **PPh 23:** Honorarium narasumber/konsultan dipotong 2%

Lihat [Bab 4: Perpajakan](04-perpajakan.md) untuk detail.

---

## Komponen Gaji Khusus Pendidikan

### Gaji Dosen Tetap

**Komponen:**
- Gaji Pokok
- Tunjangan Fungsional (Asisten Ahli, Lektor, Lektor Kepala)
- Tunjangan Struktural (Ketua Prodi, Dekan)
- Tunjangan Kinerja

**BPJS:**
- BPJS Kesehatan: 5% (4% kampus + 1% dosen)
- BPJS Ketenagakerjaan (JHT, JP, JKK, JKM)

### Honorarium Dosen Tidak Tetap

**Sistem:**
- Per SKS per semester
- Contoh: Rp 150.000/SKS
- Dosen mengajar 12 SKS = Rp 1.800.000/bulan

**Pajak:**
- PPh 21 dipotong sesuai PTKP
- Tidak ada BPJS (bukan karyawan tetap)

Lihat [Bab 5: Penggajian](05-penggajian.md) untuk setup komponen dan proses payroll.

---

## Aset Tetap Khusus Pendidikan

### Kategori Aset Kampus

| Kategori | Masa Manfaat | Metode |
|----------|--------------|--------|
| Gedung Kampus | 20 tahun (240 bulan) | Straight Line |
| Peralatan Laboratorium | 4 tahun (48 bulan) | Straight Line |
| Komputer & IT | 4 tahun (48 bulan) | Straight Line |
| Peralatan Kantor | 8 tahun (96 bulan) | Straight Line |
| Kendaraan Dinas | 8 tahun (96 bulan) | Straight Line |

**Catatan:** Tanah tidak disusutkan (aset permanen).

Lihat [Bab 3: Aset Tetap](03-aset-tetap.md) untuk pencatatan dan depresiasi.

---

## Integrasi dengan Sistem Akademik

**Skenario Ideal:**

Balaka fokus pada **financial management**. Untuk integrasi penuh, diperlukan:

1. **Sistem Akademik (SIAKAD):**
   - Manajemen mahasiswa (biodata, NIM, program)
   - Registrasi mata kuliah (KRS)
   - Nilai dan transkrip
   - Status akademik

2. **Integrasi via CSV/API:**
   - SIAKAD → Export data mahasiswa & tagihan → Import ke Balaka
   - Balaka → Export data pembayaran → Import ke SIAKAD

**Workflow:**
```
SIAKAD: Generate tagihan SPP per mahasiswa
    ↓
Export CSV: NIM, Nama, Program, Jumlah SPP
    ↓
Import ke Balaka
    ↓
Buat transaksi batch (Tagihan SPP)
    ↓
Mahasiswa bayar → Catat di aplikasi
    ↓
Export data pembayaran ke SIAKAD
    ↓
SIAKAD: Update status pembayaran → Buka akses KRS
```

---

## Referensi

- [Setup Awal & Import Seed Data](01-setup-awal.md)
- [Tutorial Akuntansi Dasar](02-tutorial-akuntansi.md)
- [Aset Tetap & Depresiasi](03-aset-tetap.md)
- [Perpajakan](04-perpajakan.md)
- [Penggajian & BPJS](05-penggajian.md)
- [Lampiran: Glosarium](12-lampiran-glosarium.md)
- [Lampiran: Referensi Akun Pendidikan](12-lampiran-akun.md)
