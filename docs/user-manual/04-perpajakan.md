# Perpajakan

Panduan pencatatan transaksi pajak dan laporan untuk kepatuhan perpajakan Indonesia.

## Jenis Pajak di Indonesia

### Pajak yang Dikelola Aplikasi

| Pajak | Tarif | Kewajiban |
|-------|-------|-----------|
| **PPN** | Nominal 12%, efektif 11% (DPP Nilai Lain) | PKP yang menyerahkan BKP/JKP |
| **PPh 21** | Progresif | Pemberi kerja (pemotongan gaji) |
| **PPh 23** | 2% (jasa), 15% (dividen) | Pemberi penghasilan |
| **PPh 25** | Angsuran | Wajib pajak badan |
| **PPh 4(2)** | Final (varies) | Transaksi tertentu |

---

## Transaksi PPN

### Konsep PPN (DPP Nilai Lain — PMK 131/2024)

**PPN Keluaran** - PPN yang Anda pungut dari pembeli
**PPN Masukan** - PPN yang Anda bayar ke penjual

```
Net PPN = PPN Keluaran - PPN Masukan
```

Sejak 1 Januari 2025, tarif PPN secara nominal naik menjadi 12%, namun menggunakan DPP Nilai Lain (PMK 131/2024) sehingga beban efektif tetap 11%:

- **DPP** = Harga Jual × 11/12 (DPP Nilai Lain)
- **PPN** = DPP × 12% = Harga Jual × 11/12 × 12% = **Harga Jual × 11%**
- Field `amount` di template = **Harga Jual** (sebelum PPN)
- Formula template: `amount * 0.11` = PPN efektif 11% dari Harga Jual

> **Mengapa formula 0.11 (bukan 0.12)?** Tarif resmi PPN adalah 12%, tetapi DPP dihitung dari 11/12 × Harga Jual (PMK 131/2024 Pasal 3). Hasil akhirnya: PPN = 11% × Harga Jual. Di Faktur Pajak Coretax, DPP dan PPN 12% ditampilkan terpisah, tetapi secara akuntansi cukup catat PPN = `amount * 0.11`.

### Mencatat Pendapatan dengan PPN

1. Buka menu **Transaksi** > **Transaksi Baru**

![Form Transaksi](screenshots/transactions-form.png)

2. Pilih template **Pendapatan Jasa + PPN**
3. Isi Harga Jual (sebelum PPN), contoh: Rp 10.000.000
4. Preview jurnal:
   ```
   Dr. Bank                    11.100.000  (amount * 1.11)
       Cr. Hutang PPN              1.100.000  (amount * 0.11)
       Cr. Pendapatan             10.000.000  (amount)
   ```
5. Klik **Simpan & Posting**

### Mencatat Pembelian dengan PPN Masukan

1. Pilih template **Pembelian dengan PPN**
2. Isi Harga Jual (sebelum PPN), contoh: Rp 10.000.000
3. Preview jurnal:
   ```
   Dr. Beban/Aset             10.000.000  (amount)
   Dr. PPN Masukan             1.100.000  (amount * 0.11)
       Cr. Bank                   11.100.000  (amount * 1.11)
   ```

### Pendapatan Jasa BUMN (FP 03)

Untuk klien BUMN/Pemerintah yang menggunakan Faktur Pajak kode 03 (PPN dipungut pembeli):

1. Pilih template **Pendapatan Jasa BUMN (FP 03)**
2. Isi Harga Jual, contoh: Rp 10.000.000
3. Preview jurnal:
   ```
   Dr. Bank                     9.800.000  (amount * 0.98)
   Dr. Kredit PPh 23              200.000  (amount * 0.02)
       Cr. Pendapatan            10.000.000  (amount)
   ```
4. PPN tidak masuk jurnal karena dipungut dan disetor oleh pembeli (BUMN)

### Laporan Ringkasan PPN

Buka menu **Laporan** > **Ringkasan PPN**.

![Ringkasan PPN](screenshots/reports-ppn-summary.png)

---

## Transaksi PPh

### PPh 23 - Pemotongan atas Jasa

Saat membayar jasa ke vendor (selain pegawai):

1. Pilih template **Bayar Jasa + PPh 23**
2. Isi nilai bruto
3. Sistem menghitung PPh 23 (2%)
4. Preview jurnal:
   ```
   Dr. Beban Jasa             10.000.000
       Cr. Hutang PPh 23           200.000
       Cr. Bank                  9.800.000
   ```

### PPh 21 - Pemotongan Gaji

Lihat bagian [Penggajian](05-penggajian.md) untuk detail PPh 21 karyawan.

### Laporan PPh

Buka menu **Laporan** > **Bukti Potong PPh 23**.

![Bukti Potong PPh 23](screenshots/reports-pph23-withholding.png)

### Ringkasan Pajak

Buka menu **Laporan** > **Ringkasan Pajak**.

![Ringkasan Pajak](screenshots/reports-tax-summary.png)

---

## Pemotongan PPh atas Pembayaran

Menu **Pembelian** > **Pemotongan PPh** mencatat pembayaran ke freelancer, vendor dan penerima luar negeri. Sistem menghitung bruto, PPh dan jumlah yang dibayar, mencatat hutang PPh, lalu memantau setoran per masa.

