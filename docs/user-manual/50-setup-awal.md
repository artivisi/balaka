# Setup Awal

## Kapan Anda Membutuhkan Ini

Gunakan panduan ini ketika Anda:
- Pertama kali menggunakan aplikasi
- Ingin menambah akun baru ke bagan akun
- Perlu mengatur saldo awal akun
- Ingin mengimpor data dari sistem lain

## Konsep yang Perlu Dipahami

### Urutan Setup yang Disarankan

1. **Bagan Akun** - Siapkan daftar akun sesuai kebutuhan bisnis
2. **Saldo Awal** - Input saldo awal jika migrasi dari sistem lain
3. **Template Jurnal** - Siapkan template untuk transaksi rutin
4. **Klien** - Input data klien (jika ada)
5. **Proyek** - Setup proyek yang sedang berjalan (jika ada)

### Bagan Akun Bawaan

Aplikasi menyediakan bagan akun standar untuk perusahaan jasa IT:

| Kategori | Contoh Akun |
|----------|-------------|
| Aset | Kas, Bank BCA, Piutang Usaha, Peralatan |
| Kewajiban | Hutang Usaha, Hutang Pajak, Pendapatan Diterima Dimuka |
| Ekuitas | Modal, Laba Ditahan |
| Pendapatan | Pendapatan Jasa, Pendapatan Lain |
| Beban | Gaji, Sewa, Listrik, Internet |

## Skenario 1: Review Bagan Akun Bawaan

**Situasi**: Pertama kali login, Anda ingin melihat akun yang sudah tersedia.

**Langkah-langkah**:

1. Klik menu **Akun** di sidebar
2. Lihat daftar akun yang tersedia
3. Gunakan filter untuk menyaring:
   - **Tipe**: Aset, Kewajiban, Ekuitas, Pendapatan, Beban
   - **Pencarian**: Cari berdasarkan kode atau nama
4. Review apakah akun-akun sudah sesuai kebutuhan

## Skenario 2: Tambah Akun Bank Baru

**Situasi**: Anda membuka rekening baru di Bank Mandiri dan ingin menambahkannya.

**Langkah-langkah**:

1. Klik menu **Akun** di sidebar
2. Klik tombol **Akun Baru**
3. Isi informasi:
   - **Kode**: `1.1.12` (sesuaikan dengan struktur yang ada)
   - **Nama**: `Bank Mandiri`
   - **Tipe**: `Aset`
   - **Sub Tipe**: `Kas & Bank`
   - **Deskripsi**: `Rekening operasional Bank Mandiri`
   - **Saldo Normal**: `Debit`
4. Klik **Simpan**

**Catatan**: Kode akun harus unik dan mengikuti struktur:
- 1.x.xx = Aset
- 2.x.xx = Kewajiban
- 3.x.xx = Ekuitas
- 4.x.xx = Pendapatan
- 5.x.xx = Beban

## Skenario 3: Tambah Akun Beban Baru

**Situasi**: Anda perlu akun khusus untuk mencatat biaya marketing.

**Langkah-langkah**:

1. Klik menu **Akun** di sidebar
2. Klik tombol **Akun Baru**
3. Isi informasi:
   - **Kode**: `5.2.10`
   - **Nama**: `Beban Marketing`
   - **Tipe**: `Beban`
   - **Sub Tipe**: `Beban Operasional`
   - **Deskripsi**: `Biaya promosi, iklan, dan marketing`
   - **Saldo Normal**: `Debit`
4. Klik **Simpan**

## Skenario 4: Input Saldo Awal (Migrasi)

**Situasi**: Anda pindah dari sistem lain dan perlu input saldo awal per 1 Januari 2025.

**Langkah-langkah**:

1. Siapkan neraca saldo dari sistem lama per tanggal cut-off
2. Klik menu **Transaksi** di sidebar
3. Klik tombol **Transaksi Baru**
4. Pilih template **Saldo Awal** atau buat jurnal manual
5. Untuk setiap akun dengan saldo:
   - Input jurnal debit/kredit sesuai saldo
   - Gunakan tanggal cut-off sebagai tanggal transaksi
   - Keterangan: "Saldo awal migrasi"

