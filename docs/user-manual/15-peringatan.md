# Peringatan (Smart Alerts)

Panduan lengkap untuk fitur peringatan otomatis: konfigurasi aturan, evaluasi harian, dan pengelolaan peringatan.

## Konsep Peringatan

### Apa Itu Peringatan

Peringatan (Smart Alerts) adalah sistem notifikasi otomatis yang memantau kondisi keuangan perusahaan dan memberitahu pengguna jika ada anomali atau risiko yang perlu ditindaklanjuti. Sistem mengevaluasi 7 jenis aturan setiap hari dan menghasilkan event peringatan ketika ambang batas terlampaui.

### Jenis Peringatan

| Tipe | Nama Indonesia | Deskripsi | Satuan Ambang Batas |
|------|---------------|-----------|---------------------|
| CASH_LOW | Kas Rendah | Saldo kas + bank di bawah ambang batas | Rp |
| RECEIVABLE_OVERDUE | Piutang Jatuh Tempo | Ada piutang yang sudah jatuh tempo | — (0 = aktif) |
| EXPENSE_SPIKE | Lonjakan Biaya | Biaya bulan ini naik melebihi X% dari rata-rata 3 bulan sebelumnya | % |
| PROJECT_COST_OVERRUN | Proyek Melebihi Anggaran | Ada proyek yang biayanya melebihi anggaran | — (0 = aktif) |
| PROJECT_MARGIN_DROP | Margin Proyek Turun | Margin proyek turun di bawah X% | % |
| COLLECTION_SLOWDOWN | Penagihan Melambat | Rata-rata hari penagihan melebihi X hari | hari |
| CLIENT_CONCENTRATION | Konsentrasi Klien | Satu klien menyumbang lebih dari X% pendapatan | % |
| PAYROLL_UNPOSTED | Payroll Belum Diposting | Payroll berstatus APPROVED belum diposting X hari setelah akhir periode | hari |

### Tingkat Keparahan (Severity)

| Severity | Indonesia | Warna Badge | Keterangan |
|----------|-----------|-------------|------------|
| INFO | Informasi | Biru | Kondisi yang perlu diketahui, tidak mendesak |
| WARNING | Peringatan | Kuning | Kondisi yang perlu segera diperhatikan |
| CRITICAL | Kritis | Merah | Kondisi darurat yang harus ditindaklanjuti segera |

### Evaluasi Harian

Sistem menjalankan evaluasi seluruh aturan yang aktif setiap hari pukul **08:00** secara otomatis. Mekanisme:

1. Setiap aturan yang `enabled = true` dievaluasi
2. Jika kondisi ambang batas terpenuhi, event peringatan dibuat
3. **Deduplikasi 24 jam:** jika sudah ada event yang belum dikonfirmasi untuk aturan yang sama dalam 24 jam terakhir, event baru tidak dibuat (mencegah duplikasi peringatan)

---

## Konfigurasi Peringatan

### Halaman Konfigurasi

Buka menu **Laporan** > **Peringatan**, lalu klik tab **Konfigurasi** atau navigasi langsung ke `/alerts/config`.

![Konfigurasi Peringatan](screenshots/alerts/config.png)

Halaman menampilkan daftar 7 aturan peringatan bawaan. Setiap aturan menampilkan:

- **Nama tipe:** Nama Indonesia dari jenis peringatan
- **Deskripsi:** Penjelasan kondisi yang dievaluasi
- **Ambang Batas (Threshold):** Nilai numerik yang menjadi batas pemicu
- **Status Aktif:** Checkbox untuk mengaktifkan/menonaktifkan aturan

### Mengubah Ambang Batas

1. Isi nilai baru pada field **Ambang Batas** sesuai jenis aturan:
   - Kas Rendah: nominal dalam Rupiah (contoh: `10000000` = Rp 10 juta)
   - Lonjakan Biaya: persentase (contoh: `30` = 30%)
   - Margin Proyek Turun: persentase (contoh: `10` = 10%)
   - Penagihan Melambat: jumlah hari (contoh: `30` = 30 hari)
   - Konsentrasi Klien: persentase (contoh: `50` = 50%)
   - Piutang Jatuh Tempo / Proyek Melebihi Anggaran: `0` berarti selalu aktif
2. Klik **Simpan** pada aturan yang diubah

### Menonaktifkan Aturan

Hapus centang pada checkbox **Aktif**, lalu klik **Simpan**. Aturan yang nonaktif tidak akan dievaluasi pada jadwal harian.

**Permission yang diperlukan:** `ALERT_CONFIG`

---

## Peringatan Aktif

### Halaman Peringatan Aktif

Buka menu **Laporan** > **Peringatan** atau navigasi ke `/alerts`.

![Peringatan Aktif](screenshots/alerts/active.png)

Halaman menampilkan daftar peringatan yang **belum dikonfirmasi (unacknowledged)**. Setiap peringatan menampilkan:

