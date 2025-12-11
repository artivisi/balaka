# Setup Awal & Administrasi

Bagian ini ditujukan untuk **administrator sistem** yang bertanggung jawab menyiapkan aplikasi untuk pertama kali.

## Tampilan Bare-Bones

Setelah instalasi, aplikasi menampilkan tampilan bare-bones dengan data minimal:
- User admin default (admin/admin)
- Tahun fiskal aktif
- Tidak ada COA, template, atau data master lainnya

![Dashboard Bare Bones](screenshots/dashboard.png)

## Import Industry Seed Data

### Konsep Seed Data

Seed data adalah paket data awal yang berisi:
- Bagan Akun (Chart of Accounts) sesuai industri
- Template transaksi standar
- Komponen gaji (jika applicable)
- Deadline pajak
- Kategori aset tetap

### Pilihan Industry Seed

| Industry | Deskripsi | Fitur Utama |
|----------|-----------|-------------|
| IT Services (PKP) | Perusahaan jasa IT dengan kewajiban PPN | Client, Project, Milestone, Invoice |
| Online Seller | Penjual online marketplace | Produk, Inventory FIFO, Multi-marketplace |
| Coffee Shop | Usaha kopi & pastry | BOM, Production Order, HPP |
| Campus | Institusi pendidikan | Mahasiswa, SPP, Beasiswa |

### Langkah Import

1. Buka menu **Pengaturan** di sidebar
2. Pilih tab **Import Data**
3. Klik **Pilih File** dan pilih file ZIP seed data
4. Review preview data yang akan diimpor
5. Klik **Import**

> **Catatan**: Proses import akan menggantikan data existing. Pastikan tidak ada data penting sebelum import.

---

## Feature Overview

Setelah import seed data, fitur yang tersedia tergantung jenis industri:

### Fitur Umum (Semua Industri)

| Fitur | Menu | Deskripsi |
|-------|------|-----------|
| Transaksi | Transaksi | Catat pendapatan dan pengeluaran |
| Laporan | Laporan | Neraca, Laba Rugi, Arus Kas |
| Pajak | Pajak | PPN, PPh, Kalender Pajak |
| Penggajian | Penggajian | Payroll, BPJS, PPh 21 |
| Aset Tetap | Aset | Depresiasi, Kategori Aset |

### Fitur Industri Jasa

| Fitur | Menu | Deskripsi |
|-------|------|-----------|
| Client | Klien | Data klien dan kontak |
| Project | Proyek | Proyek dengan milestone |
| Invoice | Invoice | Penagihan ke klien |
| Profitabilitas | Laporan | Profit per klien/proyek |

### Fitur Industri Dagang

| Fitur | Menu | Deskripsi |
|-------|------|-----------|
| Produk | Inventori > Produk | Master produk dengan harga |
| Stok | Inventori > Stok | Saldo stok per lokasi |
| Transaksi Inventori | Inventori > Transaksi | Pembelian, Penjualan, Adjustment |
| Laporan Stok | Inventori > Laporan | Kartu stok, Valuasi |

---

## Master Data

### Bagan Akun (Chart of Accounts)

Buka menu **Akun** untuk melihat daftar akun.

![Daftar Akun](screenshots/accounts-list.png)

#### Struktur Kode Akun

| Prefix | Kategori | Contoh |
|--------|----------|--------|
| 1.x.xx | Aset | Kas, Bank, Piutang, Peralatan |
| 2.x.xx | Kewajiban | Hutang Usaha, Hutang Pajak |
| 3.x.xx | Ekuitas | Modal, Laba Ditahan |
| 4.x.xx | Pendapatan | Pendapatan Jasa, Penjualan |
| 5.x.xx | Beban | Gaji, Sewa, Listrik |

#### Tambah Akun Baru

1. Klik tombol **Akun Baru**
2. Isi form:

![Form Akun](screenshots/accounts-form.png)

| Field | Keterangan |
|-------|------------|
| Kode | Kode unik mengikuti struktur |
| Nama | Nama akun |
| Tipe | Aset/Kewajiban/Ekuitas/Pendapatan/Beban |
| Sub Tipe | Kategori detail |
| Saldo Normal | Debit/Kredit |

