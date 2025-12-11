# Tutorial Dasar Akuntansi

Bagian ini adalah **panduan utama** untuk pemilik bisnis yang belum memiliki latar belakang akuntansi. Tujuannya: memahami akuntansi dengan bahasa sederhana agar dapat menggunakan aplikasi dengan benar.

> **Catatan**: Konsep di sini bersifat universal - bisa diterapkan dengan aplikasi ini, Excel, atau bahkan pencatatan manual.

---

## Apa Itu Akuntansi?

Akuntansi adalah proses **mencatat, mengklasifikasi, dan melaporkan** transaksi keuangan bisnis.

**Tujuan akuntansi:**
- Mengetahui kondisi keuangan bisnis
- Membuat keputusan bisnis yang tepat
- Memenuhi kewajiban pelaporan pajak

![Dashboard](screenshots/dashboard.png)

---

## Persamaan Dasar Akuntansi

```
Aset = Kewajiban + Ekuitas
```

| Komponen | Pengertian | Contoh |
|----------|------------|--------|
| **Aset** | Apa yang dimiliki bisnis | Kas, piutang, peralatan |
| **Kewajiban** | Apa yang dihutang bisnis | Hutang vendor, hutang pajak |
| **Ekuitas** | Modal pemilik | Modal awal, laba ditahan |

Setiap transaksi harus menjaga keseimbangan persamaan ini.

---

## Debit dan Kredit

Setiap transaksi dicatat dalam dua sisi: **debit** dan **kredit**. Total debit harus sama dengan total kredit.

| Jenis Akun | Bertambah | Berkurang |
|------------|-----------|-----------|
| Aset | Debit | Kredit |
| Kewajiban | Kredit | Debit |
| Ekuitas | Kredit | Debit |
| Pendapatan | Kredit | Debit |
| Beban | Debit | Kredit |

### Contoh: Terima Pembayaran dari Klien

Anda menerima Rp 10.000.000 dari klien untuk jasa konsultasi.

| Akun | Debit | Kredit |
|------|-------|--------|
| Kas/Bank | 10.000.000 | |
| Pendapatan Jasa | | 10.000.000 |

- Kas (aset) bertambah → debit
- Pendapatan bertambah → kredit

### Contoh: Bayar Listrik

Anda membayar tagihan listrik Rp 500.000.

| Akun | Debit | Kredit |
|------|-------|--------|
| Beban Listrik | 500.000 | |
| Kas/Bank | | 500.000 |

- Beban bertambah → debit
- Kas (aset) berkurang → kredit

---

## Bagan Akun (Chart of Accounts)

Bagan akun adalah daftar semua akun yang digunakan untuk mencatat transaksi.

![Daftar Akun](screenshots/accounts-list.png)

### Struktur Kode Akun

| Kode | Jenis | Contoh |
|------|-------|--------|
| 1.x.xx | Aset | Kas, Bank, Piutang, Peralatan |
| 2.x.xx | Kewajiban | Hutang Usaha, Hutang Pajak |
| 3.x.xx | Ekuitas | Modal, Laba Ditahan |
| 4.x.xx | Pendapatan | Pendapatan Jasa, Pendapatan Lain |
| 5.x.xx | Beban | Gaji, Sewa, Listrik, Internet |

---

## Siklus Akuntansi

Siklus akuntansi adalah urutan langkah yang dilakukan berulang setiap periode:

```
1. Identifikasi Transaksi
       ↓
2. Catat di Jurnal
       ↓
3. Posting ke Buku Besar
       ↓
4. Susun Neraca Saldo
       ↓
5. Jurnal Penyesuaian
       ↓
6. Laporan Keuangan
       ↓
7. Jurnal Penutup
       ↓
8. Neraca Saldo Setelah Penutupan
```

Dalam aplikasi ini, langkah 2-3 dilakukan otomatis saat Anda mencatat transaksi.

---

## Transaksi Harian

### Mencatat Pendapatan

Setiap kali Anda **menerima uang**:

1. Buka menu **Transaksi**

![Daftar Transaksi](screenshots/transactions-list.png)

2. Klik **Transaksi Baru**

![Form Transaksi](screenshots/transactions-form.png)

