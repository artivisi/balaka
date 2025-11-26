# Transaksi

## Pengertian

Transaksi adalah pencatatan aktivitas keuangan yang terjadi dalam bisnis Anda. Setiap transaksi akan menghasilkan jurnal yang mempengaruhi saldo akun.

## Status Transaksi

| Status | Warna | Keterangan |
|--------|-------|------------|
| Draft | Kuning | Tersimpan tapi belum mempengaruhi saldo |
| Posted | Hijau | Sudah diposting ke buku besar |
| Void | Merah | Dibatalkan |

## Melihat Daftar Transaksi

1. Klik menu **Transaksi** di sidebar
2. Gunakan filter untuk menyaring transaksi:
   - **Pencarian** - Cari berdasarkan nomor atau keterangan
   - **Periode** - Filter berdasarkan rentang tanggal
   - **Status** - Filter berdasarkan status transaksi
   - **Kategori** - Filter berdasarkan kategori template
   - **Proyek** - Filter berdasarkan proyek terkait

## Membuat Transaksi Baru

1. Klik tombol **Transaksi Baru**
2. Pilih template yang akan digunakan
3. Isi form transaksi:
   - **Tanggal** - Tanggal transaksi terjadi
   - **Jumlah** - Nilai transaksi dalam Rupiah
   - **Akun Sumber** - Pilih akun kas/bank yang terlibat
   - **Proyek** - Pilih proyek terkait untuk tracking profitabilitas (opsional)
   - **Keterangan** - Deskripsi yang jelas tentang transaksi
   - **No. Referensi** - Nomor invoice/dokumen terkait (opsional)
4. Review **Preview Jurnal** untuk memastikan kebenaran
5. Klik **Simpan Draft** atau **Simpan & Posting**

## Perbedaan Simpan Draft vs Posting

| Aksi | Efek |
|------|------|
| Simpan Draft | Transaksi tersimpan, dapat diedit, belum mempengaruhi saldo |
| Simpan & Posting | Transaksi langsung diposting, mempengaruhi saldo, tidak dapat diedit |

## Melihat Detail Transaksi

1. Klik nomor transaksi di daftar
2. Halaman detail menampilkan:
   - Informasi lengkap transaksi
   - Jurnal yang dihasilkan
   - Audit trail (riwayat perubahan)

## Memposting Transaksi Draft

1. Buka detail transaksi dengan status Draft
2. Klik tombol **Posting**
3. Konfirmasi untuk memposting

> Catatan: Transaksi yang sudah diposting tidak dapat diedit. Jika ada kesalahan, gunakan fitur Void.

## Membatalkan Transaksi (Void)

Jika terjadi kesalahan pada transaksi yang sudah diposting:

1. Buka detail transaksi
2. Klik tombol **Void**
3. Pilih **Alasan Pembatalan**:
   - Kesalahan Input
   - Duplikasi
   - Batal Transaksi
   - Lainnya
4. Masukkan **Catatan** penjelasan (opsional)
5. Klik **Konfirmasi Void**

Efek void:
- Status berubah menjadi Void
- Jurnal reversal otomatis dibuat
- Saldo akun dikembalikan ke sebelum transaksi

## Tips Pencatatan Transaksi

1. Catat transaksi sesegera mungkin setelah terjadi
2. Gunakan keterangan yang jelas dan konsisten
3. Simpan nomor referensi dokumen pendukung
4. Review Preview Jurnal sebelum menyimpan
5. Gunakan Draft jika belum yakin, posting setelah verifikasi
