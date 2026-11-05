# Ruang Pemeriksaan Pajak

Ruang pemeriksaan mengumpulkan semua data yang ditanyakan kantor pajak saat SP2DK atau pemeriksaan: hasil uji mandiri per tahun, bukti setiap transaksi, catatan kasus, dan dokumen yang sudah diserahkan. Arsip dokumen menyimpan salinan dokumen lama (faktur, bukti potong, SPT, rekening koran) yang dapat dicari isinya.

Menu ada di **Pembelian > Ruang Pemeriksaan Pajak** dan **Pembelian > Arsip Dokumen Pajak**. Halaman ini dapat dibuka pengguna berperan Admin, Owner, Accountant, dan Auditor. Pencatatan kasus, impor arsip, dan penyerahan dokumen hanya untuk Admin, Owner, dan Accountant.

## Tampilan Tahun Pajak

Buka **Ruang Pemeriksaan Pajak** (`/tax-audit`), isi tahun pajak, lalu klik **Tampilkan**. Halaman menampilkan:

| Bagian | Isi | Sumber |
|---|---|---|
| Uji mandiri pajak | Angka buku, angka pajak, selisih, status, dan penjelasan setiap uji | [Uji Mandiri Pajak](20-uji-mandiri-pajak.md) |
| Kekurangan bukti | Transaksi terposting yang belum memenuhi aturan bukti, dan rekening koran bulan itu yang belum diarsip | [Kelengkapan Bukti](18-kelengkapan-bukti.md) |
| Pelaporan pajak dan surat | SPT tahun itu, dokumen pajaknya (SP2DK, STP, teguran), kasus tahun itu, dan jumlah dokumen arsip | Register pelaporan pajak |
| Register aset tetap | Saldo awal, penambahan, pengurangan, dan saldo akhir harga perolehan | Register aset |
| Rekonsiliasi gaji | Beban gaji di buku, bruto dan PPh 21 payroll terposting, selisih | Rekonsiliasi payroll |
| Daftar periksa tutup buku | Hasil pemeriksaan tutup buku setahun | [Pemeriksaan Tutup Buku](19-pemeriksaan-tutup-buku.md) |

## Rincian Uji sampai Baris Jurnal

Klik **Setahun** pada baris uji, atau nomor bulan untuk uji per masa. Halaman rincian menampilkan baris angka buku dan baris angka pajak. Jumlah setiap kelompok sama dengan angka di tampilan tahun.

Kolom **Bukti** menunjukkan kelengkapan bukti transaksi tiap baris: **Lengkap**, **Tidak wajib** (tidak ada aturan bukti yang berlaku), atau **Kurang** dengan jenis bukti yang belum ada. Angka **Transaksi tanpa bukti lengkap** menghitung transaksi dengan status Kurang.

Klik nomor transaksi untuk membuka rincian baris buku besar. Baris jurnal yang dipilih ditandai. Halaman ini menampilkan:

- Bukti yang tercatat, jenis bukti yang belum ada, dan file lampiran.
- Invoice terkait dengan faktur, bukti potong, dan pengecualian rantai pajak penjualan.
- Faktur dan bukti potong pada rincian pajak transaksi.
- Kontrak yang ditautkan ke transaksi atau invoicenya.
- Baris rekening koran yang dicocokkan di rekonsiliasi bank.
- SPT yang masanya mencakup tanggal transaksi atau yang dibayar dengan transaksi ini, beserta SP2DK atau STP-nya dan kasus yang terkait.
- Dokumen arsip yang ditautkan ke transaksi ini.
- Dokumen transaksi ini yang sudah diserahkan ke kantor pajak, dengan kasus, tanggal, dan nomor permintaan.

Baris payroll pada uji gaji membuka halaman payroll; baris aset membuka halaman aset.

## Bukti Wajib Saat Posting

Mulai tahun buku baru, bukti dapat diwajibkan saat posting. Buka **Master Data > Aturan Bukti**, isi **Wajib berbukti saat posting mulai tanggal**, lalu klik **Simpan**.

Transaksi bertanggal sejak tanggal itu tidak dapat diposting selama aturan bukti melaporkan jenis bukti yang belum ada. Pesan penolakan menyebut jenis bukti yang kurang. Lampirkan file di halaman transaksi, pilih jenis buktinya, lalu posting lagi. File tanpa jenis bukti tidak dihitung.

