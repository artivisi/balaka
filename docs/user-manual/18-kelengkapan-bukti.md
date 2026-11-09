# Kelengkapan Bukti

Fitur ini menentukan dokumen yang wajib ada pada setiap transaksi terposting, menampilkan transaksi yang dokumennya kurang, menyimpan rekening koran bulanan, mencatat kontrak, dan menyusun paket audit tahunan.

## Aturan Bukti

Buka **Master Data > Aturan Bukti**. Satu aturan berisi:

| Kolom | Isi |
|---|---|
| Akun | Akun yang diperiksa. Aturan berlaku juga untuk semua sub-akunnya, jadi aturan pada akun induk `5.1` berlaku untuk `5.1.05`. |
| Template | Template yang diperiksa. Aturan berlaku untuk semua versi template tersebut. |
| Jenis Bukti | Kontrak, Invoice / Faktur, Bukti Pembayaran, atau Bukti Potong. |
| Nilai Minimum Transaksi | Aturan hanya berlaku bila nilai transaksi sama dengan atau lebih dari angka ini. Kosongkan untuk semua nilai. |

Isi akun atau template, tidak keduanya. Satu akun atau template hanya boleh punya satu aturan per jenis bukti. Tanpa aturan, tidak ada transaksi yang ditandai kurang bukti; aplikasi tidak membawa aturan bawaan.

Aturan berlaku untuk transaksi berstatus terposting. Draft, transaksi batal, dan jurnal penutup tahun tidak diperiksa.

Hak akses: membuat dan menghapus aturan butuh izin `EVIDENCE_CONFIG` (Admin, Owner). Melihat aturan butuh `EVIDENCE_VIEW`.

## Sumber Bukti

Satu jenis bukti terpenuhi bila salah satu sumber berikut ada:

| Jenis Bukti | Sumber |
|---|---|
| Semua jenis | Dokumen lampiran transaksi, lampiran baris jurnalnya, atau lampiran invoice-nya, dengan jenis bukti yang sesuai. |
| Invoice / Faktur | Invoice yang dibuat di aplikasi dan jurnalnya adalah transaksi ini. |
| Kontrak | Kontrak di register yang ditautkan ke transaksi atau ke invoice-nya, dan dokumen kontraknya sudah diunggah. |
| Bukti Potong | Nomor bukti potong pada detail pajak transaksi. |
| Bukti Pembayaran | Baris mutasi bank yang sudah dicocokkan dengan transaksi ini di Rekonsiliasi Bank. |

Tagihan vendor (bill) tidak dihitung sebagai invoice; unggah invoice dari vendor sebagai lampiran transaksi.

### Jenis Dokumen Lampiran

Pada halaman detail transaksi, bagian **Lampiran Dokumen** punya pilihan **Jenis bukti** saat unggah. Dokumen tanpa jenis tercatat sebagai *Belum diklasifikasikan* dan tidak memenuhi aturan apa pun. Jenis dokumen yang sudah ada dapat diubah dari pilihan di bawah nama dokumen. Pilihan **Lainnya** dipakai untuk dokumen yang bukan bukti wajib.

Bagian **Kelengkapan Bukti** di halaman yang sama menampilkan setiap bukti wajib, aturan yang mewajibkannya, dan status *Ada* atau *Belum ada*. Bagian ini diperbarui setiap kali dokumen diunggah, diubah jenisnya, atau dihapus.

## Laporan Kurang Bukti

Buka **Akuntansi > Kelengkapan Bukti**, isi tanggal awal dan akhir, lalu klik **Tampilkan**. Tabel berisi transaksi terposting pada periode tersebut yang masih kurang minimal satu bukti wajib, dengan kolom bukti wajib, bukti kurang, dan catatan. Catatan menyebut kontrak yang belum punya dokumen dan lampiran yang belum diklasifikasikan.

Baris ringkasan menyebut jumlah transaksi yang diperiksa, jumlah yang wajib bukti, jumlah yang kurang bukti, dan jumlah aturan.

## Register Kontrak

Buka **Akuntansi > Register Kontrak**, klik **Kontrak Baru**. Isi nomor kontrak, judul, satu pihak (klien atau vendor), tanggal mulai, dan bila ada tanggal berakhir dan nilai kontrak. Tanggal berakhir kosong berarti kontrak berlaku tanpa batas.

Di halaman detail kontrak:

