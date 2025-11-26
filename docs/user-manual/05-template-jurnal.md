# Template Jurnal

## Pengertian

Template Jurnal adalah pola pencatatan transaksi yang sudah dikonfigurasi sebelumnya untuk mempermudah pencatatan transaksi berulang.

## Kategori Template

| Kategori | Warna | Keterangan |
|----------|-------|------------|
| Pendapatan | Hijau | Penerimaan dari penjualan/jasa |
| Pengeluaran | Merah | Biaya operasional |
| Pembayaran | Biru | Pembayaran hutang |
| Penerimaan | Cyan | Penerimaan piutang |
| Transfer | Ungu | Perpindahan antar akun |

## Melihat Daftar Template

1. Klik menu **Template** di sidebar
2. Template ditampilkan dalam bentuk kartu dengan kategori
3. Gunakan fitur pencarian dan filter:
   - **Pencarian** - Cari berdasarkan nama atau kode template
   - **Tab Kategori** - Filter berdasarkan kategori
   - **Tag** - Filter berdasarkan tag template

## Favorit Template

Template yang sering digunakan dapat ditandai sebagai favorit:

1. Klik ikon bintang pada kartu template
2. Template favorit akan muncul di bagian atas daftar
3. Klik lagi untuk menghapus dari favorit

Sistem juga melacak template yang baru-baru ini digunakan untuk akses cepat.

## Menggunakan Template

1. Dari halaman Template, klik tombol **Gunakan** pada template yang diinginkan
2. Atau dari halaman Transaksi, klik **Transaksi Baru** dan pilih template
3. Anda akan diarahkan ke form transaksi yang sudah terisi sesuai template

## Membuat Template Baru

1. Klik tombol **Template Baru**
2. Isi informasi template:
   - **Nama Template** - Nama yang menjelaskan fungsi template
   - **Kategori** - Pilih kategori yang sesuai
   - **Klasifikasi Arus Kas** - Operasional, Investasi, atau Pendanaan
   - **Tipe Template** - Sederhana atau Terperinci
3. Konfigurasi baris jurnal:
   - Pilih **Akun** untuk setiap baris
   - Tentukan **Posisi** (Debit/Kredit)
   - Masukkan **Formula** jika diperlukan
4. Klik **Simpan Template**

## Formula yang Didukung

| Formula | Keterangan | Contoh |
|---------|------------|--------|
| `amount` | Jumlah yang diinput | Rp 1.000.000 |
| `amount * 0.11` | PPN 11% | Rp 110.000 |
| `amount / 1.11` | DPP dari harga inklusif | Rp 900.901 |
| `amount - ppn` | Jumlah setelah dikurangi PPN | Rp 890.000 |
| `amount > 2000000 ? amount * 0.02 : 0` | PPh 23 dengan threshold | Rp 20.000 (jika > 2jt) |
| `1000000` | Nilai tetap | Rp 1.000.000 |

### Formula Kondisional

Formula kondisional menggunakan format ternary operator:
```
kondisi ? nilai_jika_benar : nilai_jika_salah
```

Contoh: PPh 23 hanya dikenakan jika jumlah transaksi > Rp 2.000.000:
```
amount > 2000000 ? amount * 0.02 : 0
```

### Mencoba Formula

Gunakan fitur **Coba Formula** pada form template untuk menguji formula sebelum menyimpan:

1. Masukkan formula
2. Klik tombol **Coba Formula**
3. Masukkan nilai contoh
4. Lihat hasil perhitungan

## Tag Template

Tag membantu mengkategorikan dan menemukan template:

1. Buka form edit template
2. Tambahkan tag yang relevan (contoh: "PPN", "PPh23", "Proyek")
3. Tag akan muncul sebagai badge di kartu template
4. Filter template berdasarkan tag di halaman daftar

## Contoh Template: Pendapatan Jasa dengan PPN

Struktur jurnal:
```
Debit  : Bank BCA          = amount
Kredit : Hutang PPN        = amount * 0.11 / 1.11
Kredit : Pendapatan Jasa   = amount / 1.11
```

## Menduplikasi Template

1. Buka detail template yang ingin diduplikasi
2. Klik tombol **Duplikat**
3. Ubah nama dan konfigurasi sesuai kebutuhan
4. Klik **Simpan Template**

## Menonaktifkan Template

Template yang tidak digunakan lagi dapat dinonaktifkan agar tidak muncul di pilihan transaksi:

1. Buka detail template
2. Klik **Edit**
3. Nonaktifkan toggle **Status Template**
4. Klik **Simpan Perubahan**
