# Payroll Kampus

Setiap bulan, jalankan payroll untuk 8 karyawan. Sistem menghitung BPJS dan PPh 21 otomatis.

Untuk konsep payroll, lihat [Panduan Payroll](../common/06-payroll.md).

## Konfigurasi STMIK Merdeka Digital

| Parameter | Nilai |
|-----------|-------|
| Base Salary | 8.000.000 |
| JKK Risk Class | 1 (jasa/pendidikan) |
| Jumlah Karyawan | 8 |

## Struktur Karyawan

STMIK Merdeka Digital memiliki 8 karyawan tetap:

| Jabatan | Jumlah | Keterangan |
|---------|--------|-----------|
| Ketua STMIK | 1 | Pimpinan |
| Wakil Ketua I (Akademik) | 1 | Pimpinan |
| Wakil Ketua II (Keuangan & SDM) | 1 | Pimpinan |
| Dosen Tetap | 2 | Tenaga pengajar |
| Staff Administrasi/TU | 2 | Tenaga kependidikan |
| Staff IT | 1 | Tenaga kependidikan |

Semua karyawan mendapatkan gaji pokok yang sama: Rp 8.000.000/bulan. Dalam praktik, dosen biasanya mendapat tunjangan fungsional tambahan — tetapi di simulasi ini menggunakan gaji flat.

## Langkah Bulanan

### 1. Buat Payroll Run

Buka **Payroll - Buat Baru**:
- Periode: `2025-01`
- Base Salary: `8000000`
- JKK Risk Class: `1`

Klik **Buat & Kalkulasi**.

### 2. Review Hasil

| Item | Jan-Nov (per bulan) | Desember |
|------|-------------------|----------|
| Total Gross | 64.000.000 | 64.000.000 |
| Total PPh 21 | 960.000 | 2.688.000 |
| Total Net Pay | — | — |
| Total Company BPJS | — | — |

PPh 21 Desember lebih besar karena **rekonsiliasi tahunan** — PPh 21 setahun dihitung ulang dengan tarif Pasal 17 dari penghasilan neto (bruto − biaya jabatan − iuran JHT + JP karyawan), lalu dikurangi PPh 21 Jan–Nov. Lihat [Panduan Payroll](../common/06-payroll.md#pph-21-ter) untuk penjelasan metode TER.

### 3. Approve & Post

- Klik **Approve** - review final
- Klik **Post ke Jurnal** - jurnal "Post Gaji Bulanan" otomatis dibuat

### 4. Bayar Gaji dan BPJS

Setelah payroll diposting, buat transaksi:
- **Bayar Hutang Gaji** — jumlah = Total Net Pay
- **Bayar Hutang BPJS** — jumlah = Total BPJS (karyawan + perusahaan)

### 5. Setor PPh 21

Bulan berikutnya (sebelum tanggal 10):
- **Setor PPh 21** — jumlah = Total PPh 21 dari payroll bulan lalu

## PPh 21 di Gaji 8 Juta

Pada gaji Rp 8.000.000/bulan, tarif TER yang diterapkan tergantung status PTKP karyawan. Tarif efektif di level gaji ini lebih rendah dibandingkan gaji 15 juta (seperti di tutorial IT Service).

Total PPh 21 bulan Jan-Nov: **Rp 960.000/bulan** (untuk 8 karyawan).

Hutang PPh 21 di akhir tahun: **Rp 2.688.000** — ini adalah PPh 21 Desember yang akan disetor Januari 2026.

## Total Beban Gaji Tahunan

| Komponen | Per Bulan | Per Tahun |
|----------|----------|----------|
| Beban Gaji | 64.000.000 | 768.000.000 |
| Beban BPJS (perusahaan) | 6.553.600 | 78.643.200 |
| **Total** | **70.553.600** | **846.643.200** |

Beban gaji dan BPJS adalah komponen beban terbesar kampus — sekitar 72% dari total beban operasional.

## Konteks Kampus

Berbeda dengan perusahaan IT yang memiliki 5 karyawan dengan gaji 15 juta, kampus memiliki lebih banyak karyawan dengan gaji lebih rendah. Beberapa catatan:

- **Dosen tidak tetap** dicatat terpisah menggunakan template **Honorarium Dosen Tidak Tetap** (per SKS). Dalam demo ini tidak ada transaksi honorarium — semua dosen berstatus tetap.
- **BPJS** dihitung otomatis oleh sistem berdasarkan base salary dan risk class. Lihat [Panduan BPJS](../common/09-bpjs.md).
- STMIK berstatus **non-PKP**, sehingga tidak ada PPN yang perlu disetor dari gaji atau operasional.

## Langkah Selanjutnya

- [Operasional Kampus](06-campus-operations.md) — listrik, air, internet, pemeliharaan gedung