1. **Unggah Dokumen Kontrak**. Kontrak baru terhitung sebagai bukti setelah dokumennya diunggah. Unggahan berikutnya menggantikan dokumen kontrak; dokumen lama tetap tersimpan.
2. **Invoice**: tautkan invoice klien dengan nomor invoice. Hanya invoice milik klien kontrak yang dapat ditautkan, dan satu invoice hanya ke satu kontrak.
3. **Transaksi**: tautkan transaksi dengan nomor transaksi, misalnya pembayaran sewa bulanan ke vendor. Satu transaksi hanya ke satu kontrak.

Pihak kontrak tidak dapat diubah selama ada invoice yang tertaut. Menambah dan mengubah kontrak butuh izin `EVIDENCE_MANAGE` (Admin, Owner, Accountant).

## Arsip Rekening Koran

Buka **Akuntansi > Arsip Rekening Koran**. Tabel menampilkan setiap rekening bank aktif dan dua belas bulan tahun yang dipilih:

| Tanda | Arti |
|---|---|
| Ada | Rekening koran bulan ini sudah diarsipkan. |
| Kurang | Bulan sudah jatuh tempo dan belum ada arsip. |
| - | Bulan belum jatuh tempo. |
| · | Belum ada aktivitas: bulan sebelum jurnal terposting pertama pada akun bank tersebut dan sebelum arsip pertamanya. |

Bulan jatuh tempo setelah sejumlah hari dari akhir bulan. Jumlah hari ini adalah ambang aturan peringatan **Rekening Koran Belum Diarsipkan** di **Laporan > Peringatan**, nilai awal 10 hari.

Untuk mengarsipkan, pilih rekening bank, bulan, dan file PDF atau gambar, lalu klik **Arsipkan**. Bulan yang belum berakhir ditolak. Mengunggah ulang bulan yang sudah ada menjadikan file baru aktif; file lama tetap tersimpan dengan status *Diganti*.

Hasil impor mutasi CSV di Rekonsiliasi Bank tidak dihitung sebagai arsip, karena aplikasi tidak menyimpan file aslinya.

### Masa Simpan

Setiap arsip disimpan 10 tahun sejak akhir bulan rekening koran. Kolom **Disimpan Sampai** menampilkan tanggal tersebut. Sebelum tanggal itu, arsip dan dokumennya tidak dapat dihapus dari halaman mana pun. Setelah tanggal itu, tombol **Hapus** muncul. Dasar masa simpan adalah UU KUP Pasal 28 ayat (11); angka ini belum diverifikasi dan diatur di `app.evidence.retention-years`.

Rekening bank nonaktif tidak diperiksa.

## Paket Audit

Buka **Laporan > Paket Audit**, isi tahun, lalu klik **Unduh Paket Audit**. File ZIP berisi:

| File | Isi |
|---|---|
| `README.txt` | Tahun, perusahaan, pembuat, jumlah transaksi dan yang kurang bukti, jumlah dan ukuran dokumen. |
| `buku-besar-<tahun>.csv` | Baris jurnal terposting per akun. |
| `neraca-saldo-<tahun>.xlsx` | Neraca saldo per 31 Desember. |
| `laporan-keuangan-<tahun>.pdf` | Neraca dan laba rugi tanpa jurnal penutup. |
| `indeks-bukti-<tahun>.csv` | Setiap transaksi terposting: bukti wajib, bukti yang ada, bukti kurang, dokumen beserta checksum SHA-256, catatan. |
| `arsip-rekening-koran-<tahun>.csv` | Arsip rekening koran tahun tersebut, termasuk yang sudah diganti. |
| `register-kontrak-<tahun>.csv` | Kontrak yang berlaku pada tahun tersebut. |

Centang **Sertakan file dokumen** untuk menambahkan file lampiran, rekening koran, dan kontrak di folder `dokumen/`. Bila total ukuran melebihi batas `app.audit-pack.max-document-bytes` (500 MB), unduhan ditolak dengan pesan ukurannya; unduh tanpa file dokumen dan cocokkan dokumen dengan checksum di indeks.

Paket audit butuh izin `REPORT_EXPORT` (Admin, Owner, Accountant, Auditor).

## Peringatan Rekening Koran

Aturan peringatan **Rekening Koran Belum Diarsipkan** memeriksa semua rekening bank aktif setiap hari. Satu peringatan menyebut setiap rekening yang kurang beserta bulannya; lebih dari enam bulan diringkas menjadi jumlah bulan, bulan paling awal, dan bulan paling akhir. Ubah ambang hari atau matikan aturan di **Laporan > Peringatan > Konfigurasi**.

## Lihat Juga

- [Rekonsiliasi Bank](14-rekonsiliasi-bank.md)
- [Peringatan](15-peringatan.md)
- [Faktur & Tagihan](16-faktur-tagihan.md)
