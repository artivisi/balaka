# Pengeluaran Operasional

Pengeluaran rutin kedai kopi dicatat menggunakan template SIMPLE — isi jumlah, klik posting, selesai.

## Pengeluaran Bulanan

| Template | Akun Beban | Rata-rata/Bulan | Keterangan |
|----------|-----------|----------------|-----------|
| Bayar Sewa | 6.2.01 | 8.000.000 | Sewa ruko Jl. Braga |
| Bayar Listrik | 6.2.02 | 1.450.000 - 1.700.000 | Mesin espresso, AC, freezer |
| Beban Admin Bank | 6.2.10 | 15.000 | Potongan otomatis BCA |

## Pengeluaran Triwulanan

| Template | Akun Beban | Per Kuartal | Keterangan |
|----------|-----------|------------|-----------|
| Bayar Beban Operasional | 6.2.12 | 1.200.000 - 1.500.000 | Perlengkapan toko, ATK |

## Cara Mencatat

Semua template pengeluaran bekerja sama:

1. Pilih template (misal: Bayar Sewa)
2. Isi tanggal dan jumlah
3. Isi deskripsi dan referensi
4. Klik **Simpan & Posting**

Jurnal yang dihasilkan (contoh sewa):

| Akun | Debit | Kredit |
|------|-------|--------|
| 6.2.01 Beban Sewa Tempat | 8.000.000 | |
| 1.1.01 Kas | | 8.000.000 |

Beban bertambah (debit), kas berkurang (kredit). Perhatikan bahwa sewa dan listrik dibayar dari **Kas**, bukan dari Bank — karena ini pembayaran langsung di lokasi.

## Transfer Antar Bank

Setiap bulan, Kedai Kopi Nusantara menyetorkan sebagian uang kas ke Bank BCA sebesar **Rp 32.000.000**. Ini bukan pengeluaran — hanya memindahkan uang dari kasir ke bank.

**Template:** Transfer Antar Bank

| Field | Isi |
|-------|-----|
| Template | Transfer Antar Bank |
| Tanggal | 2025-01-27 |
| Jumlah | 32.000.000 |
| Deskripsi | Setoran kas ke bank Januari |
| Referensi | TRF-KAS-2025-01 |

Jurnal yang dihasilkan:

| Akun | Debit | Kredit |
|------|-------|--------|
| 1.1.02 Bank BCA | 32.000.000 | |
| 1.1.01 Kas | | 32.000.000 |

Bank bertambah, kas berkurang. Total aset tetap sama — hanya berpindah tempat. Uang di bank digunakan untuk membayar gaji (via transfer), BPJS, dan PPh.

## Total Pengeluaran Operasional 2025

| Kategori | Jumlah/Tahun |
|----------|-------------|
| Beban Gaji (via payroll) | 360.000.000 |
| Beban BPJS Kesehatan (via payroll) | 14.400.000 |
| Beban BPJS Ketenagakerjaan (via payroll) | 23.544.000 |
| Beban Sewa Tempat | 96.000.000 |
| Beban Listrik | 18.250.000 |
| Beban Potongan Platform | 17.400.000 |
| Beban Operasional Lainnya | 5.400.000 |
| Beban Admin Bank | 180.000 |
| **Total Beban Operasional** | **535.174.000** |

## Saldo Kas dan Bank

Alur uang di kedai kopi:

```
Penjualan tunai → Kas (1.1.01) → Transfer → Bank BCA (1.1.02)
Penjualan online → langsung ke Bank BCA (1.1.02)

Dari Kas: bayar bahan baku, sewa, listrik
Dari Bank: bayar gaji, BPJS, PPh, admin bank
```

Di akhir tahun 2025:

| Akun | Saldo |
|------|-------|
| Kas | 62.450.000 |
| Bank BCA | 14.165.000 |

## Tips

- **Sewa** — catat sewa setiap bulan meskipun kontrak tahunan, agar beban tersebar merata di laporan laba rugi
- **Listrik naik di Desember** — mesin espresso lebih sering nyala karena peak season. Ini normal
- **Transfer rutin** — setorkan uang kas ke bank secara rutin agar tidak menumpuk kas fisik terlalu banyak

## Langkah Selanjutnya

- [Payroll](06-payroll.md) — gaji 6 karyawan dan BPJS
