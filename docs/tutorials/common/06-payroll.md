# Payroll (Penggajian)

Fitur payroll menghitung gaji karyawan, potongan BPJS, dan PPh 21 secara otomatis. Anda cukup membuat payroll run setiap bulan, sistem menghitung sisanya.

## Siklus Payroll Bulanan

```
Buat Payroll Run → Kalkulasi Otomatis → Approve → Posting ke Jurnal
        ↓                    ↓               ↓            ↓
  Isi periode,          BPJS + PPh 21    Review hasil   Jurnal entry
  base salary,          dihitung per                    otomatis dibuat
  risk class            karyawan
```

### Langkah 1: Buat Payroll Run

Buka **Payroll → Buat Baru**. Isi:

| Field | Isi | Keterangan |
|-------|-----|-----------|
| Periode | 2025-01 | Format YYYY-MM |
| Base Salary | 15.000.000 | Gaji pokok (sama untuk semua karyawan) |
| JKK Risk Class | 1 | Kelas risiko BPJS Kecelakaan Kerja (1-5) |

Klik **Buat & Kalkulasi**. Sistem otomatis menghitung untuk semua karyawan aktif.

### Langkah 2: Review Hasil Kalkulasi

Halaman detail payroll menampilkan ringkasan:

- **Total Gross** — gaji kotor seluruh karyawan
- **Total Deductions** — potongan karyawan (BPJS + PPh 21)
- **Total Net Pay** — gaji bersih yang akan ditransfer
- **Total Company BPJS** — kontribusi BPJS perusahaan
- **Total PPh 21** — pajak penghasilan yang dipotong

Dan detail per karyawan: BPJS Kes, JHT, JP, JKK, JKM, PPh 21, net pay.

### Langkah 3: Approve

Klik **Approve** setelah review. Status berubah ke APPROVED.

### Langkah 4: Posting ke Jurnal

Klik **Post ke Jurnal**. Sistem membuat transaksi "Post Gaji Bulanan" dengan jurnal:

| Akun | Debit | Kredit |
|------|-------|--------|
| Beban Gaji | grossSalary | |
| Beban BPJS Kesehatan | companyBpjsKes | |
| Beban BPJS Ketenagakerjaan | companyBpjsTk | |
| Hutang Gaji | | netPay |
| Hutang BPJS | | totalBpjs |
| Hutang PPh 21 | | pph21 |

## Setelah Posting

Payroll sudah diposting, tapi gaji belum ditransfer ke karyawan. Anda perlu membuat transaksi tambahan:

### Bayar Gaji (Transfer ke Karyawan)

Template: **Bayar Hutang Gaji**. Jumlah = total net pay dari payroll.

| Akun | Debit | Kredit |
|------|-------|--------|
| 2.1.10 Hutang Gaji | net pay | |
| 1.1.02 Bank BCA | | net pay |

### Bayar BPJS

Template: **Bayar Hutang BPJS**. Jumlah = total BPJS (karyawan + perusahaan).

### Setor PPh 21

Template: **Setor PPh 21**. Jumlah = total PPh 21 dari payroll. Disetor paling lambat tanggal 10 bulan berikutnya.

## PPh 21 TER (Tarif Efektif Rata-rata)

Sejak 2024, PPh 21 menggunakan metode TER (PMK 168/2023):

- **Januari-November**: tarif efektif per bulan berdasarkan gaji dan kategori PTKP
- **Desember**: rekonsiliasi tahunan menggunakan tarif progresif

### Kategori TER

| Kategori | Status PTKP |
|----------|-------------|
| A | TK/0, TK/1, K/0 |
| B | TK/2, TK/3, K/1, K/2 |
| C | K/3, K/I/0 s/d K/I/3 |

### Contoh Perhitungan (Gaji 15 juta)

| Karyawan | PTKP | Kategori | TER Rate | PPh 21/bulan |
|----------|------|----------|----------|-------------|
| Ahmad (K/2) | 67.500.000 | B | ~4,5% | ~675.000 |
| Sari (TK/0) | 54.000.000 | A | ~5% | ~750.000 |

### Desember: Rekonsiliasi

Di bulan Desember, PPh 21 dihitung ulang menggunakan tarif progresif tahunan:
- 5% untuk PKP 0 – 60 juta
- 15% untuk PKP 60 – 250 juta
- 25% untuk PKP 250 – 500 juta
- 30% untuk PKP 500 juta – 5 miliar
- 35% untuk PKP > 5 miliar

PPh 21 Desember = PPh 21 tahunan − total PPh 21 Jan-Nov. Biasanya lebih besar dari bulan biasa.

## BPJS

Untuk detail tarif BPJS, lihat [Panduan BPJS](09-bpjs.md).

## Langkah Selanjutnya

- [BPJS](09-bpjs.md) — detail tarif dan cara hitung
- [PPh](08-pph.md) — jenis-jenis PPh dan cara setor
