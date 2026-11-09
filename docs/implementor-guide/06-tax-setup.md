# Setup Pajak

Panduan mengkonfigurasi pengaturan perpajakan untuk klien.

## PKP vs Non-PKP

Konfigurasi pajak sangat bergantung pada status PKP klien:

| Aspek | PKP | Non-PKP |
|-------|-----|---------|
| PPN | Wajib pungut dan lapor PPN | Tidak memungut PPN |
| Faktur Pajak | Wajib terbitkan e-Faktur | Tidak perlu |
| Template jurnal | Menggunakan template +PPN | Template tanpa PPN |
| Seed pack | IT Service (PKP-ready) | Online Seller, Coffee Shop, Campus |
| Pelaporan | SPT Masa PPN bulanan | Tidak perlu SPT PPN |

## Konfigurasi Perusahaan

Buka menu **Pengaturan > Perusahaan** dan pastikan field berikut terisi:

### NPWP dan NITKU

| Field | Format | Contoh |
|-------|--------|--------|
| NPWP | 16 digit (format baru) atau 15 digit (format lama) | 01.234.567.8-012.345 |
| NITKU | 22 digit | 0000000000000000000001 |

NPWP dan NITKU digunakan untuk:
- Header laporan pajak
- Export data SPT Tahunan
- Faktur pajak (PKP)

### Status PKP

1. Centang **PKP** jika klien terdaftar sebagai Pengusaha Kena Pajak
2. Isi **PKP Sejak** dengan tanggal pengukuhan
3. Klik **Simpan**

Jika klien berstatus PKP, pastikan:
- Akun 1.1.25 PPN Masukan dan 2.1.03 Hutang PPN ada di COA
- Template pendapatan menggunakan formula PPN (`amount * 1.11`, `amount * 0.11`)
- Template pembelian mencatat PPN Masukan

### Penandatangan

| Field | Keterangan |
|-------|-----------|
| Nama Penandatangan | Nama direktur/penanggung jawab yang menandatangani SPT |
| Jabatan | Jabatan penandatangan (misal: Direktur) |

Data ini muncul di laporan pajak dan dokumen resmi.

## Profil Pajak

Buka menu **Pengaturan > Profil Pajak** untuk konfigurasi detail perpajakan.

## Tahun Fiskal

Buka **Pengaturan > Perusahaan** dan isi:

| Field | Keterangan |
|-------|-----------|
| Bulan Awal Tahun Fiskal | Bulan dimulainya tahun buku (umumnya 1 = Januari) |

Sebagian besar perusahaan di Indonesia menggunakan tahun fiskal Januari-Desember. Jika klien menggunakan tahun fiskal berbeda (misal April-Maret), pilih bulan yang sesuai.

## Periode Fiskal

Setiap bulan dalam tahun berjalan harus didaftarkan sebagai periode fiskal.

Buka **Master Data > Periode Fiskal**:

1. Klik **Buat Periode**
2. Isi **Tahun** dan **Bulan**
3. Status: **OPEN**
4. Klik **Simpan**
5. Ulangi untuk semua bulan (Januari sampai Desember)

Periode fiskal mengontrol:
- Apakah transaksi bisa diposting ke bulan tersebut
- Apakah bulan tersebut sudah ditutup (CLOSED)
- Status pelaporan pajak bulan tersebut

### Status Periode

| Status | Arti |
|--------|------|
| OPEN | Transaksi bisa diposting |
| CLOSED | Periode sudah ditutup, transaksi tidak bisa diposting |

Setelah semua transaksi bulan tersebut selesai dicatat dan direview, tutup periode melalui halaman Periode Fiskal.

## Tax Deadline (Jadwal Pajak)

Seed pack sudah menyertakan jadwal pelaporan pajak standar Indonesia:

| Jenis Pajak | Batas Setor | Batas Lapor |
|-------------|-------------|-------------|
| PPh 21 | Tanggal 10 bulan berikutnya | Tanggal 20 bulan berikutnya |
| PPh 23 | Tanggal 10 bulan berikutnya | Tanggal 20 bulan berikutnya |
| PPh 25 | Tanggal 15 bulan berikutnya | Tanggal 20 bulan berikutnya |
| PPh 4(2) | Tanggal 10 bulan berikutnya | Tanggal 20 bulan berikutnya |
| PPN | Tanggal 15 bulan berikutnya | Akhir bulan berikutnya |

