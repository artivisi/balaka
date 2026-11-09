# Payroll Bulanan

Setiap bulan, jalankan payroll untuk 6 karyawan. Sistem menghitung BPJS dan PPh 21 otomatis.

Untuk konsep payroll, lihat [Panduan Payroll](../common/06-payroll.md).

## Konfigurasi Kedai Kopi Nusantara

| Parameter | Nilai |
|-----------|-------|
| Base Salary | 5.000.000 |
| JKK Risk Class | 2 (F&B / restoran) |
| Jumlah Karyawan | 6 |

## Data Karyawan

| Karyawan | Jabatan | PTKP |
|----------|---------|------|
| Karyawan 1 | Barista | TK/0 |
| Karyawan 2 | Barista | TK/0 |
| Karyawan 3 | Baker | K/0 |
| Karyawan 4 | Baker | K/1 |
| Karyawan 5 | Kasir | TK/0 |
| Karyawan 6 | Cleaning | K/0 |

## PPh 21 di Gaji 5 Juta

Dengan gaji Rp 5.000.000/bulan, PPh 21 sangat kecil. Pada level gaji ini, setelah dikurangi BPJS karyawan dan PTKP, penghasilan kena pajak hampir nol untuk sebagian besar karyawan.

Total PPh 21 per bulan (Jan-Nov): sekitar **Rp 50.000** (tergantung komposisi PTKP karyawan).

Ini sangat berbeda dengan perusahaan IT service yang gajinya 15 juta — di sana PPh 21 bisa mencapai 3,6 juta per bulan.

## Langkah Bulanan

### 1. Buat Payroll Run

Buka **Payroll → Buat Baru**:
- Periode: `2025-01`
- Base Salary: `5000000`
- JKK Risk Class: `2`

Klik **Buat & Kalkulasi**.

### 2. Review Hasil

Sistem menampilkan:

| Item | Per Bulan (Jan-Nov) |
|------|-------------------|
| Total Gross | 30.000.000 |
| Total Employee BPJS | ~1.730.000 |
| Total PPh 21 | ~50.000 |
| Total Deductions | ~1.780.000 |
| Total Net Pay | ~28.220.000 |
| Total Company BPJS | ~3.162.000 |

### 3. Approve & Post

- Klik **Approve** → review final
- Klik **Post ke Jurnal** → jurnal "Post Gaji Bulanan" otomatis dibuat

### 4. Bayar Gaji

Setelah payroll diposting, buat transaksi:
- **Bayar Hutang Gaji** — jumlah = Total Net Pay
- **Bayar Hutang BPJS** — jumlah = Total BPJS (karyawan + perusahaan)

### 5. Setor PPh 21

Bulan berikutnya (sebelum tanggal 10):
- **Setor PPh 21** — jumlah = Total PPh 21 dari payroll bulan lalu

## Dampak di Laporan Keuangan

Di akhir tahun 2025:

| Akun | Saldo | Keterangan |
|------|-------|-----------|
| 6.1.01 Gaji Barista | 360.000.000 | 30jt × 12 bulan |
| 6.1.04 BPJS Kesehatan | 14.400.000 | Company Kes × 12 |
| 6.1.05 BPJS Ketenagakerjaan | 23.544.000 | Company TK × 12 |
| 2.1.14 Hutang PPh 21 | 240.000 | PPh 21 Desember, disetor Januari 2026. Rekonsiliasi tahunan mengurangkan iuran JHT + JP karyawan (3% gaji) dari penghasilan neto |

Total beban gaji dan BPJS: **397.944.000/tahun** — ini adalah beban terbesar kedai kopi (75% dari total beban operasional).

## Perbandingan dengan IT Service

| | Kedai Kopi | IT Service |
|---|-----------|-----------|
| Gaji/orang | 5.000.000 | 15.000.000 |
| Jumlah karyawan | 6 | 5 |
| Total gross/bulan | 30.000.000 | 75.000.000 |
| PPh 21/bulan | ~50.000 | 3.600.000 |
| Revenue/bulan | ~44.000.000 | ~176.000.000 |
| Rasio gaji/revenue | ~68% | ~43% |

Rasio gaji terhadap revenue di kedai kopi jauh lebih tinggi — ini adalah tantangan utama bisnis F&B dengan banyak karyawan.

## Tips

- **PPh 21 Desember lebih besar** — ini bukan bug, melainkan rekonsiliasi tahunan. Lihat [Panduan PPh](../common/08-pph.md) untuk penjelasan TER method
- **Review kebutuhan staffing** — dengan rasio gaji 68% dari revenue, pertimbangkan apakah efisiensi bisa ditingkatkan
- **Bayar gaji tepat waktu** — jangan lupa transaksi "Bayar Hutang Gaji" setelah posting payroll

## Langkah Selanjutnya

- [Pajak UMKM](07-tax-umkm.md) — PPh Final 0,5% dan pertimbangan pajak
