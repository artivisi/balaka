# Aset Tetap dan Penyusutan

Aset tetap adalah barang bernilai tinggi yang dipakai lebih dari satu tahun: komputer, kendaraan, mesin espresso, peralatan lab. Nilainya tidak dibebankan sekaligus, melainkan **disusutkan** (depreciated) selama masa manfaatnya.

## Mengapa Disusutkan?

Laptop seharga 25 juta dipakai selama 4 tahun. Tidak adil jika seluruh 25 juta dibebankan di bulan pembelian. Penyusutan membagi biaya merata:

- Biaya per bulan = 25.000.000 / 48 bulan = **520.833/bulan**
- Setiap bulan, Rp 520.833 dicatat sebagai beban penyusutan

## Mencatat Pembelian Aset

### Langkah 1: Daftarkan Aset

Buka **Master Data → Aset Tetap → Tambah Baru**. Isi:

| Field | Contoh |
|-------|--------|
| Kode Aset | AST-LPT-001 |
| Nama | Laptop HP EliteBook 860 |
| Kategori | KOMPUTER |
| Tanggal Beli | 2025-01-15 |
| Harga Beli | 25.000.000 |
| Tanggal Mulai Penyusutan | 2025-01-15 |
| Metode Penyusutan | Garis Lurus (Straight Line) |
| Masa Manfaat | 48 bulan |
| Nilai Residu | 0 |

### Langkah 2: Buat Transaksi Pembelian

Gunakan template **Pembelian Aset Tetap** (DETAILED, variabel `assetCost`):

| Akun | Debit | Kredit |
|------|-------|--------|
| 1.2.01 Peralatan Komputer | 25.000.000 | |
| 1.1.02 Bank BCA | | 25.000.000 |

## Penyusutan Bulanan

Setiap akhir bulan, buka **Aset Tetap → Penyusutan**:

1. Pilih periode (bulan)
2. Klik **Generate** — sistem menghitung penyusutan untuk semua aset aktif
3. Klik **Post** untuk setiap entry yang muncul

Jurnal penyusutan:

| Akun | Debit | Kredit |
|------|-------|--------|
| 5.1.12 Beban Penyusutan | 520.833 | |
| 1.2.02 Akum. Penyusutan | | 520.833 |

**Beban Penyusutan** masuk ke Laba Rugi (mengurangi laba).
**Akumulasi Penyusutan** masuk ke Neraca (mengurangi nilai aset).

## Nilai Buku

Nilai buku = Harga Beli − Akumulasi Penyusutan

Contoh setelah 12 bulan:
- Harga Beli: 25.000.000
- Akum. Penyusutan: 520.833 × 12 = 6.250.000
- **Nilai Buku: 18.750.000**

## Metode Penyusutan

| Metode | Cara Hitung | Kapan Pakai |
|--------|-------------|------------|
| **Garis Lurus** (Straight Line) | Sama rata per bulan | Paling umum, sederhana |
| **Saldo Menurun** (Declining Balance) | Persentase dari sisa nilai buku | Aset yang cepat usang |

Untuk usaha kecil, **Garis Lurus** cukup.

## Kategori Aset

Setiap kategori aset punya konfigurasi:
- Akun aset (misal 1.2.01 Peralatan Komputer)
- Akun akumulasi penyusutan (misal 1.2.02)
- Akun beban penyusutan (misal 5.1.12)
- Masa manfaat default

| Industri | Kategori | Masa Manfaat |
|----------|----------|-------------|
| IT Service | Komputer (KOMPUTER) | 48 bulan |
| IT Service | Kendaraan (KENDARAAN) | 96 bulan |
| Coffee Shop | Peralatan Kopi (CAT-COFFEE) | 60 bulan |
| Coffee Shop | Furniture (CAT-FURNITURE) | 120 bulan |
| Campus | Peralatan Lab (LAB) | 48 bulan |
| Campus | Gedung (GEDUNG) | 240 bulan |

## Langkah Selanjutnya

- [Monthly Closing](11-monthly-closing.md) — penyusutan sebagai bagian dari tutup bulan
- [Year-End Closing](12-year-end-closing.md) — dampak penyusutan di laporan tahunan