### Tarif

| Perlakuan | Penerima | Dasar | Tarif | Tanpa NPWP/NIK |
|-----------|----------|-------|-------|----------------|
| PPh 21 Bukan Pegawai | Orang pribadi | 50% bruto per pembayaran | Pasal 17 | Lebih tinggi 20% |
| PPh 23 | Badan atau orang pribadi | Bruto | 2% | Lebih tinggi 100% |
| PPh 26 | Luar negeri | Bruto | 20% atau tarif P3B | Tidak berpengaruh |

Tarif dan kode objek pajak dicocokkan dengan PMK 168/2023, PMK 81/2024 dan daftar kode objek pajak e-Bupot per 6 November 2026, belum diperiksa konsultan pajak.

Tarif berjenjang Pasal 17 dihitung dari bruto setiap pembayaran, tidak diakumulasikan dengan pembayaran lain ke penerima yang sama (PMK 168/2023). PTKP tidak dikurangkan. Contoh dengan NIK: dua pembayaran Rp 100.000.000 pada bulan yang sama masing-masing dipotong Rp 2.500.000.

Tarif P3B berlaku hanya jika nomor DGT terisi dan DGT masih berlaku pada tanggal bayar. Jika tidak, sistem menghitung tarif umum 20% dan kolom Dasar Tarif menampilkan alasannya.

### Data Penerima

Buka **Pembelian** > **Vendor**, lalu isi bagian Informasi Pajak:

1. **Jenis Penerima**: Badan dalam negeri, Orang pribadi dalam negeri, atau Penerima luar negeri
2. **Perlakuan Pajak Bawaan** dan **Kode Objek Pajak Bawaan**
3. **NPWP** atau **NIK**. Untuk penerima luar negeri: **Kode Negara**, **TIN Luar Negeri**, **Tarif P3B**, **Nomor Formulir DGT**, **DGT Berlaku Sampai**

NPWP dan NIK vendor tersimpan terenkripsi.

### Mencatat Pembayaran

1. Klik **Pembayaran Baru**
2. Pilih penerima, isi tanggal bayar, jumlah bruto, akun beban, akun bank dan keterangan
3. Klik **Hitung dan Simpan Draf**
4. Periksa PPh Dipotong dan Dibayar ke Penerima, lalu klik **Posting**

Draf belum masuk jurnal. **Batalkan Draf** menghapus draf tanpa mengubah pembayaran lain. Pembayaran terposting dibatalkan lewat void transaksi.

Jurnal setelah posting:

```
Dr. Beban Jasa Profesional  10.000.000
    Cr. Bank                 9.750.000
    Cr. Hutang PPh 21          250.000
```

Akun hutang dicari berdasarkan nama: **Hutang PPh 21**, **Hutang PPh 23**, **Hutang PPh 26**. Jika nama tersebut tidak ada atau ganda, penyimpanan draf ditolak.

### Melunasi Tagihan dengan Pemotongan

Pada form pembayaran, kosongkan Akun Beban dan isi **Nomor Tagihan**, **Jumlah Pelunasan** (termasuk PPN) dan **Metode Pembayaran**. Jumlah Bruto tetap dasar pemotongan, tanpa PPN. Jurnal mendebit Hutang Usaha. Status tagihan berubah saat draf diposting.

### Setoran dan Jatuh Tempo

Pilih masa pada halaman **Pemotongan PPh**. Tabel Status Setor menampilkan PPh dipotong, disetor, sisa, tanggal jatuh tempo dari Kalender Pajak dan statusnya. Hanya transaksi terposting yang dihitung; nilai draf tampil di kolom tersendiri.

Untuk mencatat setoran, isi form Setoran, klik **Simpan Draf Setoran**, lalu **Posting**. Tanggal jatuh tempo belum digeser ke hari kerja berikutnya.

Setoran di menu ini hanya untuk PPh hasil pemotongan di menu ini. PPh 21 karyawan disetor lewat template Setor PPh 21.

### Ekspor

- **Ekspor BP21**: PPh 21 bukan pegawai
- **Ekspor Bupot Unifikasi**: PPh 23, PPh 26 dan PPh 4(2). PPh 21 tidak lagi masuk ekspor ini

Format kolom belum dicocokkan dengan konverter Coretax terbaru.

### Pembayaran Jasa Tanpa Detail PPh

Bagian ini menampilkan transaksi terposting pada akun jasa yang tidak punya detail PPh 21, 23, 26 atau 4(2) pada masa terpilih. Detail PPN tidak dihitung.

Akun jasa diatur di **Bagan Akun**, kolom **Akun Jasa (Pemotongan PPh)**. Akun beban yang belum diklasifikasi dan punya transaksi pada masa itu muncul sebagai peringatan, karena laporan tidak bisa menilainya.

---

## Rantai Pajak Penjualan

Menu **Pembelian** > **Rantai Pajak Penjualan** dan bagian **Faktur dan Bukti Potong** pada halaman invoice menghubungkan invoice dengan faktur, penerimaan dan bukti potong PPh 23. Nomor faktur dan nomor bukti potong hanya tersimpan di detail pajak transaksi. Detail pajak ikut ke invoice melalui transaksinya.

