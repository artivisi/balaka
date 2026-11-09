# Pemeriksaan Tutup Buku

Daftar periksa tutup buku menampilkan saldo dan jurnal yang perlu ditindaklanjuti sebelum periode ditutup. Daftar ini tidak menghalangi tutup bulan atau tutup tahun; setiap temuan harus diperiksa dan diselesaikan oleh akuntan.

## Daftar Periksa

Daftar periksa tampil di dua halaman:

| Halaman | Periode yang diperiksa |
|---|---|
| **Master Data > Periode Fiskal**, detail satu periode | Bulan periode tersebut |
| **Laporan**, kartu **Penutupan Tahun Buku** (`/reports/fiscal-closing`) | Satu tahun penuh, 1 Januari sampai 31 Desember |

Setiap pemeriksaan punya status:

| Status | Arti |
|---|---|
| Lolos | Tidak ada temuan. |
| Ada temuan | Tabel di bawahnya berisi referensi, tanggal, keterangan, dan jumlah. Nomor transaksi bisa diklik ke detail transaksi. |
| Tidak berlaku | Syarat pemeriksaan belum terpenuhi, misalnya SPT Tahunan belum tercatat dilaporkan. |
| Belum dikonfigurasi | Pengaturan yang dibutuhkan belum ada. Pesan menyebut pengaturan yang kurang. |

Hanya jurnal terposting yang dihitung. Draft dan transaksi batal tidak dihitung.

## Peran Akun

Tiga pemeriksaan memilih akun dari kolom **Peran Pemeriksaan Tutup Buku** di formulir akun (**Master Data > Bagan Akun**, ubah akun):

| Peran | Tipe akun | Dipakai oleh |
|---|---|---|
| Kredit pajak | Aset | Pemeriksaan kredit pajak setelah SPT Tahunan |
| Akun antara | Aset atau liabilitas | Pemeriksaan akun antara |
| Rekening koran pemegang saham/direksi | Aset atau liabilitas | Rekening koran pemegang saham dan peringatan hasil pelepasan aset |

Akun header tidak boleh diberi peran. Paket data bawaan memberi peran Kredit pajak pada `1.1.26 Kredit Pajak PPh 23` dan membuat akun `2.1.50 Rekening Koran Pemegang Saham/Direksi`. Akun antara tidak diberi peran secara bawaan karena akun ini berbeda di setiap perusahaan.

## Kredit Pajak Setelah SPT Tahunan

Pemeriksaan ini mengacu pada SPT Tahunan PPh Badan tahun sebelum periode yang diperiksa. Contoh: daftar periksa Maret 2026 atau tahun 2026 memeriksa SPT Tahunan 2025.

Syaratnya, SPT tersebut tercatat di Register Pelaporan Pajak (`/api/tax-filings`, lihat bab Perpajakan) dengan status Sudah Lapor, Terlambat, atau Dibetulkan, dan tanggal lapornya paling lambat tanggal akhir periode. Bila ada lebih dari satu (SPT asli dan pembetulan), tanggal lapor paling awal yang berlaku.

Untuk setiap akun berperan Kredit pajak, aplikasi menghitung saldo debit per 31 Desember tahun SPT, lalu mengurangkan mutasi kredit sejak 1 Januari tahun berikutnya sampai akhir periode. Sisa di atas nol menjadi temuan. Kredit pajak yang sudah dikreditkan di SPT harus dipindahkan ke akun utang PPh Badan atau akun lain dengan jurnal penyesuaian.

## Akun Antara

Akun berperan Akun antara menjadi temuan bila punya saldo pada akhir periode dan tidak punya mutasi pengurang selama periode. Mutasi pengurang adalah kredit untuk akun bersaldo debit dan debit untuk akun bersaldo kredit. Akun yang dikurangi sebagian tidak menjadi temuan.

## Liabilitas Tidak Bergerak

