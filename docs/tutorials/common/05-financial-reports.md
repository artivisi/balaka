# Laporan Keuangan

Laporan keuangan adalah output utama dari pencatatan akuntansi. Balaka menyediakan 4 laporan standar yang bisa diakses dari menu **Laporan**.

## 1. Neraca Saldo (Trial Balance)

Daftar semua akun beserta saldo debit atau kreditnya pada tanggal tertentu.

**Fungsi:** Memastikan total debit = total kredit (buku seimbang).

| Akun | Debit | Kredit |
|------|-------|--------|
| 1.1.02 Bank BCA | 1.288.859.240 | |
| 1.1.26 Kredit Pajak PPh 23 | 36.600.000 | |
| 2.1.03 Hutang PPN | | 69.300.000 |
| 3.1.01 Modal Disetor | | 500.000.000 |
| 4.1.01 Pendapatan Jasa | | 2.110.000.000 |
| 5.1.01 Beban Gaji | 900.000.000 | |
| ... | ... | ... |
| **TOTAL** | **2.711.275.000** | **2.711.275.000** |

Jika total tidak sama, ada transaksi yang belum benar. Dalam praktik, Balaka menjamin keseimbangan karena setiap template sudah divalidasi.

**Cara akses:** Laporan → Neraca Saldo → pilih tanggal

## 2. Laporan Laba Rugi (Income Statement / P&L)

Menampilkan pendapatan dikurangi beban selama periode tertentu.

```
PENDAPATAN
  Pendapatan Jasa Konsultasi      2.110.000.000
                                  ─────────────
  Total Pendapatan                2.110.000.000

BEBAN
  Beban Gaji                        900.000.000
  Beban BPJS                         79.010.760
  Beban Sewa                        180.000.000
  Beban Cloud & Server                66.000.000
  Beban Penyusutan                    15.625.000
  ...lainnya                         75.180.000
                                  ─────────────
  Total Beban                     1.315.815.760

                                  ═════════════
  LABA BERSIH                       794.184.240
```

**Laba bersih positif** = perusahaan untung. **Negatif** = rugi.

**Cara akses:** Laporan → Laba Rugi → pilih rentang tanggal

## 3. Neraca (Balance Sheet)

Menampilkan posisi keuangan pada tanggal tertentu: apa yang dimiliki (aset), apa yang dihutang (liabilitas), dan berapa modal pemilik (ekuitas).

```
ASET
  Bank BCA                        1.288.859.240
  Kredit Pajak PPh 23                36.600.000
  Peralatan Komputer                 70.000.000
  Akum. Penyusutan                  (15.625.000)
                                  ─────────────
  Total Aset                      1.379.834.240

KEWAJIBAN
  Hutang PPN                         69.300.000
  Hutang PPh 21                      16.350.000
                                  ─────────────
  Total Kewajiban                    85.650.000

EKUITAS
  Modal Disetor                     500.000.000
  Laba Berjalan                     794.184.240
                                  ─────────────
  Total Ekuitas                   1.294.184.240

                                  ═════════════
  Total Kewajiban + Ekuitas       1.379.834.240
```

**Persamaan akuntansi:** Aset = Kewajiban + Ekuitas. Selalu harus sama.

**Cara akses:** Laporan → Neraca → pilih tanggal

## 4. Laporan Arus Kas (Cash Flow)

Menampilkan pergerakan uang masuk dan keluar, dikelompokkan menjadi:

- **Operasional** — aktivitas utama bisnis (pendapatan, beban, gaji)
- **Investasi** — pembelian/penjualan aset tetap
- **Pendanaan** — setoran modal, pengambilan prive

**Cara akses:** Laporan → Arus Kas → pilih rentang tanggal

## Ekspor Laporan

Semua laporan bisa diekspor ke:
- **PDF** — untuk cetak atau kirim ke pihak ketiga
- **Excel** — untuk analisis lebih lanjut

Klik tombol **PDF** atau **Excel** di pojok kanan atas halaman laporan.

## Tips Membaca Laporan

1. **Cek Trial Balance dulu** — pastikan balance sebelum baca laporan lain
2. **Bandingkan antar bulan** — lihat tren naik/turun pendapatan dan beban
3. **Perhatikan HPP** — untuk usaha dagang/produksi, HPP menentukan margin kotor
4. **Cek hutang pajak** — pastikan tidak ada hutang pajak yang terlupakan

## Langkah Selanjutnya

- [Payroll](06-payroll.md) — bagaimana gaji karyawan mempengaruhi laporan
- [PPN](07-ppn.md) — bagaimana PPN muncul di neraca dan arus kas
