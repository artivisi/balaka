# Invoice

## Pengertian

Invoice adalah dokumen penagihan yang dikirim ke klien untuk pembayaran atas jasa atau produk yang diberikan. Invoice dapat dibuat secara manual atau otomatis dari termin pembayaran proyek.

## Status Invoice

| Status | Warna | Keterangan |
|--------|-------|------------|
| Draft | Kuning | Invoice dibuat, belum dikirim |
| Sent | Biru | Invoice sudah dikirim ke klien |
| Paid | Hijau | Pembayaran sudah diterima |
| Overdue | Merah | Melewati tanggal jatuh tempo |
| Cancelled | Abu-abu | Invoice dibatalkan |

## Melihat Daftar Invoice

1. Klik menu **Invoice** di sidebar
2. Gunakan filter untuk menyaring:
   - **Status** - Filter berdasarkan status invoice
   - **Klien** - Filter berdasarkan klien
   - **Proyek** - Filter berdasarkan proyek
   - **Periode** - Filter berdasarkan tanggal invoice

## Informasi Invoice

| Field | Keterangan |
|-------|------------|
| **Nomor Invoice** | Nomor unik invoice (contoh: INV-2025-0001) |
| **Klien** | Klien yang ditagih |
| **Proyek** | Proyek terkait |
| **Termin** | Termin pembayaran terkait |
| **Tanggal Invoice** | Tanggal penerbitan |
| **Tanggal Jatuh Tempo** | Batas waktu pembayaran |
| **Jumlah** | Total tagihan |

## Membuat Invoice dari Termin

Cara paling umum membuat invoice:

1. Buka detail proyek
2. Di bagian Termin Pembayaran, klik **Buat Invoice** pada termin yang ingin ditagih
3. Sistem otomatis membuat invoice dengan:
   - Klien dari proyek
   - Jumlah dari termin
   - Link ke termin pembayaran
4. Review dan klik **Simpan**

## Membuat Invoice Manual

1. Klik tombol **Invoice Baru**
2. Isi form invoice:
   - **Klien** - Pilih klien
   - **Proyek** - Pilih proyek (opsional)
   - **Tanggal Invoice** - Tanggal penerbitan
   - **Tanggal Jatuh Tempo** - Batas pembayaran
   - **Jumlah** - Total tagihan
   - **Keterangan** - Detail tagihan
3. Klik **Simpan**

## Detail Invoice

Halaman detail menampilkan:

### Informasi Invoice
- Semua data invoice
- Status dan riwayat perubahan

### Informasi Klien
- Nama dan kontak klien
- Alamat penagihan

### Timeline
- Tanggal dibuat
- Tanggal dikirim
- Tanggal dibayar (jika sudah)

## Alur Invoice

### 1. Draft → Sent

Setelah invoice siap dikirim:

1. Buka detail invoice
2. Klik tombol **Kirim Invoice**
3. Status berubah menjadi Sent
4. Catat tanggal pengiriman

### 2. Sent → Paid

Setelah menerima pembayaran:

1. Buka detail invoice
2. Klik tombol **Tandai Lunas**
3. Pilih tanggal pembayaran
4. Pilih akun kas/bank penerima
5. Sistem otomatis membuat jurnal:
   ```
   Debit  : Kas/Bank
   Kredit : Piutang Usaha
   ```
6. Status berubah menjadi Paid

### 3. Overdue

Invoice otomatis berubah menjadi Overdue jika:
- Status masih Sent
- Tanggal saat ini melewati tanggal jatuh tempo

### 4. Cancelled

Untuk membatalkan invoice:

1. Buka detail invoice
2. Klik tombol **Batalkan**
3. Masukkan alasan pembatalan
4. Status berubah menjadi Cancelled

## Invoice dan Revenue Recognition

Alur revenue recognition dengan invoice:

1. **Termin DP (on_signing)**: Invoice dibuat saat kontrak
   - Pembayaran diterima → Dicatat sebagai Pendapatan Diterima Dimuka

2. **Termin Milestone (on_milestone)**: Invoice dibuat saat milestone selesai
   - Milestone selesai → Revenue recognition otomatis
   - Dr. Pendapatan Diterima Dimuka / Cr. Pendapatan Jasa

3. **Invoice Paid**: Pembayaran diterima
   - Dr. Kas/Bank / Cr. Piutang Usaha

## Tips Penggunaan

1. Buat invoice segera setelah milestone selesai
2. Set reminder untuk invoice overdue
3. Gunakan termin pembayaran untuk tracking yang lebih terstruktur
4. Review daftar invoice Sent secara berkala untuk follow-up
5. Cocokkan pembayaran dengan invoice yang tepat