3. Pilih template yang sesuai (contoh: "Pendapatan Jasa")
4. Isi field:
   - **Tanggal**: Tanggal uang diterima
   - **Jumlah**: Nominal yang diterima
   - **Keterangan**: Catatan untuk referensi
5. Review preview jurnal
6. Klik **Simpan & Posting**

![Detail Transaksi](screenshots/transactions-detail.png)

### Mencatat Pengeluaran

Setiap kali Anda **mengeluarkan uang**:

1. Buka menu **Transaksi**
2. Klik **Transaksi Baru**
3. Pilih template yang sesuai (contoh: "Beban Operasional")
4. Isi field:
   - **Tanggal**: Tanggal pembayaran
   - **Jumlah**: Nominal yang dibayar
   - **Keterangan**: Catatan (contoh: "Bayar listrik November")
5. Review preview jurnal
6. Klik **Simpan & Posting**

### Transfer Antar Akun

Saat memindahkan uang antar rekening:

1. Buka menu **Transaksi**
2. Klik **Transaksi Baru**
3. Pilih template "Transfer Antar Akun"
4. Isi:
   - **Dari Akun**: Rekening sumber
   - **Ke Akun**: Rekening tujuan
   - **Jumlah**: Nominal transfer
5. Klik **Simpan & Posting**

---

## Jurnal dan Buku Besar

### Apa Itu Jurnal?

Jurnal adalah catatan debit/kredit yang dihasilkan dari transaksi.

![Daftar Jurnal](screenshots/journals-list.png)

Aplikasi ini menggunakan pendekatan **transaction-centric** - Anda mencatat transaksi, sistem otomatis membuat jurnal yang benar.

### Status Transaksi

| Status | Arti |
|--------|------|
| **Draft** | Tersimpan tapi belum mempengaruhi saldo |
| **Posted** | Sudah diposting, mempengaruhi saldo |
| **Void** | Dibatalkan, jurnal reversal dibuat |

---

## Penyesuaian (Adjustments)

### Apa Itu Penyesuaian?

Penyesuaian adalah koreksi yang dilakukan di akhir periode untuk memastikan laporan keuangan akurat.

### Jenis Penyesuaian

| Jenis | Contoh | Akun yang Terlibat |
|-------|--------|-------------------|
| **Beban Dibayar Dimuka** | Sewa dibayar setahun | Beban Sewa, Sewa Dibayar Dimuka |
| **Beban Akrual** | Gaji belum dibayar | Beban Gaji, Hutang Gaji |
| **Penyusutan** | Depresiasi peralatan | Beban Penyusutan, Akum. Penyusutan |

### Jadwal Amortisasi

Untuk beban yang dibayar dimuka (sewa tahunan, asuransi, dll):

![Daftar Amortisasi](screenshots/amortization-list.png)

1. Buka menu **Amortisasi**
2. Klik **Amortisasi Baru**

![Form Amortisasi](screenshots/amortization-form.png)

3. Isi:
   - Nama jadwal
   - Total nilai
   - Tanggal mulai dan selesai
   - Akun beban
4. Sistem akan membuat jurnal amortisasi otomatis setiap bulan

---

## Laporan Keuangan

### Neraca Saldo (Trial Balance)

Daftar saldo semua akun. Total debit harus sama dengan total kredit.

![Neraca Saldo](screenshots/reports-trial-balance.png)

**Kapan digunakan**: Untuk validasi bahwa pembukuan balance.

### Laporan Laba Rugi (Income Statement)

Menampilkan pendapatan, beban, dan laba/rugi periode tertentu.

![Laba Rugi](screenshots/reports-income-statement.png)

**Struktur**:
```
Pendapatan          Rp 50.000.000
Total Beban         Rp 30.000.000
─────────────────────────────────
LABA BERSIH         Rp 20.000.000
```

### Neraca (Balance Sheet)

Menampilkan posisi keuangan pada tanggal tertentu.

![Neraca](screenshots/reports-balance-sheet.png)