### Mencatat Faktur

Pada halaman invoice yang sudah terkirim, klik **Catat Faktur**, isi nomor, tanggal, kode transaksi, DPP dan PPN, lalu klik **Simpan Faktur**. Faktur menempel pada transaksi pendapatan pertama dari invoice. Invoice dengan beberapa akun pendapatan tetap memiliki satu faktur. Faktur kedua lewat form ini ditolak.

### Mencatat Penerimaan dengan PPh 23

Jika pelanggan memotong PPh 23, isi **Jumlah** dengan piutang yang lunas, termasuk PPh 23, dan isi **PPh 23 Dipotong Pelanggan** dengan potongannya. Contoh: tagihan Rp 11.100.000, uang masuk Rp 10.900.000, potongan Rp 200.000. Isi Jumlah 11.100.000 dan PPh 23 Dipotong Pelanggan 200.000. Invoice menjadi lunas.

Form ini belum membuat jurnal, sama seperti sebelumnya. Uang masuk tetap dicatat dengan template **Terima Pelunasan Piutang** sebesar Rp 10.900.000.

### Mencatat Bukti Potong

Bukti potong biasanya datang setelah uang masuk. Klik **Catat Bukti Potong**, pilih penerimaan, isi nomor, tanggal, kode objek pajak, bruto, tarif dan PPh, lalu klik **Simpan sebagai Draf**. Sistem membuat draf transaksi dengan template **Pemotongan PPh 23 oleh Pelanggan**. Setelah diposting:

```
Dr. Kredit Pajak PPh 23    200.000
    Cr. Piutang Usaha       200.000
```

Tanggal bukti potong menjadi tanggal transaksi dan menentukan masa kredit pajak. Bukti potong dari pelanggan masuk Lampiran III dan tidak masuk Ekspor Bupot Unifikasi.

Akun dicari berdasarkan nama **Kredit Pajak PPh 23**. Jika nama tersebut tidak ada atau ganda, penyimpanan ditolak.

### Pengecualian

Daftar pengecualian menampilkan semua pengecualian yang tanggalnya sampai akhir masa terpilih. Pengecualian masa lama tetap tampil sampai diperbaiki.

| Jenis | Muncul jika | Tidak muncul jika | Cara Menyelesaikan |
|-------|-------------|-------------------|--------------------|
| Dibayar tanpa faktur | Invoice ber-PPN sudah dibayar sebagian atau lunas dan belum punya faktur bernomor | Invoice tanpa PPN, belum dibayar, atau dibatalkan | Catat faktur |
| Faktur tanpa pendapatan terposting | Pendapatan invoice atau transaksi masih draf, sudah di-void, atau transaksinya tidak punya akun pendapatan | Sebagian pendapatan invoice sudah terposting | Posting pendapatan. Jika faktur sudah dibatalkan di Coretax, hapus detail pajaknya |
| Lebih dari satu faktur pada satu invoice | Satu invoice punya dua faktur atau lebih pada transaksi yang tidak di-void | Faktur lama berada pada transaksi yang sudah di-void | Hapus detail pajak yang salah |
| Bukti potong belum ada | PPh 23 pada penerimaan belum tertutup bukti potong, detail PPh 23 pada transaksi pendapatan belum bernomor, atau akun Kredit Pajak PPh 23 didebit tanpa detail PPh 23 | Bukti potong masih draf, atau detail PPh 23 berasal dari pembayaran ke vendor | Catat bukti potong atau isi nomornya |

Bukti potong yang transaksinya di-void tidak dihitung. Tidak ada tombol untuk mengabaikan pengecualian.

### Rekonsiliasi Bulanan

Tabel pertama menjelaskan selisih pendapatan terposting dan DPP SPT Masa PPN pada masa terpilih. DPP SPT Masa PPN adalah jumlah DPP faktur bernomor pada transaksi terposting dengan tanggal faktur pada masa tersebut.

| Baris | Isi |
|-------|-----|
| PPN ikut terbukukan sebagai pendapatan | Pendapatan lebih besar dari DPP tepat sebesar PPN faktur |
| PPh 23 mengurangi pendapatan | Pendapatan lebih kecil dari DPP tepat sebesar PPh 23 |
| Pendapatan tanpa faktur | Pendapatan tanpa faktur di masa mana pun. Pendapatan bunga juga masuk baris ini |
| Pendapatan masa ini, faktur di masa lain | Faktur bertanggal di masa lain |
| Faktur masa ini, pendapatan di masa lain | Pendapatan terposting di masa lain |
| Faktur tanpa pendapatan terposting | Transaksi terposting tanpa akun pendapatan |
| Selisih DPP Nilai Lain | Faktur kode 04 |
| Selisih lain | Selisih yang tidak cocok dengan semua baris di atas |

Faktur pada transaksi draf belum masuk DPP dan muncul sebagai pengecualian.

Tabel kedua menampilkan efek PPN dan PPh 23: DPP ditambah PPN faktur, dikurangi PPh 23 bukti potong, serta selisih PPN dan PPh 23 terhadap buku besar.