- **Badge severity:** Warna menunjukkan tingkat keparahan (biru = INFO, kuning = WARNING, merah = CRITICAL)
- **Tipe peringatan:** Nama Indonesia dari jenis peringatan
- **Pesan:** Detail kondisi yang memicu peringatan
- **Waktu:** Timestamp kapan peringatan dipicu
- **Tombol Konfirmasi:** Untuk menandai peringatan sudah ditindaklanjuti

### Mengonfirmasi Peringatan

Klik tombol **Konfirmasi** pada peringatan yang sudah ditindaklanjuti. Peringatan yang dikonfirmasi berpindah ke halaman riwayat dan tidak lagi muncul di daftar aktif.

**Permission yang diperlukan:** `ALERT_ACKNOWLEDGE`

---

## Widget Dashboard

### Widget Peringatan

Widget peringatan ditampilkan di halaman **Dashboard** (`/dashboard`), dimuat secara dinamis via HTMX.

![Widget Peringatan Dashboard](screenshots/alerts/dashboard-widget.png)

Widget menampilkan:
- **Jumlah peringatan per severity:** Berapa peringatan aktif bertipe INFO, WARNING, dan CRITICAL
- **5 peringatan terbaru:** Daftar singkat peringatan aktif dengan badge severity dan pesan
- **Link ke halaman peringatan:** Klik untuk melihat seluruh peringatan aktif

Widget ini membantu memantau kondisi keuangan tanpa harus membuka halaman peringatan secara terpisah.

---

## Riwayat Peringatan

### Halaman Riwayat

Buka navigasi ke `/alerts/history` atau klik tab **Riwayat** dari halaman peringatan.

![Riwayat Peringatan](screenshots/alerts/history.png)

Halaman menampilkan seluruh peringatan (aktif dan yang sudah dikonfirmasi) dalam tabel paginated. Informasi yang ditampilkan:

- **Tipe:** Jenis peringatan
- **Severity:** Tingkat keparahan
- **Pesan:** Detail peringatan
- **Waktu Trigger:** Kapan peringatan dipicu
- **Status:** Dikonfirmasi (dengan nama dan waktu) atau belum

**Permission yang diperlukan:** `ALERT_VIEW`

---

## Contoh Kasus

Walk-through penggunaan fitur peringatan untuk PT ArtiVisi Intermedia.

### Skenario: Kas Rendah

1. **Konfigurasi:** Aturan "Kas Rendah" diset dengan ambang batas Rp 10.000.000, status aktif
2. **Evaluasi harian:** Pukul 08:00, sistem menghitung total saldo akun kas + bank
3. **Trigger:** Saldo total = Rp 8.500.000 (di bawah Rp 10.000.000) → event WARNING dibuat
4. **Notifikasi:** Peringatan muncul di widget dashboard dan halaman peringatan aktif
5. **Tindak lanjut:** Staff keuangan melihat peringatan, melakukan penagihan piutang yang jatuh tempo
6. **Konfirmasi:** Setelah saldo membaik, staff mengklik **Konfirmasi** pada peringatan tersebut
7. **Riwayat:** Peringatan tercatat di riwayat dengan nama pengonfirmasi dan waktu konfirmasi

### Skenario: Lonjakan Biaya

1. **Konfigurasi:** Aturan "Lonjakan Biaya" diset 30% (biaya naik lebih dari 30% dibanding rata-rata 3 bulan sebelumnya)
2. **Evaluasi:** Rata-rata biaya 3 bulan = Rp 50.000.000, biaya bulan ini = Rp 70.000.000 (naik 40%)
3. **Trigger:** 40% > 30% → event WARNING dibuat
4. **Investigasi:** Pengguna memeriksa detail biaya bulan ini, menemukan pembelian peralatan baru yang bersifat one-time
5. **Konfirmasi:** Peringatan dikonfirmasi setelah diverifikasi sebagai pengeluaran wajar

---

## Tips

1. **Review ambang batas secara berkala** — Sesuaikan nilai ambang batas setiap bulan atau kuartal sesuai kondisi bisnis yang berubah.
2. **Konfirmasi peringatan segera** — Peringatan yang tidak dikonfirmasi akan mencegah duplikasi (deduplikasi 24 jam), tetapi juga bisa menutupi kondisi baru jika dibiarkan terlalu lama.
3. **Nonaktifkan tipe yang tidak relevan** — Jika bisnis tidak memiliki proyek (misalnya retail), nonaktifkan aturan PROJECT_COST_OVERRUN dan PROJECT_MARGIN_DROP.
4. **Periksa widget dashboard setiap hari** — Widget menampilkan ringkasan cepat tanpa perlu membuka halaman terpisah.
5. **Gunakan riwayat untuk analisis tren** — Pola peringatan yang berulang mengindikasikan masalah struktural yang perlu diperbaiki.

---

## Lihat Juga

- [Setup Awal & Import Seed Data](01-setup-awal.md)
- [Tutorial Akuntansi Dasar](02-tutorial-akuntansi.md) — Laporan keuangan yang menjadi basis evaluasi
- [Industri Jasa IT](07-industri-jasa.md) — Proyek dan klien yang dipantau oleh peringatan
- [Rekonsiliasi Bank](14-rekonsiliasi-bank.md) — Saldo bank yang termasuk dalam evaluasi kas