Aturan ini berlaku untuk semua jalur posting: halaman transaksi, API, payroll, penyusutan, dan transaksi berulang. Draf yang dibuat dari template tanpa baris jurnal baru diperiksa aturan akunnya saat posting, sehingga panel bukti draf dapat menyatakan lengkap tetapi posting tetap ditolak.

Kosongkan tanggal untuk mematikan pemeriksaan.

## Kasus Pemeriksaan

Satu kasus mencatat satu surat dari kantor pajak: SP2DK, pemeriksaan, atau surat lain.

### Membuat Kasus

Buka **Ruang Pemeriksaan Pajak > Kasus pemeriksaan > Kasus Baru**. Isi jenis, tahun pajak yang diperiksa, nomor dan tanggal surat, tanggal diterima, kantor pajak, tenggat jawaban, dan pokok permintaan.

Surat wajib dilampirkan dengan salah satu cara:

- Unggah file surat (PDF atau gambar).
- Pilih surat yang sudah tercatat di register pelaporan pajak (SP2DK, STP, teguran, atau surat lain). Kasus lalu ditautkan ke SPT pemilik surat itu. Pilihan ini muncul bila halaman dibuka dengan tahun terpilih.

Nomor surat tidak boleh dipakai dua kali untuk jenis yang sama.

### Mencatat Isi Kasus

Semua catatan kasus tetap tersimpan apa adanya: tidak dapat diubah atau dihapus. Kesalahan diperbaiki dengan entri koreksi.

| Catatan | Cara |
|---|---|
| Permintaan | Isi uraian setiap butir yang ditanyakan. Butir dapat ditautkan ke baris uji mandiri dan bulannya (0 = setahun). Nomor butir diberikan berurutan. |
| Balasan dikirim, catatan pertemuan, catatan | Pilih jenis, tanggal, butir yang dijawab (opsional), isi, dan file (opsional), lalu klik **Catat**. |
| Koreksi | Isi kolom koreksi di bawah entri yang salah. Entri asli tetap tampil, koreksinya tampil di bawahnya. |
| Ubah tenggat | Isi tenggat baru dan alasannya, mis. perpanjangan disetujui. |
| Ubah status | Terbuka atau Sudah dijawab, dengan alasan. |
| Keputusan | Isi tanggal dan isi keputusan, mis. LHP2DK atau SKP. Kasus berstatus Selesai. |

Setelah kasus selesai, permintaan, penyerahan, perubahan tenggat, dan perubahan status tidak dapat ditambahkan lagi. Catatan dan koreksi masih dapat ditambahkan.

### Menyerahkan Dokumen

Penyerahan mencatat dokumen apa yang diberikan, kapan, dan untuk butir permintaan yang mana.

1. Di halaman kasus, klik **Siapkan Penyerahan**.
2. Isi nomor transaksi (pisahkan dengan koma) dan/atau kata kunci arsip, lalu klik **Cari**. Daftar menampilkan file lampiran transaksi, kontraknya, dokumen arsip yang ditautkan ke transaksi itu, dan hasil pencarian arsip. Kolom **Sudah diserahkan** menyebut penyerahan sebelumnya atas file yang sama.
3. Centang file yang diminta dan pilih butir permintaan untuk setiap file.
4. Isi tanggal penyerahan, lalu klik **Catat Penyerahan**.

Penyerahan tercatat dengan nama file, SHA-256, dan ukurannya saat itu. Klik **Unduh ZIP** pada penyerahan untuk mengambil file yang diserahkan. ZIP hanya berisi file yang dicentang, di folder `dokumen/<nomor butir>/`, dan `index.csv` dengan kolom nomor butir, uraian butir, jalur di ZIP, nama asli, SHA-256, ukuran, asal, nomor transaksi, tanggal serah, dan surat. Bila isi file di penyimpanan tidak lagi sama dengan SHA-256 yang tercatat, unduhan ditolak.

## Arsip Dokumen

Arsip menyimpan salinan dokumen dari tahun-tahun sebelumnya. Dokumen asli tetap di sumbernya (portal pajak atau bank); setiap salinan mencatat sumber dan tanggal unduhnya.

### Impor ZIP

Buka **Arsip Dokumen Pajak > Impor ZIP**, pilih file ZIP, lalu klik **Impor**.

Akar ZIP berisi `manifest.csv` dan file-file yang disebut di dalamnya. File boleh disusun dalam folder, mis. per tahun dan jenis. `manifest.csv` memakai UTF-8, pemisah koma, dan baris pertama berisi nama kolom (urutan bebas):

