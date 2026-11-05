# Uji Mandiri Pajak

Kantor pajak menjalankan pemeriksaan silang dari SPT Tahunan dan mengirim SP2DK bila angkanya tidak cocok. Laporan uji mandiri menjalankan pemeriksaan yang sama lebih dulu. Setiap baris berisi angka buku, angka pajak, selisih, dan penjelasan yang diisi pengguna.

## Membuka Laporan

Buka **Pembelian > Uji Mandiri Pajak** (`/reports/self-test`), isi tahun, lalu klik **Tampilkan**.

Selisih = angka buku dikurangi angka pajak. Angka buku hanya menghitung jurnal terposting dan tidak menghitung jurnal penutup tahun.

| Status | Arti |
|---|---|
| Sesuai | Angka buku sama dengan angka pajak. |
| Ada selisih | Angka buku berbeda dari angka pajak. Isi penjelasan. |
| Ada mutasi | Baris tanpa angka pembanding di SPT, dan saldo bergerak. Isi penjelasan. |
| Tidak ada aktivitas | Kedua angka nol. |
| Belum dikonfigurasi | Belum ada akun dengan peran yang dibutuhkan baris tersebut. Pesan menyebut perannya. |

## Baris Pemeriksaan

| Baris | Angka buku | Angka pajak |
|---|---|---|
| Pendapatan vs DPP PPN, per masa | Kredit dikurangi debit pada akun bertipe pendapatan | DPP faktur keluaran bernomor pada transaksi terposting, menurut tanggal faktur |
| Beban gaji vs DPP PPh 21, per masa | Debit dikurangi kredit pada akun gaji template **Post Gaji Bulanan** | Penghasilan bruto PPh 21 payroll berstatus POSTED: gaji, bonus, THR, natura |
| Beban jasa/sewa vs DPP PPh 23 | Beban pada akun berperan Jasa/sewa objek PPh 23 | Bruto bukti potong PPh 23 yang dipotong perusahaan |
| Beban sewa tanah/bangunan vs DPP PPh 4(2) | Beban pada akun berperan Sewa tanah/bangunan | Bruto bukti potong PPh 4(2) yang dipotong perusahaan |
| Penambahan aset tetap vs register | Debit pada akun harga perolehan kategori aset | Harga perolehan aset yang dibeli tahun itu menurut register aset |
| Pengurangan aset tetap vs pelepasan | Kredit pada akun harga perolehan kategori aset | Harga perolehan aset yang dilepas tahun itu menurut register aset |
| Mutasi modal | Kredit dikurangi debit pada semua akun bertipe ekuitas | Tidak ada |
| Mutasi aset non-operasional | Debit dikurangi kredit pada akun berperan Aset non-operasional | Tidak ada |
| Beban lain-lain tanpa rincian | Beban pada akun berperan Beban lain-lain | Bagian yang berbukti atau di bawah ambang |

Baris per masa punya satu baris total tahun dan dua belas baris masa. Payroll yang belum berstatus POSTED tidak masuk DPP PPh 21; baris masanya menampilkan pesan status payroll.

Bukti potong PPh 23 dari pelanggan atas invoice tidak dihitung, karena pelanggan yang memotong.

Mutasi modal dan mutasi aset non-operasional tidak punya angka pembanding di SPT. Kantor pajak menanyakan sumber dana setoran modal dan pembelian aset di luar usaha, jadi seluruh mutasinya perlu dijelaskan, misalnya dengan nomor akta atau keputusan RUPS.

### Beban Lain-lain Tanpa Rincian

Satu baris jurnal dianggap tanpa rincian bila:

1. Baris itu mendebit akun berperan Beban lain-lain sebesar ambang atau lebih, dan
2. Transaksinya tidak punya keterangan, atau tidak punya bukti.

Bukti mengikuti [Kelengkapan Bukti](18-kelengkapan-bukti.md): lampiran yang sudah diberi jenis bukti, invoice yang diterbitkan aplikasi, nomor bukti potong, baris mutasi bank yang sudah dicocokkan, atau kontrak tertaut. Lampiran tanpa jenis bukti tidak dihitung.

Ambang tampil di bagian atas laporan. Nilainya diatur di `app.self-test.unitemised-threshold`; `application.properties` mengisinya Rp 5.000.000. Tanpa pengaturan ini aplikasi gagal _startup_.

## Peran Akun

Empat baris memilih akun dari kolom **Peran Uji Mandiri Pajak** di formulir akun (**Master Data > Bagan Akun**, ubah akun):

| Peran | Tipe akun | Dipakai oleh |
|---|---|---|
| Jasa/sewa objek PPh 23 | Beban | Beban jasa/sewa vs DPP PPh 23 |
| Sewa tanah/bangunan (PPh 4(2)) | Beban | Beban sewa tanah/bangunan vs DPP PPh 4(2) |
| Beban lain-lain | Beban | Beban lain-lain tanpa rincian |
| Aset non-operasional | Aset | Mutasi aset non-operasional |

Satu akun hanya punya satu peran, dan akun header tidak boleh diberi peran. Paket data bawaan memberi peran pada akun beban jasa (sama dengan kolom Akun Jasa), akun sewa gedung atau tempat, dan akun `Beban Operasional Lainnya`. Aset non-operasional tidak diberi peran secara bawaan.

## Penjelasan

Setiap baris, termasuk baris masa, punya kolom **Penjelasan**. Isi teks lalu klik **Simpan**. Penjelasan disimpan per tahun dan per baris, dengan nama pengguna dan waktu perubahan terakhir. Kosongkan teks lalu klik **Simpan** untuk menghapusnya.

Hak akses: laporan butuh izin `TAX_REPORT_VIEW`. Mengubah penjelasan butuh `FISCAL_PERIOD_MANAGE`. Pengguna tanpa izin itu melihat penjelasan tanpa formulir.

Laporan dan baris-baris pembentuk setiap angka juga tersedia lewat API `/api/self-test` dengan scope `self-test:read`.

## Lihat Juga

- [Perpajakan](04-perpajakan.md)
- [Kelengkapan Bukti](18-kelengkapan-bukti.md)
- [Pemeriksaan Tutup Buku](19-pemeriksaan-tutup-buku.md)
