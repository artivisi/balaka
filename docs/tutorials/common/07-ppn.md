# PPN (Pajak Pertambahan Nilai)

PPN adalah pajak atas penjualan barang/jasa. Hanya **Pengusaha Kena Pajak (PKP)** yang wajib memungut dan menyetor PPN. Usaha UMKM (non-PKP) tidak perlu menangani PPN.

## Siapa yang Kena PPN?

| Status | Omzet/tahun | Wajib PPN? |
|--------|-------------|-----------|
| UMKM (non-PKP) | < 4,8 miliar | Tidak |
| PKP | ≥ 4,8 miliar atau mendaftar sukarela | Ya |

Jika usaha Anda non-PKP (Online Seller, Coffee Shop, Campus di demo), Anda bisa melewatkan panduan ini.

## Tarif PPN 2025

Sejak PMK 131/2024 (DPP Nilai Lain):

| Komponen | Formula | Keterangan |
|----------|---------|-----------|
| Harga Jual | amount | Harga yang disepakati dengan klien |
| DPP (Dasar Pengenaan Pajak) | amount × 11/12 | DPP Nilai Lain |
| PPN | DPP × 12% = **amount × 11%** | Tarif efektif 11% dari harga jual |
| Total Tagihan | amount + PPN = **amount × 1,11** | Yang dibayar klien |

## PPN Keluaran (Penjualan)

Saat menjual jasa/barang, Anda memungut PPN 11% dari klien.

Template: **Pendapatan Jasa + PPN**

| Akun | Debit | Kredit | Keterangan |
|------|-------|--------|-----------|
| Bank BCA | amount × 1,11 | | Terima dari klien |
| Pendapatan | | amount | Harga jual tanpa PPN |
| Hutang PPN | | amount × 0,11 | PPN yang harus disetor |

**Hutang PPN** bertambah setiap kali ada penjualan dengan PPN.

## PPN Masukan (Pembelian)

Saat membeli barang/jasa dari vendor PKP, Anda membayar PPN. PPN yang dibayar bisa dikreditkan (dikurangkan dari PPN Keluaran).

Template: **Pembelian dengan PPN**

| Akun | Debit | Kredit | Keterangan |
|------|-------|--------|-----------|
| Beban/Aset | amount | | Nilai pembelian |
| PPN Masukan | amount × 0,11 | | PPN yang bisa dikreditkan |
| Bank BCA | | amount × 1,11 | Total yang dibayar |

## Setor PPN

Setiap bulan (paling lambat akhir bulan berikutnya), setor selisih PPN:

**PPN yang disetor = PPN Keluaran − PPN Masukan**

Jika PPN Keluaran > PPN Masukan → kurang bayar, harus setor.
Jika PPN Keluaran < PPN Masukan → lebih bayar, bisa dikompensasi atau restitusi.

Template: **Setor PPN**

| Akun | Debit | Kredit |
|------|-------|--------|
| Hutang PPN | amount | |
| Bank BCA | | amount |

## PPN untuk BUMN (Faktur Pajak 03)

Jika klien Anda adalah BUMN atau instansi pemerintah, PPN **dipungut oleh pembeli** (Wapu):

- Anda **tidak** menerima PPN dari klien
- Klien menyetor PPN langsung ke kas negara
- Anda tetap membuat faktur pajak (kode 03)

Template: **Pendapatan Jasa BUMN (FP 03)**

| Akun | Debit | Kredit |
|------|-------|--------|
| Bank BCA | amount × 0,98 | |
| Kredit Pajak PPh 23 | amount × 0,02 | |
| Pendapatan | | amount |

Tidak ada Hutang PPN karena PPN dipungut oleh pembeli.

## Faktur Pajak Elektronik (e-Faktur)

Setiap transaksi PPN harus dilengkapi faktur pajak. Balaka otomatis mencatat detail PPN saat Anda menggunakan template bertanda PPN.

Data yang dicatat: DPP, PPN, NPWP lawan transaksi, nomor faktur, tanggal.

## Langkah Selanjutnya

- [PPh](08-pph.md) — pajak penghasilan (PPh 21, 23, Final)
- [Monthly Closing](11-monthly-closing.md) — kapan menyetor PPN
