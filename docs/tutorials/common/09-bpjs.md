# BPJS (Jaminan Sosial)

Setiap karyawan wajib didaftarkan di BPJS Kesehatan dan BPJS Ketenagakerjaan. Iuran dibagi antara karyawan (dipotong dari gaji) dan perusahaan (ditanggung sebagai beban).

## Tarif BPJS 2025

### BPJS Kesehatan

| Komponen | Tarif | Basis | Cap |
|----------|-------|-------|-----|
| Perusahaan | 4% | Gaji | Maks basis Rp 12.000.000 |
| Karyawan | 1% | Gaji | Maks basis Rp 12.000.000 |

Jika gaji > 12 juta, iuran tetap dihitung dari 12 juta.

**Contoh gaji 15 juta:**
- Basis = 12.000.000 (cap)
- Perusahaan = 12.000.000 × 4% = Rp 480.000
- Karyawan = 12.000.000 × 1% = Rp 120.000

### BPJS Ketenagakerjaan — JHT (Jaminan Hari Tua)

| Komponen | Tarif | Basis | Cap |
|----------|-------|-------|-----|
| Perusahaan | 3,7% | Gaji | Tidak ada |
| Karyawan | 2% | Gaji | Tidak ada |

**Contoh gaji 15 juta:**
- Perusahaan = 15.000.000 × 3,7% = Rp 555.000
- Karyawan = 15.000.000 × 2% = Rp 300.000

### BPJS Ketenagakerjaan — JP (Jaminan Pensiun)

| Komponen | Tarif | Basis | Cap |
|----------|-------|-------|-----|
| Perusahaan | 2% | Gaji | Maks basis Rp 10.042.300 |
| Karyawan | 1% | Gaji | Maks basis Rp 10.042.300 |

**Contoh gaji 15 juta:**
- Basis = 10.042.300 (cap)
- Perusahaan = 10.042.300 × 2% = Rp 200.846
- Karyawan = 10.042.300 × 1% = Rp 100.423

### BPJS Ketenagakerjaan — JKK (Jaminan Kecelakaan Kerja)

Ditanggung 100% perusahaan. Tarif berdasarkan kelas risiko:

| Kelas | Tarif | Jenis Usaha |
|-------|-------|------------|
| 1 | 0,24% | Kantor, jasa, IT |
| 2 | 0,54% | Perdagangan, restoran |
| 3 | 0,89% | Pertanian, perikanan |
| 4 | 1,27% | Industri, manufaktur |
| 5 | 1,74% | Konstruksi, pertambangan |

### BPJS Ketenagakerjaan — JKM (Jaminan Kematian)

| Komponen | Tarif |
|----------|-------|
| Perusahaan | 0,3% |

## Ringkasan per Karyawan (Gaji 15 juta, Kelas 1)

| Komponen | Karyawan | Perusahaan |
|----------|----------|-----------|
| BPJS Kesehatan | 120.000 | 480.000 |
| JHT | 300.000 | 555.000 |
| JP | 100.423 | 200.846 |
| JKK | — | 36.000 |
| JKM | — | 45.000 |
| **Total** | **520.423** | **1.316.846** |

Total potongan karyawan: Rp 520.423/bulan
Total beban perusahaan: Rp 1.316.846/bulan (di atas potongan karyawan)

## Di Balaka

BPJS dihitung otomatis saat Anda membuat payroll run. Anda hanya perlu mengisi:
- **Base Salary** — gaji pokok
- **JKK Risk Class** — kelas risiko (1-5)

Sistem menghitung semua komponen BPJS per karyawan.

Untuk alur payroll lengkap, lihat [Panduan Payroll](06-payroll.md).

## Langkah Selanjutnya

- [Aset Tetap](10-fixed-assets.md) — pembelian dan penyusutan peralatan