**Struktur**:
```
ASET
  Aset Lancar           Rp 100.000.000
  Aset Tetap            Rp  50.000.000
Total Aset              Rp 150.000.000

KEWAJIBAN
  Hutang Usaha          Rp  20.000.000
Total Kewajiban         Rp  20.000.000

EKUITAS
  Modal                 Rp 100.000.000
  Laba Ditahan          Rp  30.000.000
Total Ekuitas           Rp 130.000.000

TOTAL KEWAJIBAN + EKUITAS Rp 150.000.000
```

**Persamaan yang harus balance**: Aset = Kewajiban + Ekuitas

---

## Tutup Buku (Fiscal Closing)

### Apa Itu Tutup Buku?

Proses akhir tahun untuk:
1. Menutup akun pendapatan dan beban (saldo menjadi nol)
2. Memindahkan laba/rugi ke akun Laba Ditahan

### Langkah Tutup Buku

1. Buka menu **Laporan** > **Penutupan Tahun Buku**

![Penutupan Tahun Buku](screenshots/reports-fiscal-closing.png)

2. Pilih tahun yang akan ditutup
3. Review preview jurnal penutup:
   - Tutup Pendapatan
   - Tutup Beban
   - Transfer ke Laba Ditahan
4. Klik **Eksekusi Penutupan**

### Jurnal Penutup yang Dibuat

**1. Tutup Pendapatan**
```
Dr. Pendapatan Jasa          xxx
    Cr. Laba Berjalan            xxx
```

**2. Tutup Beban**
```
Dr. Laba Berjalan            xxx
    Cr. Beban Gaji               xxx
    Cr. Beban Sewa               xxx
```

**3. Transfer ke Laba Ditahan**
```
Dr. Laba Berjalan            xxx
    Cr. Laba Ditahan             xxx
```

### Kapan Melakukan Tutup Buku?

Lakukan setelah:
- Semua transaksi tahun tersebut sudah dicatat
- Penyesuaian akhir tahun sudah dilakukan
- Penyusutan aset sudah dihitung
- Laporan keuangan sudah final

---

## Telegram Receipt

Fitur untuk mengirim foto struk via Telegram dan mencatat transaksi langsung.

### Cara Penggunaan

1. Buka Telegram, cari bot yang dikonfigurasi
2. Kirim foto struk
3. Bot akan meminta konfirmasi:
   - Template transaksi
   - Jumlah
   - Keterangan
4. Konfirmasi, transaksi akan tercatat

> **Catatan**: Fitur ini perlu dikonfigurasi dulu oleh administrator. Lihat [Setup Awal](01-setup-awal.md).

---

## Tips untuk Pemula

1. **Catat segera** - Catat transaksi sesegera mungkin setelah terjadi
2. **Simpan bukti** - Simpan struk, invoice, dan dokumen pendukung
3. **Gunakan template** - Aplikasi menyediakan template untuk transaksi umum
4. **Review rutin** - Periksa laporan minimal sekali seminggu
5. **Posting langsung** - Jangan tunda posting transaksi draft
6. **Rekonsiliasi** - Cocokkan saldo aplikasi dengan saldo bank secara berkala

---

## Istilah Penting

| Istilah | Artinya |
|---------|---------|
| **Transaksi** | Setiap kali uang masuk atau keluar |
| **Posting** | Menyimpan transaksi secara permanen |
| **Draft** | Transaksi yang masih bisa diedit |
| **Akun** | Kategori untuk mengelompokkan uang |
| **Template** | Format siap pakai untuk transaksi |
| **Jurnal** | Catatan debit/kredit dari transaksi |
| **Debit** | Kolom kiri (biasanya = bertambah untuk aset) |
| **Kredit** | Kolom kanan (biasanya = berkurang untuk aset) |
| **Neraca** | Laporan posisi keuangan |
| **Laba Rugi** | Laporan keuntungan/kerugian |

Lihat [Glosarium](12-lampiran-glosarium.md) untuk daftar istilah lengkap.

---

## Lihat Juga

- [Setup Awal](01-setup-awal.md) - Konfigurasi awal aplikasi
- [Aset Tetap](03-aset-tetap.md) - Pencatatan dan depresiasi aset
- [Perpajakan](04-perpajakan.md) - Transaksi pajak
- [Referensi Template](12-lampiran-template.md) - Daftar template transaksi