Jika Ekspor e-Faktur memuat DPP yang berbeda, halaman menampilkan peringatan. Ekspor memilih baris berdasarkan tanggal transaksi dan memuat baris tanpa nomor faktur.

Status SPT Masa PPN diambil dari Register Pelaporan Pajak. Nilai DPP pada SPT yang dilaporkan ke DJP tidak tersimpan di aplikasi, sehingga angka ini berasal dari data aplikasi.

---

## Periode Fiskal

Periode fiskal mengontrol kapan transaksi dapat diposting. Setiap bulan memiliki status yang menentukan apakah jurnal dapat dibuat di bulan tersebut.

### Melihat Periode Fiskal

Buka menu **Pengaturan** > **Periode Fiskal** di sidebar.

![Daftar Periode Fiskal](screenshots/fiscal-periods-list.png)

Fitur daftar periode:
- **Filter tahun** — pilih tahun untuk melihat 12 periode
- **Filter status** — tampilkan semua atau filter berdasarkan status tertentu

### Generate Periode Satu Tahun

Untuk membuat 12 periode sekaligus:

1. Isi tahun pada kolom **Tahun** di bagian atas halaman
2. Klik **Generate**
3. Sistem membuat periode Januari–Desember untuk tahun tersebut (periode yang sudah ada dilewati)

Atau buat periode satuan via **Periode Baru** (pilih tahun dan bulan manual).

### Status Periode

| Status | Label | Arti |
|--------|-------|------|
| OPEN | Terbuka | Transaksi dapat diposting ke periode ini |
| MONTH_CLOSED | Tutup Bulan | Transaksi tidak dapat diposting; periode masih bisa dibuka kembali |
| TAX_FILED | SPT Dilaporkan | SPT sudah dilaporkan ke DJP; periode tidak bisa dibuka kembali |

Alur status:

```
OPEN → MONTH_CLOSED → TAX_FILED
```

### Detail Periode

Klik periode di daftar untuk melihat detail:
- Tanggal mulai dan berakhir
- Siapa dan kapan menutup bulan
- Siapa dan kapan melaporkan SPT
- Visualisasi alur status (3 tahap)

### Menutup Periode

1. Buka detail periode yang berstatus **Terbuka**
2. Klik **Tutup Bulan**
3. Konfirmasi pada dialog

Setelah ditutup, sistem memblokir posting transaksi ke periode tersebut. Jika ada transaksi draf yang belum diposting di periode tersebut, penutupan akan ditolak.

### Melaporkan SPT

Setelah SPT Masa sudah dilaporkan ke DJP:

1. Buka detail periode yang berstatus **Tutup Bulan**
2. Klik **Lapor SPT**
3. Konfirmasi pada dialog

Periode dengan status **SPT Dilaporkan** tidak bisa dibuka kembali.

### Membuka Kembali Periode

Periode dengan status **Tutup Bulan** dapat dibuka kembali:

1. Buka detail periode
2. Klik **Buka Kembali**
3. Konfirmasi pada dialog

Periode dengan status **SPT Dilaporkan** tidak bisa dibuka kembali.

### Posting Guard

Saat pengguna mencoba memposting transaksi ke bulan yang sudah ditutup, sistem menampilkan pesan error dan transaksi tidak akan diposting. Pastikan periode dalam status **Terbuka** sebelum memposting transaksi.

---

## Kalender Pajak

### Melihat Kalender Pajak

Buka menu **Pajak** > **Kalender Pajak**.

![Kalender Pajak](screenshots/tax-calendar.png)

### Deadline Pajak Standar

| Pajak | Setor | Lapor |
|-------|-------|-------|
| PPN | Akhir bulan berikutnya | Akhir bulan berikutnya |
| PPh 21 | Tgl 15 bulan berikutnya | Tgl 20 bulan berikutnya |
| PPh 23 | Tgl 15 bulan berikutnya | Tgl 20 bulan berikutnya |
| PPh 4(2) | Tgl 15 bulan berikutnya | Tgl 20 bulan berikutnya |
| PPh 25 | Tgl 15 bulan berikutnya | Tgl 20 bulan berikutnya |

> **Catatan:** Deadline penyetoran PPh 21, PPh 23, dan PPh 4(2) berubah dari tanggal 10 menjadi tanggal 15 sesuai PMK 81/2024.

### Kalender Tahunan

![Kalender Pajak Tahunan](screenshots/tax-calendar-yearly.png)

### Menandai Selesai

1. Klik deadline yang sudah diselesaikan
2. Isi nomor bukti setor/lapor
3. Klik **Selesai**

---

## Referensi Regulasi

### Tarif PPh 21 (TER 2024)

| PKP Tahunan | Tarif |
|-------------|-------|
| s.d. Rp 60.000.000 | 5% |
| > Rp 60.000.000 - Rp 250.000.000 | 15% |
| > Rp 250.000.000 - Rp 500.000.000 | 25% |
| > Rp 500.000.000 - Rp 5.000.000.000 | 30% |
| > Rp 5.000.000.000 | 35% |

### PTKP (Penghasilan Tidak Kena Pajak)