| Kolom | Wajib | Isi |
|---|---|---|
| `file` | Ya | Jalur file di dalam ZIP, mis. `2023/faktur/010-23-00000001.pdf` |
| `year` | Ya | Tahun dokumen, 1983 sampai tahun berjalan |
| `type` | Ya | `FAKTUR_PAJAK`, `BUPOT`, `SPT`, `BPE`, `BANK_STATEMENT`, `INVOICE`, `RECEIPT`, `CONTRACT`, `TAX_LETTER`, `OTHER` |
| `source` | Ya | `TAX_PORTAL` (Coretax/DJP Online), `BANK`, `OTHER` |
| `downloaded_on` | Ya | Tanggal unduh dari sumber, `YYYY-MM-DD`, tidak setelah hari ini |
| `doc_number` | Tidak | Nomor dokumen, maks. 100 karakter |
| `counterparty` | Tidak | Lawan transaksi, maks. 255 karakter |
| `amount` | Tidak | Nilai, angka tanpa pemisah ribuan, desimal dengan titik, mis. `3500000.00` |
| `description` | Tidak | Keterangan; apit dengan tanda kutip bila berisi koma |
| `transaction_number` | Tidak | Nomor transaksi terposting yang dibuktikan dokumen ini |
| `account_code` | Tidak | Kode akun baris jurnal transaksi tersebut; butuh `transaction_number` |

Contoh:

```csv
file,year,type,source,downloaded_on,doc_number,counterparty,amount,description,transaction_number,account_code
2023/faktur/010-23-00000001.pdf,2023,FAKTUR_PAJAK,TAX_PORTAL,2025-01-15,010-23-00000001,PT Sumber Makmur,3500000.00,Faktur jasa Januari,TRX-2023-0001,4.1.01
2023/bank/rk-2023-01.pdf,2023,BANK_STATEMENT,BANK,2025-01-16,,Bank Mandiri,,"Rekening koran Januari, giro",,
```

File yang didukung: PDF, JPG, PNG, GIF, masing-masing maks. 10 MB. ZIP maks. 100 MB per unggahan, isi maks. 200 MB dan 2.000 file. Folder `__MACOSX/` dan file `.DS_Store` dari macOS diabaikan.

Semua baris diperiksa sebelum ada yang disimpan. Satu kesalahan menolak seluruh impor dan halaman menampilkan semua kesalahan dengan nomor barisnya (baris 1 adalah nama kolom). Impor ditolak bila:

- Kolom wajib tidak ada, ada kolom yang tidak dikenal, atau nilai kolom tidak sah.
- File di manifest tidak ada di ZIP, tercantum dua kali, kosong, terlalu besar, atau isinya tidak sesuai jenisnya.
- Ada file di ZIP yang tidak tercantum di manifest.
- Isi file sama dengan baris lain atau dengan dokumen yang sudah ada di arsip.
- Transaksi tidak ditemukan atau belum terposting, atau tidak punya tepat satu baris pada akun yang disebut.
- PDF tidak dapat dibaca.

### Mencari Dokumen

Isi kata kunci di **Arsip Dokumen Pajak**, dan bila perlu tahun, jenis, dan sumber, lalu klik **Cari**. Pencarian mencakup nomor dokumen, lawan transaksi, keterangan, dan teks di dalam PDF. Hasil diurutkan menurut kecocokan, lalu tahun terbaru; potongan teks menandai kata yang cocok dengan `[ ]`. Tanpa kata kunci, daftar menampilkan dokumen menurut filter.

Pencarian mencocokkan kata utuh tanpa imbuhan: "faktur" tidak menemukan "fakturnya". Dua kata atau lebih harus semuanya ada; kata dalam tanda kutip dicari sebagai frasa. Gambar hasil pindai tidak punya teks dan hanya dapat dicari dari metadatanya. Teks lebih dari 500.000 karakter per dokumen tidak diindeks.

### Menautkan ke Buku Besar

Di halaman dokumen arsip, isi nomor transaksi terposting dan, bila perlu, kode akun barisnya, lalu klik **Tautkan**. Kosongkan nomor transaksi untuk melepas tautan. Dokumen yang ditautkan tampil di rincian baris buku besar dan menjadi pilihan saat menyiapkan penyerahan.

## Lihat Juga

- [Uji Mandiri Pajak](20-uji-mandiri-pajak.md)
- [Kelengkapan Bukti](18-kelengkapan-bukti.md)
- [Pemeriksaan Tutup Buku](19-pemeriksaan-tutup-buku.md)
