# Pelaporan

Akhir tahun memerlukan beberapa laporan: laporan keuangan untuk yayasan, analisis piutang SPP, dan penyiapan data SPT Tahunan.

Untuk konsep umum, lihat [Panduan Tutup Bulanan](../common/11-monthly-closing.md), [Panduan Tutup Tahun](../common/12-year-end-closing.md), dan [Coretax Export](../common/13-coretax-export.md).

## Tutup Bulan — Checklist Kampus

Setiap bulan, lakukan langkah-langkah berikut:

### Minggu 1 Bulan Berikutnya
- [ ] Catat semua pembayaran SPP yang diterima bulan lalu
- [ ] Catat semua pengeluaran bulan lalu (listrik, air, internet, dll)

### Tanggal 10
- [ ] **Setor PPh 21** — jumlah dari payroll bulan lalu

### Akhir Bulan
- [ ] Jalankan payroll bulan ini
- [ ] Bayar gaji (Bayar Hutang Gaji)
- [ ] Bayar BPJS (Bayar Hutang BPJS)
- [ ] Post penyusutan bulan ini (mulai April, setelah pembelian server)
- [ ] Catat beban admin bank
- [ ] **Tutup periode** bulan lalu

Karena STMIK non-PKP, tidak ada PPN yang perlu disetor.

## Laporan Laba Rugi (Tahun 2025)

Buka **Laporan - Laba Rugi** (periode: 1 Jan - 31 Des 2025):

```
PENDAPATAN OPERASIONAL
  Pendapatan SPP                   4.500.000.000
  Pendapatan Uang Pangkal         1.800.000.000
  Pendapatan Biaya Praktikum        360.000.000
                                  ─────────────
  Total Pendapatan Operasional    6.660.000.000

PENDAPATAN LAIN-LAIN
  Pendapatan Hibah                   500.000.000
                                  ─────────────
  Total Pendapatan                7.160.000.000

BEBAN USAHA
  Beban Gaji Karyawan               768.000.000
  Beban BPJS Karyawan                78.643.200
  Beban Listrik                      97.500.000
  Beban Telepon & Internet           60.000.000
  Beban Keamanan                     36.000.000
  Beban Pemeliharaan Gedung          33.000.000
  Beban Kebersihan                   30.000.000
  Beban Air                          24.800.000
  Beban Beasiswa Tidak Mampu         25.000.000
  Beban Beasiswa Prestasi            15.000.000
  Beban Pemeliharaan Lab             13.000.000
  Beban Bahan Praktikum               9.500.000
  Beban ATK                           8.000.000
  Beban Penyusutan                    7.291.667
                                  ─────────────
  Total Beban Usaha               1.177.734.867

BEBAN LUAR USAHA
  Beban Admin Bank                       300.000
                                  ─────────────

SURPLUS (LABA BERSIH)             5.981.965.133
```

Surplus yang besar (5,98M) terutama berasal dari pendapatan uang pangkal angkatan 2025 (1,8M) dan hibah MBKM (500M). Tahun tanpa penerimaan maba baru akan memiliki surplus yang lebih kecil.

## Neraca (31 Desember 2025, Sebelum Jurnal Penutup)

```
ASET
  Bank BCA                        5.968.944.800
  Piutang SPP Mahasiswa             160.000.000
  Aset Tetap (Server Lab)           35.000.000
  Akum. Penyusutan                   (7.291.667)
                                  ─────────────
  Total Aset                      6.156.653.133

KEWAJIBAN
  Hutang PPh 21                      2.688.000
                                  ─────────────
  Total Kewajiban                     2.688.000

EKUITAS
  Modal Yayasan                     200.000.000
  Surplus Tahun Berjalan          5.981.965.133
                                  ─────────────
  Total Ekuitas                   6.181.965.133
  (catatan: -28.000.000 dari beasiswa
   mengurangi piutang bukan kas)

  Total Kewajiban + Ekuitas       6.157.805.133  ✓
```

## Analisis Piutang SPP

Piutang SPP outstanding di akhir tahun: **Rp 160.000.000**

| Angkatan | Tagihan Ganjil | Dibayar | Beasiswa | Sisa |
|----------|---------------|---------|----------|------|
| 2023 | 900.000.000 | 900.000.000 | — | 0 |
| 2024 | 900.000.000 | 700.000.000 | — | 200.000.000 |
| 2025 | 900.000.000 | 900.000.000 | — | 0 |

