# Transaksi Berulang (Recurring Transactions)

Panduan lengkap untuk fitur transaksi berulang: penjadwalan, eksekusi otomatis, dan pengelolaan jadwal.

## Konsep Transaksi Berulang

### Apa Itu Transaksi Berulang

Transaksi Berulang adalah fitur untuk mengotomatisasi pencatatan transaksi yang terjadi secara rutin dengan jumlah tetap. Contoh: sewa kantor bulanan, biaya langganan, pembayaran retainer, atau cicilan.

Sistem menjalankan scheduler harian pada pukul **05:00** yang memproses seluruh transaksi berulang yang sudah jatuh tempo, membuat transaksi baru secara otomatis, dan mencatat hasilnya.

### Frekuensi yang Didukung

| Frekuensi | Indonesia | Keterangan |
|-----------|-----------|------------|
| DAILY | Harian | Setiap hari |
| WEEKLY | Mingguan | Hari tertentu dalam minggu (Senin-Minggu) |
| MONTHLY | Bulanan | Tanggal tertentu dalam bulan (1-28) |
| QUARTERLY | Triwulanan | Setiap 3 bulan pada tanggal tertentu |
| YEARLY | Tahunan | Setiap tahun pada tanggal tertentu |

### Status Transaksi Berulang

| Status | Indonesia | Warna Badge | Keterangan |
|--------|-----------|-------------|------------|
| ACTIVE | Aktif | Hijau | Dijadwalkan dan dieksekusi otomatis |
| PAUSED | Dijeda | Kuning | Dihentikan sementara, bisa dilanjutkan |
| COMPLETED | Selesai | Abu-abu | Tidak akan dieksekusi lagi |

---

## Membuat Transaksi Berulang

### Halaman Daftar

Buka menu **Akuntansi** > **Transaksi Berulang** di sidebar, atau navigasi langsung ke `/recurring`.

![Daftar Transaksi Berulang](screenshots/recurring/list.png)

Halaman menampilkan:
- **Kartu ringkasan:** jumlah Aktif, Dijeda, Selesai
- **Filter status:** filter berdasarkan status
- **Tabel:** nama, template, frekuensi, jumlah, jadwal berikut, total eksekusi, status

### Formulir Pembuatan

Klik tombol **+ Transaksi Berulang Baru** untuk membuka formulir.

Field yang harus diisi:

| Field | Wajib | Keterangan |
|-------|-------|------------|
| Nama | Ya | Nama deskriptif (contoh: "Sewa Kantor Bulanan") |
| Template Jurnal | Ya | Template yang digunakan untuk membuat transaksi |
| Jumlah (Rp) | Ya | Nominal transaksi |
| Deskripsi | Ya | Deskripsi yang akan muncul di transaksi |
| Frekuensi | Ya | Harian/Mingguan/Bulanan/Triwulanan/Tahunan |
| Tanggal | Kondisional | Tanggal 1-28 untuk Bulanan/Triwulanan/Tahunan |
| Hari | Kondisional | Senin-Minggu untuk Mingguan |
| Tanggal Mulai | Ya | Tanggal pertama eksekusi |
| Tanggal Selesai | Tidak | Kosongkan jika tanpa batas waktu |
| Maks. Eksekusi | Tidak | Jumlah maksimum eksekusi, kosongkan jika tanpa batas |

Opsi tambahan:
- **Lewati Akhir Pekan:** Jika jadwal jatuh pada Sabtu/Minggu, eksekusi dipindah ke Senin berikutnya
- **Posting Otomatis:** Jika dicentang, transaksi langsung diposting. Jika tidak, disimpan sebagai Draft

---

## Detail dan Pengelolaan

### Halaman Detail

Klik nama transaksi berulang di daftar untuk melihat detail.

![Detail Transaksi Berulang](screenshots/recurring/detail.png)

Halaman menampilkan:
- **Informasi:** jumlah, frekuensi, deskripsi, tanggal mulai/selesai, jadwal berikut, total eksekusi
- **Jadwal 5 Eksekusi Berikutnya:** preview tanggal-tanggal eksekusi mendatang
- **Riwayat Eksekusi:** log setiap eksekusi (Berhasil/Gagal/Dilewati) beserta link ke transaksi yang dibuat

### Aksi yang Tersedia

| Aksi | Kondisi | Keterangan |
|------|---------|------------|
| Edit | Status bukan Selesai | Mengubah konfigurasi |
| Jeda | Status Aktif | Menghentikan sementara eksekusi |
| Lanjutkan | Status Dijeda | Melanjutkan eksekusi, jadwal dihitung ulang dari hari ini |
| Selesaikan | Status bukan Selesai | Menghentikan permanen |
| Hapus | Semua status | Soft delete, transaksi yang sudah dibuat tidak terpengaruh |

---

## Eksekusi Otomatis (Scheduler)

### Jadwal Eksekusi

Scheduler berjalan setiap hari pukul **05:00** (sebelum amortisasi pukul 06:00). Proses:

1. Query semua transaksi berulang dengan status **ACTIVE** dan `nextRunDate <= hari ini`
2. Untuk setiap item:
   - Jika **Lewati Akhir Pekan** aktif dan tanggal jatuh pada Sabtu/Minggu: dipindah ke Senin
   - Buat transaksi baru dari template + jumlah + deskripsi + mapping akun
   - Jika **Posting Otomatis** aktif: posting transaksi
   - Catat log eksekusi (SUCCESS)
   - Update `nextRunDate`, `lastRunDate`, `totalRuns`
3. Jika `maxOccurrences` tercapai atau `nextRunDate` melewati `endDate`: set status COMPLETED
4. Jika terjadi error: catat log (FAILED) dengan pesan error, lanjut ke item berikutnya

### Status Log Eksekusi

| Status | Indonesia | Keterangan |
|--------|-----------|------------|
| SUCCESS | Berhasil | Transaksi berhasil dibuat |
| FAILED | Gagal | Terjadi error saat pembuatan transaksi |
| SKIPPED | Dilewati | Dilewati karena jatuh pada akhir pekan |

---

## Hak Akses

| Permission | Admin | Owner | Accountant | Staff | Auditor |
|-----------|-------|-------|------------|-------|---------|
| RECURRING_VIEW | Ya | Ya | Ya | Ya | Ya |
| RECURRING_CREATE | Ya | Ya | Ya | — | — |
| RECURRING_EDIT | Ya | Ya | Ya | — | — |
| RECURRING_DELETE | Ya | Ya | Ya | — | — |