| Status | PTKP/Tahun |
|--------|------------|
| TK/0 | Rp 54.000.000 |
| K/0 | Rp 58.500.000 |
| K/1 | Rp 63.000.000 |
| K/2 | Rp 67.500.000 |
| K/3 | Rp 72.000.000 |

### Tarif PPh 23

| Objek | Tarif |
|-------|-------|
| Dividen | 15% |
| Bunga | 15% |
| Royalti | 15% |
| Jasa (umum) | 2% |
| Sewa (selain tanah/bangunan) | 2% |

---

## Koreksi Fiskal

Koreksi fiskal (fiscal adjustments) adalah penyesuaian atas laba komersial untuk menghitung Penghasilan Kena Pajak (PKP) pada SPT Tahunan Badan. Ada dua kategori koreksi:

- **Beda Tetap (Permanent)** — biaya yang secara permanen tidak diakui pajak (contoh: denda pajak, sumbangan, biaya tanpa bukti potong)
- **Beda Waktu (Temporary)** — perbedaan waktu pengakuan antara akuntansi komersial dan fiskal (contoh: penyusutan metode berbeda)

### Mengelola Koreksi Fiskal via Web

Buka menu **Laporan** > **Rekonsiliasi Fiskal**. Di halaman ini:

1. Pilih tahun pajak
2. Klik **Tambah Koreksi** untuk menambah item baru
3. Isi form: deskripsi, kategori (Beda Tetap/Beda Waktu), arah (Positif/Negatif), jumlah, kode akun, dan catatan
4. Klik **Simpan**
5. Untuk menghapus, klik ikon hapus pada baris koreksi

Hasil koreksi fiskal ditampilkan dalam laporan rekonsiliasi fiskal dan digunakan dalam perhitungan PPh Badan.

### Perlakuan Fiskal Akun

Setiap akun beban yang bukan header memiliki atribut **Perlakuan Fiskal** pada form akun (**Master Data** > **Bagan Akun**):

| Nilai | Dampak pada draf koreksi |
|-------|--------------------------|
| Dapat dikurangkan | Tidak ada draf |
| Tidak dapat dikurangkan | Draf sebesar saldo akun setahun |
| Bersyarat | Draf sebesar saldo akun setahun dikurangi natura yang punya penerima |
| Belum diklasifikasi | Tidak ada draf. Akun bersaldo tampil sebagai peringatan |

Akun lama pada basis data yang sudah berjalan berstatus **Belum diklasifikasi** sampai diisi. Atribut ini ditolak pada akun selain beban dan pada akun header.

### Draf Koreksi dari Perlakuan Fiskal

Di halaman **Rekonsiliasi Fiskal**, bagian **Draf Koreksi dari Perlakuan Fiskal Akun**:

1. Klik **Buat Draf Koreksi**. Sistem membuat draf Beda Tetap, arah Positif, untuk akun Tidak dapat dikurangkan dan Bersyarat.
2. Baca kolom Catatan. Kolom ini memuat saldo akun, natura dengan penerima, dan koreksi yang sudah dikonfirmasi.
3. Ubah jumlah bila perlu, lalu klik **Konfirmasi**. Koreksi masuk ke perhitungan PKP.
4. Klik **Tolak** untuk menghapus draf yang tidak berlaku.

Draf tidak masuk ke total koreksi, PKP, PPh Badan, L1, maupun ekspor SPT sebelum dikonfirmasi. **Checklist SPT** menampilkan jumlah draf yang belum ditinjau.

Klik **Buat Draf Koreksi** sekali lagi akan mengganti seluruh draf hasil pembuatan sebelumnya pada tahun tersebut, termasuk draf yang jumlahnya sudah diubah tetapi belum dikonfirmasi dan draf yang sudah ditolak. Koreksi yang sudah dikonfirmasi tetap ada dan mengurangi jumlah draf baru untuk akun yang sama.

Peringatan setelah pembuatan draf:

- **Akun bersaldo tanpa perlakuan fiskal**: isi Perlakuan Fiskal akun tersebut, lalu buat ulang draf.
- **Transaksi berlabel Natura tanpa penerima**: catat penerima pada detail transaksi (lihat [Natura dan Kenikmatan](05-penggajian.md)).

### Mengelola Koreksi Fiskal via API

Endpoint CRUD tersedia untuk integrasi dengan sistem eksternal:

```
GET    /api/fiscal-adjustments?year=2025     — daftar koreksi per tahun
POST   /api/fiscal-adjustments               — buat koreksi baru
PUT    /api/fiscal-adjustments/{id}           — update koreksi
DELETE /api/fiscal-adjustments/{id}           — hapus koreksi
POST   /api/fiscal-adjustments/generate?year=2025 — buat draf dari perlakuan fiskal akun
POST   /api/fiscal-adjustments/{id}/confirm   — konfirmasi draf
```

Contoh request membuat koreksi:

```json
{
  "year": 2025,
  "description": "Denda Pajak",
  "adjustmentCategory": "PERMANENT",
  "adjustmentDirection": "POSITIVE",
  "amount": 3226367.00,
  "accountCode": "5.9.90",
  "notes": "Pasal 9(1)(k) UU PPh"
}
```

