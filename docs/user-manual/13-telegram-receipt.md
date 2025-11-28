# Telegram Receipt

## Kapan Anda Membutuhkan Ini

Gunakan panduan ini ketika Anda:
- Ingin mengirim foto struk/kwitansi dari HP langsung ke aplikasi
- Ingin menyimpan bukti transaksi secara digital
- Ingin mempercepat pencatatan transaksi dari struk fisik

## Konsep yang Perlu Dipahami

Fitur **Telegram Receipt** memungkinkan Anda:
1. Mengirim foto struk via Telegram bot
2. Sistem menyimpan gambar dan menunggu review
3. Anda memilih template dan memposting transaksi dari aplikasi

Ini memudahkan pencatatan karena Anda bisa langsung foto struk dari lokasi, lalu proses di kantor kemudian.

## Skenario 1: Setup Telegram Bot (Sekali Saja)

**Situasi**: Pertama kali menggunakan fitur Telegram Receipt.

**Langkah-langkah**:

1. Buka Telegram di HP
2. Cari bot **@AplikasiAkuntingBot** (atau nama bot yang dikonfigurasi)
3. Ketik `/start` untuk memulai
4. Bot akan meminta **kode akses**
5. Di aplikasi web, buka menu **Pengaturan** > **Telegram**
6. Salin **Kode Akses** yang ditampilkan
7. Kirim kode tersebut ke bot
8. Bot akan mengkonfirmasi: "Akun berhasil terhubung"

**Hasil**: HP Anda sekarang terhubung ke akun aplikasi.

## Skenario 2: Kirim Struk Belanja

**Situasi**: Anda baru saja membeli perlengkapan kantor dan mendapat struk dari kasir.

**Langkah-langkah**:

**Di HP (Telegram):**

1. Buka chat dengan **@AplikasiAkuntingBot**
2. Foto struk dengan jelas (pastikan angka terbaca)
3. Kirim foto ke bot
4. Tambahkan caption (opsional): `ATK dari Toko ABC - Rp 350.000`
5. Bot akan konfirmasi: "Struk diterima, silakan proses di aplikasi"

**Di Aplikasi Web:**

1. Klik menu **Transaksi** di sidebar
2. Anda akan melihat notifikasi **"X struk menunggu diproses"**
3. Klik notifikasi tersebut atau buka tab **Struk Pending**
4. Klik struk yang baru dikirim
5. Review gambar struk
6. Isi form transaksi:
   - **Template**: Beban ATK
   - **Tanggal**: Tanggal di struk
   - **Jumlah**: `350000`
   - **Keterangan**: `Pembelian ATK - Toko ABC`
7. Klik **Simpan & Posting**

**Hasil**: Transaksi tercatat dengan lampiran gambar struk.

## Skenario 3: Kirim Struk dengan Caption Detail

**Situasi**: Anda ingin memberikan informasi lebih detail saat mengirim struk.

**Format Caption yang Disarankan**:

```
[Kategori] - [Vendor/Toko] - Rp [Jumlah]
Keterangan tambahan jika perlu
```

**Contoh**:
- `Listrik - PLN - Rp 850.000`
- `Internet - Biznet - Rp 500.000`
- `Makan siang meeting - Restoran XYZ - Rp 250.000`
- `Parkir - Rp 10.000`

Caption ini akan muncul saat Anda memproses struk di aplikasi, memudahkan pengisian form.

## Skenario 4: Kirim Banyak Struk Sekaligus

**Situasi**: Anda punya beberapa struk yang perlu dicatat setelah meeting seharian.

**Langkah-langkah**:

**Di HP (Telegram):**

1. Buka chat dengan bot
2. Kirim foto struk satu per satu (jangan kirim sekaligus dalam satu pesan)
3. Berikan caption untuk setiap foto
4. Ulangi untuk semua struk

**Di Aplikasi Web:**

1. Buka menu **Transaksi** > **Struk Pending**
2. Anda akan melihat daftar semua struk yang belum diproses
3. Klik struk pertama, isi form, posting
4. Lanjut ke struk berikutnya
5. Ulangi sampai semua struk terproses

## Skenario 5: Batalkan Struk yang Salah Kirim

**Situasi**: Anda tidak sengaja mengirim foto yang bukan struk.

**Langkah-langkah**:

1. Di aplikasi web, buka **Struk Pending**
2. Klik struk yang salah
3. Klik tombol **Hapus** atau **Abaikan**
4. Konfirmasi penghapusan

## Skenario 6: Lampirkan Dokumen ke Transaksi yang Sudah Ada

**Situasi**: Anda sudah mencatat transaksi, tapi ingin menambahkan lampiran bukti.

**Langkah-langkah**:

1. Buka detail transaksi yang sudah ada
2. Di bagian **Lampiran**, klik **Tambah Lampiran**
3. Upload file dari komputer, atau
4. Pilih dari **Struk Pending** yang sudah dikirim via Telegram
5. Klik **Simpan**

## Tips

1. **Foto dengan jelas** - Pastikan angka dan tanggal terbaca
2. **Satu struk satu foto** - Jangan gabung beberapa struk dalam satu foto
3. **Caption singkat** - Tambahkan informasi penting di caption
4. **Proses rutin** - Biasakan memproses struk setiap hari agar tidak menumpuk
5. **Backup otomatis** - Gambar struk tersimpan di sistem sebagai bukti

## Troubleshooting

| Masalah | Solusi |
|---------|--------|
| Bot tidak merespon | Pastikan bot online, cek dengan `/status` |
| Struk tidak muncul di aplikasi | Refresh halaman, tunggu beberapa detik |
| Kode akses tidak valid | Generate ulang kode di Pengaturan > Telegram |
| Foto tidak jelas | Kirim ulang dengan pencahayaan lebih baik |

## Lihat Juga

- [Mencatat Pengeluaran](11-mencatat-pengeluaran.md) - Template untuk berbagai jenis pengeluaran
- [Laporan Harian](20-laporan-harian.md) - Verifikasi transaksi yang sudah dicatat
- [Setup Awal](50-setup-awal.md) - Konfigurasi Telegram bot
- [Setup Telegram Bot](55-setup-telegram.md) - Konfigurasi server-side untuk administrator