**Contoh Jurnal Saldo Awal**:
```
Tanggal: 31 Desember 2024 (atau 1 Januari 2025)

Debit  : Kas Kecil          Rp  5.000.000
Debit  : Bank BCA           Rp 50.000.000
Debit  : Piutang Usaha      Rp 20.000.000
Debit  : Peralatan          Rp 30.000.000
Kredit : Hutang Usaha       Rp 15.000.000
Kredit : Modal              Rp 90.000.000
```

**Tips**:
- Pastikan total debit = total kredit
- Verifikasi dengan Neraca Saldo setelah input

## Skenario 5: Import Bagan Akun dari File

**Situasi**: Anda memiliki daftar akun dalam file Excel/JSON dan ingin mengimpornya.

**Langkah-langkah**:

1. Siapkan file dengan format yang sesuai:
   - JSON atau Excel
   - Kolom: kode, nama, tipe, sub_tipe, deskripsi
2. Klik menu **Akun** di sidebar
3. Klik tombol **Import**
4. Pilih file yang akan diimpor
5. Review preview data
6. Klik **Import**

**Format JSON**:
```json
[
  {
    "kode": "1.1.12",
    "nama": "Bank Mandiri",
    "tipe": "ASET",
    "subTipe": "KAS_BANK",
    "deskripsi": "Rekening operasional"
  }
]
```

## Skenario 6: Nonaktifkan Akun yang Tidak Dipakai

**Situasi**: Ada akun bawaan yang tidak relevan untuk bisnis Anda.

**Langkah-langkah**:

1. Klik menu **Akun** di sidebar
2. Cari akun yang ingin dinonaktifkan
3. Klik akun tersebut
4. Klik **Edit**
5. Nonaktifkan toggle **Status Aktif**
6. Klik **Simpan**

**Efek**:
- Akun tidak muncul di dropdown saat membuat transaksi
- Data historis tetap tersimpan
- Akun tetap muncul di laporan jika ada saldo

## Skenario 7: Setup Telegram Bot

**Situasi**: Anda ingin menggunakan fitur kirim struk via Telegram.

**Untuk Administrator (Server Setup)**:

Lihat [Setup Telegram Bot](55-setup-telegram.md) untuk:
- Membuat bot di BotFather
- Konfigurasi environment variables
- Mendaftarkan webhook

**Untuk End User (Menghubungkan Akun)**:

1. Klik menu **Pengaturan** di sidebar
2. Pilih tab **Telegram**
3. Klik **Hubungkan Telegram**
4. Salin kode verifikasi yang muncul
5. Buka Telegram, cari bot yang dikonfigurasi
6. Kirim kode verifikasi ke bot
7. Verifikasi koneksi berhasil

Lihat [Telegram Receipt](13-telegram-receipt.md) untuk panduan penggunaan.

## Skenario 8: Backup Data

**Situasi**: Anda ingin membuat backup data sebelum melakukan perubahan besar.

**Langkah-langkah**:

1. Klik menu **Pengaturan** di sidebar
2. Pilih tab **Backup**
3. Klik **Buat Backup**
4. File backup akan terunduh

**Kapan Perlu Backup**:
- Sebelum import data baru
- Sebelum tutup buku tahunan
- Secara berkala (minimal bulanan)

## Tips

1. **Mulai sederhana** - Gunakan akun bawaan dulu, tambah sesuai kebutuhan
2. **Konsisten** - Ikuti struktur kode akun yang sudah ada
3. **Dokumentasi** - Isi deskripsi akun dengan jelas
4. **Test dulu** - Coba input beberapa transaksi untuk memastikan setup benar

## Troubleshooting

| Masalah | Solusi |
|---------|--------|
| Kode akun sudah dipakai | Gunakan kode lain yang belum ada |
| Akun tidak muncul di dropdown | Pastikan akun aktif dan tipe sesuai |
| Neraca Saldo tidak balance setelah migrasi | Cek kembali jurnal saldo awal |

## Lihat Juga

- [Konsep Dasar](01-konsep-dasar.md) - Memahami struktur akun
- [Kelola Template](51-kelola-template.md) - Buat template untuk akun baru
- [Referensi Akun](91-referensi-akun.md) - Daftar akun standar