3. Klik **Simpan**

### Template Transaksi

Template menentukan akun-akun yang terlibat dalam suatu jenis transaksi.

![Daftar Template](screenshots/templates-list.png)

Lihat [Referensi Template](12-lampiran-template.md) untuk daftar lengkap template standar.

---

## User Management

### Melihat Daftar Pengguna

Buka menu **Pengguna** di sidebar.

![Daftar Pengguna](screenshots/users-list.png)

### Tambah Pengguna Baru

1. Klik tombol **Pengguna Baru**

![Form Pengguna](screenshots/users-form.png)

2. Isi form:

| Field | Keterangan |
|-------|------------|
| Username | Login ID (unik) |
| Nama Lengkap | Nama tampilan |
| Email | Alamat email |
| Password | Password minimal 12 karakter |
| Role | ADMIN/OWNER/ACCOUNTANT/STAFF/AUDITOR/EMPLOYEE |

3. Klik **Simpan**

### Role dan Permission

| Role | Deskripsi | Permission |
|------|-----------|------------|
| ADMIN | Administrator sistem | Full access |
| OWNER | Pemilik bisnis | Full access kecuali user management |
| ACCOUNTANT | Akuntan | Transaksi, Laporan, Pajak |
| STAFF | Staff operasional | Transaksi, View laporan |
| AUDITOR | Auditor | View only |
| EMPLOYEE | Karyawan | Self-service saja |

---

## Telegram Integration

### Untuk Administrator

#### 1. Buat Bot di BotFather

1. Buka Telegram, cari @BotFather
2. Kirim `/newbot`
3. Ikuti instruksi untuk membuat bot
4. Catat **Bot Token** yang diberikan

#### 2. Konfigurasi Environment

Tambahkan ke environment variables server:

```
TELEGRAM_BOT_TOKEN=your_bot_token_here
TELEGRAM_WEBHOOK_URL=https://your-domain.com/api/telegram/webhook
```

#### 3. Daftarkan Webhook

```bash
curl -X POST "https://api.telegram.org/bot{your_token}/setWebhook" \
  -d "url=https://your-domain.com/api/telegram/webhook"
```

### Untuk End User

1. Buka menu **Pengaturan** > **Telegram**
2. Klik **Hubungkan Telegram**
3. Salin kode verifikasi
4. Buka Telegram, cari bot yang dikonfigurasi
5. Kirim kode verifikasi ke bot

Setelah terhubung, Anda dapat mengirim foto struk via Telegram.

Lihat [Tutorial Akuntansi](02-tutorial-akuntansi.md) bagian Telegram Receipt untuk panduan penggunaan.

---

## Periode Fiskal

### Konsep

Periode fiskal adalah periode akuntansi perusahaan. Aplikasi menggunakan sistem 12 bulan per tahun fiskal.

### Melihat Periode Fiskal

Buka menu **Periode Fiskal** di sidebar.

![Daftar Periode Fiskal](screenshots/fiscal-periods-list.png)

### Status Periode

| Status | Arti |
|--------|------|
| OPEN | Periode aktif, transaksi dapat dicatat |
| CLOSED | Periode ditutup, transaksi tidak dapat dicatat |

---

## Backup Data

### Manual Backup

1. Buka menu **Pengaturan** > **Backup**
2. Klik **Buat Backup**
3. File backup akan terunduh

### Kapan Perlu Backup

- Sebelum import data baru
- Sebelum tutup buku tahunan
- Secara berkala (minimal bulanan)

---

## Tips Setup

1. **Mulai dari seed data** - Jangan setup dari kosong, gunakan seed data industri yang sesuai
2. **Review COA** - Pastikan bagan akun sesuai kebutuhan bisnis
3. **Test transaksi** - Coba input beberapa transaksi untuk memastikan setup benar
4. **Dokumentasi** - Catat perubahan konfigurasi yang dilakukan

## Lihat Juga

- [Tutorial Dasar Akuntansi](02-tutorial-akuntansi.md) - Pahami konsep dan workflow
- [Keamanan & Kepatuhan](11-keamanan-kepatuhan.md) - Konfigurasi keamanan
- [Referensi Akun](12-lampiran-akun.md) - Daftar akun standar