Validasi:
- `adjustmentCategory`: `PERMANENT` atau `TEMPORARY`
- `adjustmentDirection`: `POSITIVE` atau `NEGATIVE`
- `amount`: harus positif
- `year` dan `description`: wajib diisi

Autentikasi: Bearer token. Scope `tax-export:read` untuk membaca, `tax-export:write` untuk menulis.

### Skenario Penggunaan

**Persiapan SPT Tahunan Badan:**

1. Setelah tutup buku komersial, buka Rekonsiliasi Fiskal
2. Klik **Buat Draf Koreksi**, tinjau, lalu konfirmasi atau tolak setiap draf
3. Tambahkan koreksi positif untuk biaya lain yang tidak diakui pajak dan tidak tercakup draf (biaya tanpa bukti potong)
4. Tambahkan koreksi negatif jika ada pendapatan yang sudah dikenai pajak final
5. Verifikasi total koreksi dan PKP yang dihasilkan
6. Gunakan hasil untuk mengisi SPT Tahunan Badan di Coretax

---

## SPT Tahunan Badan

Halaman Checklist SPT Tahunan membantu mempersiapkan data untuk pelaporan SPT Tahunan PPh Badan di Coretax DJP. Halaman ini menampilkan status kesiapan data dan menyediakan download lampiran dalam format Excel.

### Mengakses Checklist SPT Tahunan

Buka menu **Laporan** > **SPT Tahunan** di sidebar.

![Checklist SPT Tahunan](screenshots/spt-checklist/spt-checklist-page.png)

### Checklist Kesiapan Data

Sistem memeriksa 7 komponen data:

| No | Komponen | Status Hijau | Status Kuning/Merah |
|----|----------|-------------|---------------------|
| 1 | **Laporan Keuangan** | Ada transaksi pendapatan/beban | Belum ada transaksi |
| 2 | **Koreksi Fiskal** | Sudah diisi | Belum ada koreksi |
| 3 | **PPh Badan Terutang** | Berhasil dihitung | Gagal menghitung |
| 4 | **Periode Fiskal** | 12/12 bulan ditutup | Ada periode belum ditutup |
| 5 | **Penyusutan Aset Tetap** | Ada aset tercatat | Tidak ada aset |
| 6 | **Payroll & PPh 21** | Ada data payroll | Tidak ada data |
| 7 | **Kompensasi Kerugian** | Info rugi fiskal aktif | Tidak ada rugi |

### Download Lampiran SPT

Setelah data siap, download file Excel untuk diimpor ke Coretax via DJP Converter:

![Download Lampiran SPT](screenshots/spt-checklist/spt-checklist-downloads.png)

| Lampiran | Isi | Format |
|----------|-----|--------|
| **L1 — Rekonsiliasi Fiskal** | P&L komersial, koreksi fiskal, kompensasi rugi, PKP, PPh Badan | Excel |
| **L4 — Penghasilan Final** | PPh 4(2) per kode objek pajak | Excel |
| **L9 — Penyusutan & Amortisasi** | Daftar aset tetap, format DJP Converter (DATA + REF) | Excel |
| **Transkrip 8A — Laporan Keuangan** | Neraca + Laba Rugi (2 sheet) | Excel |
| **BPA1 — e-Bupot PPh 21** | 1721-A1 semua karyawan, format DJP Converter | Excel |
| **Rekonsiliasi Fiskal (detail)** | Format laporan internal | Excel |
| **Laporan Keuangan (PDF)** | Neraca + Laba Rugi gabungan untuk upload Coretax | PDF |

### Catatan Teknis

- **Beban luar usaha (L1):** Semua beban non-operasional (akun 5.2, 5.3, 5.9, dll.) secara otomatis masuk ke bagian "beban luar usaha" pada L1. Sistem menggunakan filter eksklusi (semua yang bukan 5.1) sehingga akun baru yang ditambahkan di masa depan otomatis tercakup.
- **Penyusutan aset pool (L9):** Jika aset tidak memiliki entri penyusutan individual (misalnya aset pool yang disusutkan di level GL), sistem menghitung dari formula bulanan × jumlah bulan aktif dalam tahun tersebut.
- **Pembulatan PKP:** PKP dibulatkan ke bawah ke ribuan terdekat sebelum perhitungan PPh Badan, sesuai UU PPh pasal 6 ayat 3. Nilai PKP asli dan PKP pembulatan keduanya ditampilkan di response API.

### Kompensasi Kerugian Fiskal

Rugi fiskal yang belum habis masa berlakunya (5 tahun per UU PPh Pasal 6 ayat 2) dapat dikompensasi ke tahun pajak berikutnya. Pengelolaan kompensasi kerugian dilakukan via API:

```
GET    /api/fiscal-adjustments/loss-carryforward?year=2025  — daftar rugi fiskal aktif
POST   /api/fiscal-adjustments/loss-carryforward             — catat rugi fiskal baru
DELETE /api/fiscal-adjustments/loss-carryforward/{id}        — hapus entry
```

Kompensasi kerugian diterapkan otomatis (FIFO berdasarkan tahun asal) pada laporan L1.

### Akses via API

Semua lampiran juga tersedia via API di `/api/tax-export/spt-tahunan/*`:

