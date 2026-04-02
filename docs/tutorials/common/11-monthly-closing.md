# Tutup Buku Bulanan

Setiap akhir bulan, lakukan serangkaian langkah untuk memastikan semua transaksi tercatat dan pajak disetor tepat waktu.

## Checklist Akhir Bulan

### 1. Pastikan Semua Transaksi Tercatat

- [ ] Semua pendapatan bulan ini sudah diinput
- [ ] Semua pengeluaran (sewa, listrik, operasional) sudah diinput
- [ ] Beban admin bank sudah dicatat

### 2. Jalankan Payroll

- [ ] Buat payroll run untuk bulan ini
- [ ] Review kalkulasi (BPJS + PPh 21)
- [ ] Approve dan post ke jurnal
- [ ] Bayar gaji ke karyawan (Bayar Hutang Gaji)
- [ ] Bayar BPJS (Bayar Hutang BPJS)

Lihat [Panduan Payroll](06-payroll.md) untuk detail.

### 3. Post Penyusutan

- [ ] Buka Aset Tetap → Penyusutan
- [ ] Generate entry untuk bulan ini
- [ ] Post semua entry

Lihat [Panduan Aset Tetap](10-fixed-assets.md) untuk detail.

### 4. Setor Pajak Bulan Lalu

Pajak bulan lalu disetor di bulan ini:

| Pajak | Batas Setor | Template |
|-------|-------------|---------|
| PPh 21 | Tanggal 10 | Setor PPh 21 |
| PPh 23 | Tanggal 10 | Setor PPh 23 |
| PPh 4(2) | Tanggal 10 | Setor PPh 4(2) |
| PPN | Akhir bulan | Setor PPN |
| PPh Final UMKM | Tanggal 15 | Bayar PPh Final UMKM |

**PPh 21:** jumlah dari payroll bulan lalu.
**PPN:** jumlah PPN Keluaran bulan lalu dikurangi PPN Masukan.
**PPh Final UMKM:** 0,5% dari total penjualan bulan lalu.

### 5. Tutup Periode

- [ ] Buka Master Data → Periode Fiskal
- [ ] Klik periode bulan ini
- [ ] Klik **Tutup Bulan**

Setelah ditutup, tidak ada transaksi baru yang bisa diposting ke bulan tersebut. Ini mencegah perubahan di bulan yang sudah final.

## Jadwal Bulanan

| Tanggal | Kegiatan |
|---------|---------|
| 1-5 | Input transaksi akhir bulan lalu yang tertinggal |
| 1-5 | Jalankan payroll bulan lalu (jika belum) |
| 10 | Setor PPh 21, 23, 4(2) bulan lalu |
| 15 | Setor PPh Final UMKM bulan lalu |
| 25-31 | Input semua transaksi bulan ini |
| 25-31 | Post penyusutan bulan ini |
| Akhir bulan | Setor PPN bulan lalu |
| Akhir bulan | Tutup periode bulan lalu |

## Mengecek Hasil

Setelah semua langkah selesai, cek:

1. **Trial Balance** — pastikan balance (debit = kredit)
2. **Hutang Gaji** — harus 0 (sudah dibayar)
3. **Hutang BPJS** — harus 0 (sudah disetor)
4. **Hutang PPh 21** — hanya tersisa bulan ini (belum jatuh tempo)
5. **Bank** — saldo harus positif dan masuk akal

## Langkah Selanjutnya

- [Year-End Closing](12-year-end-closing.md) — tutup buku akhir tahun