Jadwal ini bisa dilihat dan disesuaikan di **Master Data > Tax Deadlines**.

## PPh 21 — Konfigurasi Payroll

Balaka menggunakan metode TER (Tarif Efektif Rata-rata) sesuai PMK 168/2023 untuk perhitungan PPh 21 bulanan.

### Kategori TER

Setiap karyawan memiliki kategori TER berdasarkan status PTKP:

| Kategori TER | Status PTKP |
|--------------|-------------|
| A | TK/0, TK/1 |
| B | TK/2, TK/3, K/0, K/1 |
| C | K/2, K/3 |

Kategori TER menentukan tarif PPh 21 bulanan. Balaka menghitung otomatis berdasarkan status PTKP karyawan.

### Cara Kerja PPh 21 TER

- **Januari s/d November**: PPh 21 dihitung menggunakan tarif TER (persentase flat dari penghasilan bruto)
- **Desember**: Rekonsiliasi tahunan menggunakan tarif progresif Pasal 17, dikurangi PPh 21 yang sudah dipotong Januari-November

### Setup Karyawan untuk PPh 21

Buka **Master Data > Karyawan** dan pastikan setiap karyawan memiliki:

| Field | Keterangan |
|-------|-----------|
| Status PTKP | TK/0, TK/1, TK/2, TK/3, K/0, K/1, K/2, K/3 |
| NPWP | NPWP karyawan (wajib untuk pemotongan PPh 21) |

## BPJS — Konfigurasi

### Kelas Risiko JKK

BPJS JKK (Jaminan Kecelakaan Kerja) memiliki 5 kelas risiko dengan tarif berbeda:

| Kelas | Tarif | Jenis Usaha |
|-------|-------|-------------|
| 1 | 0,24% | Jasa, perdagangan, restoran, hotel |
| 2 | 0,54% | Pertanian, perikanan, kehutanan |
| 3 | 0,89% | Pertambangan, bahan kimia |
| 4 | 1,27% | Transportasi, konstruksi ringan |
| 5 | 1,74% | Konstruksi berat, industri berat |

Untuk sebagian besar klien Balaka (IT service, online seller, coffee shop, kampus), pilih **Kelas 1**.

### Komponen BPJS

| Komponen | Tanggungan Karyawan | Tanggungan Perusahaan | Batas Atas Upah |
|----------|--------------------|-----------------------|-----------------|
| Kesehatan | 1% | 4% | Rp 12.000.000 |
| JHT | 2% | 3,7% | Tidak ada |
| JP | 1% | 2% | Rp 11.086.300 sejak Maret 2026 (berubah tiap Maret) |
| JKK | — | Sesuai kelas risiko | Tidak ada |
| JKM | — | 0,3% | Tidak ada |

Komponen BPJS sudah dikonfigurasi di seed pack (file `03_salary_components.csv`). Pastikan kelas risiko JKK sesuai jenis usaha klien.

## PPh Final UMKM (Non-PKP)

Untuk klien UMKM non-PKP dengan omzet di bawah Rp 4,8 miliar:

- Tarif PPh Final: 0,5% dari omzet bruto (PP 55/2022)
- Batas waktu setor: tanggal 15 bulan berikutnya
- Template: **Bayar PPh Final UMKM** (tersedia di seed pack Online Seller dan Coffee Shop)

Perhitungan: total pendapatan bruto bulan berjalan x 0,5%

## Checklist Konfigurasi Pajak

Pastikan semua item berikut sudah dikonfigurasi:

- [ ] NPWP perusahaan terisi
- [ ] NITKU terisi
- [ ] Status PKP sudah benar (centang/tidak)
- [ ] Tanggal PKP terisi (jika PKP)
- [ ] Bulan awal tahun fiskal terisi
- [ ] Nama dan jabatan penandatangan terisi
- [ ] Periode fiskal tahun berjalan sudah dibuat
- [ ] Kelas risiko BPJS JKK sudah sesuai
- [ ] Status PTKP semua karyawan sudah benar
- [ ] NPWP semua karyawan sudah terisi
- [ ] Jadwal pajak sudah direview

## Langkah Selanjutnya

[Training Klien](07-training-clients.md)