```
GET /api/tax-export/spt-tahunan/lampiran?year=2025        — Semua lampiran (consolidated)
GET /api/tax-export/spt-tahunan/l1?year=2025              — JSON
GET /api/tax-export/spt-tahunan/l1?year=2025&format=excel — XLSX
GET /api/tax-export/spt-tahunan/l4?year=2025
GET /api/tax-export/spt-tahunan/l9?year=2025
GET /api/tax-export/spt-tahunan/transkrip-8a?year=2025
GET /api/tax-export/ebupot-pph21?year=2025
GET /api/tax-export/financial-statements/pdf?year=2025    — PDF (Neraca + Laba Rugi)
GET /api/tax-export/coretax/spt-badan?year=2025           — Coretax-compatible JSON
```

**Endpoint konsolidasi** (`/lampiran`) mengembalikan seluruh data lampiran dalam satu response JSON, dengan field number Coretax (8A.I.1, 8A.II.1, dst.) siap untuk key-in ke Coretax DJP. Mencakup:
- Transkrip 8A (neraca + laba rugi dengan nomor field Coretax)
- Lampiran I (rekonsiliasi fiskal + kompensasi kerugian)
- Lampiran II (rincian beban usaha dan beban luar usaha)
- Lampiran III (kredit pajak PPh 23 dari bukti potong)
- Lampiran V (placeholder — data pemegang saham diisi manual)
- PPh Badan (PKP, PKP pembulatan, PPh terutang, kredit pajak, PPh 29)

**Endpoint Coretax** (`/coretax/spt-badan`) mengembalikan JSON terstruktur dengan nama field yang cocok dengan form Coretax. Nilai berupa angka biasa (tanpa format ribuan) untuk langsung diketik ke Coretax. Mencakup:
- `induk`: field utama SPT (peredaran usaha, laba, koreksi fiskal, PKP, PPh)
- `l1dLabaRugi`: item laba rugi per akun
- `l1dNeraca`: item neraca per akun
- `l3KreditPajak`: daftar bukti potong PPh 23 individual
- `penyusutan`: daftar aset tetap dan penyusutan

**Endpoint PDF** (`/financial-statements/pdf`) menghasilkan file PDF 2 halaman (Neraca + Laba Rugi) dengan header nama perusahaan dan NPWP, siap upload sebagai lampiran laporan keuangan di Coretax.

**Laporan Laba Rugi via Analysis API** juga mendukung parameter `excludeClosing`:
```
GET /api/analysis/income-statement?startDate=2025-01-01&endDate=2025-12-31&excludeClosing=true
```
Parameter ini mengeluarkan jurnal penutup dari perhitungan, berguna untuk keperluan pajak dan analisis.

Autentikasi: Bearer token dengan scope `tax-export:read`.

---

## Register Pelaporan Pajak

Dokumen Coretax terbagi dua. Dokumen yang menempel pada transaksi — faktur pajak keluaran, bukti potong PPh 23, kode billing atas setoran yang sudah dijurnal — disimpan pada transaksi terkait melalui lampiran dan detail pajak transaksi. Dokumen yang menempel pada **masa pajak** tidak punya transaksi untuk ditumpangi: SPT Masa PPN, SPT Masa PPh 21, BPE, STP, surat teguran, SP2DK, SKPLB. Register pelaporan pajak adalah tempat dokumen jenis kedua ini.

Register menjawab tiga pertanyaan yang sebelumnya tidak bisa dijawab aplikasi:

- Masa mana yang sudah dilaporkan, kapan, dan dengan nomor BPE berapa
- Berapa kurang bayar sebuah masa, kode billing dan NTPN-nya, serta transaksi setoran mana yang membukukannya
- Rangkaian STP → surat teguran → kode billing → BPN untuk masa yang bermasalah

### Kunci Periode

Satu catatan pelaporan diidentifikasi oleh kombinasi **jenis pajak + periode + nomor pembetulan**. Mendaftarkan kombinasi yang sama dua kali ditolak.

| Jenis pajak | Dilaporkan | Format periode |
|-------------|------------|----------------|
| `PPN` | per masa | `YYYY-MM` |
| `PPH21` | per masa | `YYYY-MM` |
| `PPH23_UNIFIKASI` | per masa | `YYYY-MM` |
| `PPH_BADAN` | per tahun | `YYYY` |

Nomor pembetulan `0` berarti SPT normal. Pembetulan dicatat sebagai record terpisah dengan `pembetulanNo: 1`, dan status SPT aslinya diubah menjadi `CORRECTED`.

### Status Pelaporan

| Status | Arti |
|--------|------|
| `NOT_FILED` | Masa sudah diketahui, SPT belum dilaporkan |
| `FILED` | Sudah dilaporkan tepat waktu |
| `LATE` | Dilaporkan lewat batas waktu |
| `CORRECTED` | Sudah digantikan oleh pembetulan |

Status diisi oleh pengguna. Aplikasi mencatat apa yang terjadi di DJP, tidak menyimpulkannya sendiri dari tanggal deadline.

### Mengelola Register via API

Register dikelola melalui REST API:

