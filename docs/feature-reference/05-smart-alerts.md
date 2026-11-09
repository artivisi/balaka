# Peringatan (Smart Alerts)

Referensi layar fitur peringatan otomatis: konfigurasi aturan, peringatan aktif, widget dashboard, dan riwayat peringatan.

---

## Jenis Peringatan

Sistem menyediakan 7 jenis peringatan bawaan:

| Tipe | Nama | Deskripsi | Satuan Ambang Batas |
|------|------|-----------|---------------------|
| CASH_LOW | Kas Rendah | Saldo kas + bank di bawah ambang batas | Rp |
| RECEIVABLE_OVERDUE | Piutang Jatuh Tempo | Ada piutang yang sudah jatuh tempo | 0 = selalu aktif |
| EXPENSE_SPIKE | Lonjakan Biaya | Biaya bulan ini naik melebihi X% dari rata-rata 3 bulan sebelumnya | % |
| PROJECT_COST_OVERRUN | Proyek Melebihi Anggaran | Ada proyek yang biayanya melebihi anggaran | 0 = selalu aktif |
| PROJECT_MARGIN_DROP | Margin Proyek Turun | Margin proyek turun di bawah X% | % |
| COLLECTION_SLOWDOWN | Penagihan Melambat | Rata-rata hari penagihan melebihi X hari | hari |
| CLIENT_CONCENTRATION | Konsentrasi Klien | Satu klien menyumbang lebih dari X% pendapatan | % |
| PAYROLL_UNPOSTED | Payroll Belum Diposting | Payroll berstatus APPROVED belum diposting X hari setelah akhir periode | hari |

## Tingkat Keparahan (Severity)

| Severity | Nama | Warna Badge | Keterangan |
|----------|------|-------------|------------|
| INFO | Informasi | Biru | Kondisi yang perlu diketahui, tidak mendesak |
| WARNING | Peringatan | Kuning | Kondisi yang perlu segera diperhatikan |
| CRITICAL | Kritis | Merah | Kondisi darurat yang harus ditindaklanjuti segera |

---

## Konfigurasi Peringatan

**Navigasi:** Sidebar > **Laporan** > **Peringatan** > tab **Konfigurasi**

**URL:** `/alerts/config`

**Permission:** `ALERT_CONFIG`

### Daftar Aturan

Halaman menampilkan 7 aturan peringatan bawaan. Setiap aturan menampilkan:

| Kolom | Keterangan |
|-------|------------|
| Nama Tipe | Nama Indonesia dari jenis peringatan |
| Deskripsi | Penjelasan kondisi yang dievaluasi |
| Ambang Batas (Threshold) | Nilai numerik batas pemicu |
| Status Aktif | Checkbox aktif/nonaktif |

### Mengubah Ambang Batas

1. Isi nilai baru pada field **Ambang Batas** sesuai jenis aturan:
   - **Kas Rendah:** Nominal dalam Rupiah (contoh: `10000000` = Rp 10 juta)
   - **Lonjakan Biaya:** Persentase (contoh: `30` = 30%)
   - **Margin Proyek Turun:** Persentase (contoh: `10` = 10%)
   - **Penagihan Melambat:** Jumlah hari (contoh: `30` = 30 hari)
   - **Konsentrasi Klien:** Persentase (contoh: `50` = 50%)
   - **Piutang Jatuh Tempo / Proyek Melebihi Anggaran:** Nilai `0` berarti selalu aktif jika ada kasus
2. Klik **Simpan** pada aturan yang diubah

### Menonaktifkan Aturan

Hapus centang pada checkbox **Aktif**, lalu klik **Simpan**. Aturan nonaktif tidak dievaluasi oleh scheduler harian.

---

## Peringatan Aktif

**Navigasi:** Sidebar > **Laporan** > **Peringatan**

**URL:** `/alerts`

**Permission:** `ALERT_VIEW`

### Daftar Peringatan Aktif

Halaman menampilkan peringatan yang **belum dikonfirmasi** (unacknowledged). Setiap peringatan menampilkan:

| Elemen | Keterangan |
|--------|------------|
| Badge severity | Biru (INFO), kuning (WARNING), merah (CRITICAL) |
| Tipe peringatan | Nama Indonesia dari jenis peringatan |
| Pesan | Detail kondisi yang memicu peringatan |
| Waktu | Timestamp kapan peringatan dipicu |
| Tombol Konfirmasi | Untuk menandai sudah ditindaklanjuti |

### Mengonfirmasi Peringatan

Klik tombol **Konfirmasi** pada peringatan yang sudah ditindaklanjuti.

Perilaku:
- Peringatan berpindah ke halaman riwayat
- Tidak lagi muncul di daftar aktif
- Nama pengonfirmasi dan waktu konfirmasi dicatat

**Permission:** `ALERT_ACKNOWLEDGE`

---

## Widget Dashboard

**Navigasi:** Sidebar > **Dashboard**

**URL:** `/dashboard`

Widget peringatan dimuat secara dinamis via HTMX di halaman dashboard.

### Isi Widget

| Elemen | Keterangan |
|--------|------------|
| Jumlah per severity | Berapa peringatan aktif bertipe INFO, WARNING, CRITICAL |
| 5 peringatan terbaru | Daftar singkat dengan badge severity dan pesan |
| Link "Lihat semua" | Menuju halaman peringatan aktif (`/alerts`) |

---

## Riwayat Peringatan

**Navigasi:** Halaman peringatan > tab **Riwayat**

**URL:** `/alerts/history`

**Permission:** `ALERT_VIEW`

### Tabel Riwayat

Tabel paginated menampilkan seluruh peringatan (aktif dan yang sudah dikonfirmasi):

| Kolom | Keterangan |
|-------|------------|
| Tipe | Jenis peringatan |
| Severity | Tingkat keparahan (badge berwarna) |
| Pesan | Detail peringatan |
| Waktu Trigger | Kapan peringatan dipicu |
| Status | Dikonfirmasi (nama + waktu) atau belum |

---

## Evaluasi Harian

Scheduler berjalan otomatis setiap hari pukul **08:00** (dikonfigurasi via `app.alerts.schedule`).

Mekanisme:
1. Setiap aturan yang `enabled = true` dievaluasi
2. Jika kondisi ambang batas terpenuhi, event peringatan dibuat
3. **Deduplikasi 24 jam:** Jika sudah ada event yang belum dikonfirmasi untuk aturan yang sama dalam 24 jam terakhir, event baru tidak dibuat

---

## Hak Akses

| Permission | Keterangan |
|-----------|------------|
| `ALERT_VIEW` | Melihat peringatan aktif dan riwayat |
| `ALERT_CONFIG` | Mengubah ambang batas dan status aktif aturan |
| `ALERT_ACKNOWLEDGE` | Mengonfirmasi (acknowledge) peringatan |

---

Selanjutnya: [Label Transaksi (Tags)](06-tags.md)