Angkatan 2024 memiliki tunggakan Rp 200.000.000 yang dikurangi beasiswa Rp 40.000.000, menyisakan piutang Rp 160.000.000.

Tindak lanjut yang diperlukan:
- Kirim surat tagihan ke mahasiswa penunggak
- Pertimbangkan pemberian keringanan (cicilan tambahan)
- Laporkan status piutang ke yayasan

Piutang SPP yang terlalu lama outstanding perlu dievaluasi apakah masih tertagih. Jika tidak tertagih, pertimbangkan untuk dihapuskan sebagai piutang tak tertagih (bad debt).

## Laporan ke Yayasan

Yayasan memerlukan laporan berkala yang mencakup:

### Laporan Semester

1. **Realisasi penerimaan SPP** — bandingkan tagihan vs pembayaran per angkatan
2. **Tingkat kolektibilitas** — berapa persen mahasiswa yang sudah membayar lunas
3. **Penggunaan hibah** — rincian penggunaan dana hibah Kemendikbud
4. **Realisasi beasiswa** — jumlah penerima dan total beasiswa

### Laporan Tahunan

1. **Laporan Laba Rugi** — surplus/defisit tahun berjalan
2. **Neraca** — posisi keuangan per 31 Desember
3. **Rincian piutang** — status penagihan per angkatan
4. **Perbandingan anggaran vs realisasi** — jika yayasan menetapkan RKAT

## Tutup Tahun

### Langkah 1: Pastikan Semua Bulan Ditutup

Buka **Periode Fiskal** dan pastikan 12 bulan berstatus **MONTH_CLOSED**.

### Langkah 2: Jurnal Penutup

Buka **Laporan - Tutup Buku Tahun** dan klik **Eksekusi**.

Jurnal penutup menutup semua akun pendapatan dan beban:
- Semua pendapatan (4.x) menjadi saldo 0
- Semua beban (5.x) menjadi saldo 0
- Selisih masuk ke **Laba Ditahan**: 5.981.965.133

## SPT Tahunan untuk Lembaga Pendidikan

STMIK Merdeka Digital tetap wajib melaporkan SPT Tahunan Badan meskipun berstatus non-PKP. Beberapa perbedaan dengan perusahaan PKP:

| Aspek | Perusahaan PKP | Kampus Non-PKP |
|-------|---------------|----------------|
| PPN | Memungut dan menyetor PPN | Tidak ada PPN |
| PPh 23 | Dipotong oleh klien | Tidak ada (bukan jasa kena pajak) |
| Kredit Pajak | PPh 23, PPh 25 | Hanya PPh 25 (jika ada) |
| Koreksi Fiskal | Beban entertainment, dll | Beban beasiswa (cek deductibility) |

### Rekonsiliasi Fiskal

Beberapa beban kampus perlu dievaluasi deductibility-nya:

| Beban | Status Fiskal | Keterangan |
|-------|--------------|-----------|
| Gaji + BPJS | Deductible | Beban gaji yang wajar |
| Listrik, air, internet | Deductible | Operasional kampus |
| Beasiswa | Cek regulasi | Beasiswa yang memenuhi syarat PP 93/2010 bisa deductible |
| Pemeliharaan gedung | Deductible | Jika ada bukti pendukung |

### Export Coretax

Buka **Laporan - SPT Tahunan** untuk data yang siap diinput ke Coretax. Lihat [Panduan Coretax Export](../common/13-coretax-export.md) untuk langkah detailnya.

## Catatan untuk BAN-PT

Auditor BAN-PT memerlukan bukti pengelolaan keuangan yang baik. Pastikan:

1. **Semua transaksi tercatat** — tidak ada transaksi off-book
2. **Bukti pendukung lengkap** — kwitansi, invoice, bukti transfer
3. **Laporan keuangan teraudit** — minimal oleh auditor internal yayasan
4. **Transparansi SPP** — rincian penggunaan dana SPP bisa dipertanggungjawabkan
5. **Piutang terkontrol** — tingkat kolektibilitas SPP di atas 90%

Kolektibilitas SPP tahun 2025: (4.300M + 40M beasiswa) / 4.500M = **96,4%** — baik.