```
GET    /api/tax-filings?taxType=PPN&year=2025     — daftar pelaporan
GET    /api/tax-filings/{id}                      — detail + dokumen
POST   /api/tax-filings                           — daftarkan pelaporan
PUT    /api/tax-filings/{id}                      — update pelaporan
DELETE /api/tax-filings/{id}                      — hapus (hanya bila tanpa dokumen)
```

Contoh mendaftarkan SPT Masa PPN yang terlambat dan sudah dibayar:

```json
{
  "taxType": "PPN",
  "period": "2025-04",
  "pembetulanNo": 0,
  "status": "LATE",
  "filedDate": "2025-06-12",
  "bpeNumber": "BPE-25040012345",
  "bpeDate": "2025-06-12",
  "kurangBayar": 1250000,
  "billingCode": "820250612001234",
  "ntpn": "0912B4C7D1E2F3A4",
  "paidDate": "2025-06-11",
  "paymentTransactionId": "uuid-transaksi-setoran"
}
```

Validasi:
- `period` harus `YYYY-MM` untuk SPT Masa dan `YYYY` untuk SPT Tahunan Badan
- `kurangBayar` dan `lebihBayar` tidak boleh diisi bersamaan
- SPT `nihil` tidak boleh punya kurang bayar atau lebih bayar
- `paymentTransactionId` opsional — masa nihil tidak punya setoran, dan masa yang seluruhnya FP 03 (BUMN pemungut) bersifat lapor saja karena PPN disetor oleh BUMN

Autentikasi: Bearer token dengan scope `tax-filings:read` (baca) dan `tax-filings:write` (tulis).

### Melampirkan Dokumen

Dokumen diunggah sebagai multipart ke pelaporan yang bersangkutan:

```
POST   /api/tax-filings/{id}/documents                        — unggah dokumen
GET    /api/tax-filings/{id}/documents                        — daftar dokumen
GET    /api/tax-filings/{id}/documents/{docId}/download       — unduh berkas
DELETE /api/tax-filings/{id}/documents/{docId}                — hapus dokumen
```

Field metadata: `docType` (wajib), `docNumber`, `docDate`, `dueDate`, `amount`, `referenceNumber`, `notes`.

| `docType` | Dokumen |
|-----------|---------|
| `SPT` | Surat Pemberitahuan |
| `BPE` | Bukti Penerimaan Elektronik |
| `STP` | Surat Tagihan Pajak |
| `TEGURAN` | Surat Teguran |
| `SP2DK` | Surat Permintaan Penjelasan atas Data dan/atau Keterangan |
| `SKPLB` | Surat Ketetapan Pajak Lebih Bayar |
| `SKPKPP` | Surat Keputusan Pengembalian Kelebihan Pembayaran Pajak |
| `KODE_BILLING` | Kode billing |
| `BPN` | Bukti Penerimaan Negara |
| `SURAT` | Surat lainnya |

Berkas disimpan di penyimpanan dokumen yang sama dengan lampiran transaksi: terenkripsi, ber-checksum SHA-256, dan tunduk pada retensi 10 tahun.

`referenceNumber` mencatat dokumen yang dijawab atau ditagih oleh dokumen ini. Inilah yang membuat rangkaian penagihan bisa dibaca kembali:

```
SPT PPN 2025-04  ──▶  STP-07643        (tagihan kurang bayar)
                       └──▶ TEG-00112  referenceNumber: "STP-07643"
                              └──▶ kode billing ──▶ BPN
```

STP ganda atas masa yang sudah lunas akan terlihat sebagai dua dokumen `STP` pada pelaporan yang sama — satu punya `BPN`, satu tidak.

Pelaporan yang masih punya dokumen tidak bisa dihapus. Dokumen SPT/BPE/STP terikat kewajiban retensi 10 tahun, jadi penghapusan dilakukan satu per satu secara sadar, bukan ikut terhapus bersama induknya.

### Status Pelaporan di Ringkasan Pajak

Endpoint ringkasan pajak `GET /api/analysis/tax-summary?startDate=&endDate=` kini menyertakan array `filings` di samping saldo akun pajak, berisi seluruh pelaporan yang periodenya beririsan dengan rentang tanggal yang diminta — lengkap dengan status, nomor BPE, kurang/lebih bayar, dan tanggal setor.

Hanya pelaporan yang sudah terdaftar yang muncul. Masa yang tidak ada di daftar berarti belum punya catatan sama sekali — dan itulah sinyal bahwa masa tersebut masih perlu ditangani.

---

## Tips Kepatuhan

1. **Catat tepat waktu** - Jangan menunda pencatatan transaksi pajak
2. **Simpan bukti** - Faktur pajak, bukti potong, NTPN
3. **Rekonsiliasi bulanan** - Cocokkan saldo akun pajak
4. **Gunakan kalender** - Set reminder untuk deadline
5. **Konsultasi** - Hubungi konsultan pajak untuk kasus kompleks

---

## Lihat Juga

- [Penggajian](05-penggajian.md) - PPh 21 karyawan
- [Tutorial Akuntansi](02-tutorial-akuntansi.md) - Jurnal pajak
- [Referensi Template](12-lampiran-template.md) - Template transaksi pajak