Semua akun bertipe liabilitas yang punya saldo pada akhir periode dan tidak punya jurnal terposting selama N bulan menjadi temuan. Batasnya: tanggal jurnal terakhir sama dengan atau sebelum tanggal akhir periode dikurangi N bulan.

N diambil dari ambang aturan **Liabilitas Tidak Bergerak** di **Laporan > Peringatan > Konfigurasi**. Nilai awal 6 bulan. Ambang harus bilangan bulat minimal 1; nilai lain atau aturan yang terhapus membuat pemeriksaan berstatus Belum dikonfigurasi. Aturan peringatan yang sama, bila aktif, mengirim peringatan harian dengan daftar akun per hari ini.

## Invoice Belum di Piutang

Invoice berstatus Terkirim, Sebagian, Lunas, atau Jatuh Tempo dengan tanggal invoice dalam periode diperiksa terhadap akun Piutang Usaha di **Pengaturan Perusahaan**. Jumlah debit ke akun piutang dari jurnal terposting yang bersumber dari invoice tersebut, sampai akhir periode, harus sama dengan total invoice (DPP ditambah PPN). Selisih menjadi temuan.

Temuan ini muncul bila jurnal pengakuan pendapatan invoice masih draft, bila jurnalnya bertanggal setelah periode, atau bila pendapatan hanya dicatat saat pembayaran diterima (basis kas). Laporan keuangan aplikasi berbasis akrual, jadi invoice yang terbit harus tercatat di piutang pada periode invoice.

Bila akun Piutang Usaha belum diatur, pemeriksaan berstatus Belum dikonfigurasi.

## Jurnal Periode Berikutnya

Pemeriksaan ini mencari jurnal terposting bertanggal setelah akhir periode untuk dokumen yang bertanggal dalam periode:

| Dokumen | Tanggal yang dibandingkan |
|---|---|
| Invoice | Tanggal invoice terhadap tanggal jurnal pengakuan pendapatannya |
| Tagihan vendor | Tanggal tagihan terhadap tanggal jurnalnya |
| Faktur pajak | Tanggal faktur pada detail pajak terhadap tanggal transaksinya |

Contoh: faktur pajak bertanggal 28 Maret yang dicatat pada transaksi 2 April muncul di daftar periksa Maret. Daftar periksa April tidak menampilkannya. Pembayaran invoice tidak diperiksa karena pembayaran memang terjadi setelah invoice.

## Hasil Pelepasan Aset

Pelepasan aset tetap dalam periode yang tidak dibatalkan dan punya hasil penjualan menjadi temuan bila akun penerimaannya:

- berperan Rekening koran pemegang saham/direksi, artinya dana diterima pemegang saham atau direksi, atau
- bukan akun GL dari rekening bank perusahaan yang aktif di halaman Rekening Bank (`/settings/bank-accounts`). Penerimaan ke akun Kas termasuk di sini.

Halaman catatan pelepasan menampilkan peringatan yang sama. Akun berperan Rekening koran pemegang saham/direksi boleh dipilih sebagai akun penerimaan walaupun bertipe liabilitas.

## Rekening Koran Pemegang Saham

Buka **Laporan > Rekening Koran Pemegang Saham**. Pilih akun berperan Rekening koran pemegang saham/direksi, isi tanggal awal dan akhir, lalu klik **Tampilkan**.

Laporan berisi saldo awal, setiap baris jurnal terposting dengan saldo berjalan, total debit dan kredit, dan saldo akhir. Saldo dihitung kredit dikurangi debit: saldo positif berarti perusahaan berutang kepada pemegang saham atau direksi, saldo negatif berarti pemegang saham atau direksi berutang kepada perusahaan. Gunakan satu akun untuk setiap pemegang saham atau direksi.

Hak akses: laporan ini butuh izin `REPORT_VIEW`. Daftar periksa di halaman periode fiskal butuh `FISCAL_PERIOD_VIEW`.

## Lihat Juga

- [Kelengkapan Bukti](18-kelengkapan-bukti.md)
- [Peringatan](15-peringatan.md)
- [Aset Tetap](03-aset-tetap.md)
