# Jadwal Amortisasi

## Pengertian

Jadwal amortisasi digunakan untuk mengalokasikan biaya atau pendapatan secara merata ke beberapa periode akuntansi. Fitur ini mengotomatisasi pembuatan jurnal penyesuaian bulanan.

## Tipe Jadwal

| Tipe | Nama Indonesia | Contoh |
|------|----------------|--------|
| `prepaid_expense` | Beban Dibayar Dimuka | Asuransi, sewa, lisensi software |
| `unearned_revenue` | Pendapatan Diterima Dimuka | Pembayaran dimuka, retainer |
| `intangible_asset` | Aset Tak Berwujud | Website, pengembangan software |
| `accrued_revenue` | Pendapatan Akrual | Retainer bulanan ditagih kuartalan |

## Status Jadwal

| Status | Warna | Keterangan |
|--------|-------|------------|
| Active | Hijau | Jadwal berjalan, entri masih diproses |
| Completed | Biru | Semua periode sudah selesai |
| Cancelled | Abu-abu | Jadwal dibatalkan |

## Melihat Daftar Jadwal

1. Klik menu **Amortisasi** di sidebar
2. Gunakan filter untuk menyaring:
   - **Tipe** - Filter berdasarkan tipe jadwal
   - **Status** - Filter berdasarkan status jadwal
   - **Pencarian** - Cari berdasarkan nama atau kode

## Membuat Jadwal Baru

1. Klik tombol **Jadwal Baru**
2. Pilih **Tipe Jadwal**
3. Isi informasi jadwal:
   - **Nama** - Nama deskriptif untuk jadwal
   - **Keterangan** - Detail tambahan
   - **Akun Sumber** - Akun asal (aset/kewajiban)
   - **Akun Tujuan** - Akun beban/pendapatan
   - **Jumlah Total** - Total nilai yang akan diamortisasi
   - **Tanggal Mulai** - Periode pertama amortisasi
   - **Tanggal Selesai** - Periode terakhir amortisasi
   - **Frekuensi** - Bulanan atau tahunan
4. Review perhitungan otomatis:
   - **Jumlah per Periode** - Total dibagi jumlah periode
   - **Jumlah Periode** - Dihitung dari tanggal mulai/selesai
5. Pilih opsi posting:
   - **Auto-Post** - Jurnal otomatis diposting setiap periode
   - **Tanggal Posting** - Hari dalam bulan untuk posting otomatis
6. Klik **Simpan Jadwal**

## Pola Jurnal

Setiap tipe jadwal memiliki pola jurnal berbeda:

### Beban Dibayar Dimuka
```
Debit  : Beban (akun tujuan)
Kredit : Dibayar Dimuka (akun sumber)
```

### Pendapatan Diterima Dimuka
```
Debit  : Diterima Dimuka (akun sumber)
Kredit : Pendapatan (akun tujuan)
```

### Aset Tak Berwujud
```
Debit  : Beban Amortisasi (akun tujuan)
Kredit : Akumulasi Amortisasi (akun sumber)
```

### Pendapatan Akrual
```
Debit  : Piutang Pendapatan (akun sumber)
Kredit : Pendapatan (akun tujuan)
```

## Detail Jadwal

Halaman detail menampilkan:

### Informasi Jadwal
- Nama dan tipe jadwal
- Akun sumber dan tujuan
- Total, jumlah per periode, dan saldo tersisa

### Daftar Entri

| Kolom | Keterangan |
|-------|------------|
| Periode | Nomor urut periode |
| Tanggal | Rentang tanggal periode |
| Jumlah | Nilai amortisasi periode tersebut |
| Status | Draft, Posted, atau Pending |
| Jurnal | Link ke jurnal jika sudah diposting |

## Entri Amortisasi

### Status Entri

| Status | Warna | Keterangan |
|--------|-------|------------|
| Pending | Abu-abu | Belum waktunya diproses |
| Draft | Kuning | Sudah dibuat, menunggu posting |
| Posted | Hijau | Sudah diposting ke buku besar |

### Posting Manual

Jika tidak menggunakan auto-post:

1. Buka detail jadwal
2. Klik tombol **Post** pada entri dengan status Draft
3. Konfirmasi posting

### Batch Posting

Sistem menjalankan proses batch harian yang:
1. Menemukan entri yang sudah jatuh tempo
2. Membuat jurnal untuk entri yang belum diproses
3. Memposting jurnal jika auto-post aktif

## Pembulatan

Sistem menangani pembulatan secara otomatis:
- Periode 1 sampai n-1: menggunakan jumlah per periode standar
- Periode terakhir: menyerap selisih pembulatan

Contoh: Rp 100.000 untuk 3 periode
- Periode 1: Rp 33.333
- Periode 2: Rp 33.333
- Periode 3: Rp 33.334 (menyerap selisih)

## Tips Penggunaan

1. Gunakan nama deskriptif yang mencakup tahun (contoh: "Asuransi Kantor 2025")
2. Aktifkan auto-post untuk jadwal rutin yang tidak perlu review
3. Nonaktifkan auto-post untuk item yang perlu validasi manual
4. Periksa daftar jadwal di awal bulan untuk memastikan entri terproses
