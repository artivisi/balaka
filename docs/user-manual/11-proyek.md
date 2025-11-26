# Manajemen Proyek

## Pengertian

Fitur manajemen proyek memungkinkan Anda melacak proyek bisnis, milestone, dan termin pembayaran. Semua transaksi dapat dihubungkan ke proyek untuk analisis profitabilitas.

## Status Proyek

| Status | Warna | Keterangan |
|--------|-------|------------|
| Active | Hijau | Proyek sedang berjalan |
| Completed | Biru | Proyek selesai, masih tampil di laporan |
| Archived | Abu-abu | Proyek diarsipkan, tidak tampil di dropdown |

## Melihat Daftar Proyek

1. Klik menu **Proyek** di sidebar
2. Gunakan filter untuk menyaring:
   - **Status** - Filter berdasarkan status proyek
   - **Klien** - Filter berdasarkan klien
   - **Pencarian** - Cari berdasarkan nama atau kode

## Informasi Proyek

| Field | Keterangan |
|-------|------------|
| **Kode** | Kode unik proyek (contoh: PRJ-2025-0001) |
| **Nama** | Nama proyek |
| **Klien** | Klien yang terkait dengan proyek |
| **Nilai Kontrak** | Total nilai kontrak dengan klien |
| **Budget** | Anggaran biaya internal |
| **Tanggal Mulai** | Tanggal proyek dimulai |
| **Tanggal Selesai** | Target penyelesaian proyek |
| **Deskripsi** | Detail proyek |

## Membuat Proyek Baru

1. Klik tombol **Proyek Baru**
2. Isi form proyek:
   - **Kode** - Kode unik proyek
   - **Nama** - Nama deskriptif proyek
   - **Klien** - Pilih klien dari dropdown
   - **Nilai Kontrak** - Total nilai yang disepakati
   - **Budget** - Anggaran biaya internal
   - **Tanggal Mulai** - Tanggal mulai proyek
   - **Tanggal Selesai** - Target penyelesaian
   - **Deskripsi** - Detail tambahan
3. Klik **Simpan**

## Detail Proyek

Halaman detail menampilkan beberapa bagian:

### Informasi Proyek
- Semua data proyek
- Status dan progress keseluruhan

### Milestone
- Daftar tahapan proyek
- Progress dan status masing-masing milestone

### Termin Pembayaran
- Jadwal pembayaran dari klien
- Status invoice dan pembayaran

### Transaksi Terkait
- Daftar transaksi yang ditandai ke proyek ini
- Total pendapatan dan biaya

## Milestone

### Pengertian
Milestone adalah tahapan penting dalam proyek yang menandai pencapaian tertentu.

### Informasi Milestone

| Field | Keterangan |
|-------|------------|
| **Nama** | Nama milestone |
| **Bobot** | Persentase kontribusi ke progress proyek |
| **Target** | Tanggal target penyelesaian |
| **Status** | Pending, In Progress, atau Completed |
| **Completion** | Persentase penyelesaian (0-100%) |

### Menambah Milestone

1. Buka detail proyek
2. Di bagian Milestone, klik **Tambah Milestone**
3. Isi informasi:
   - **Nama** - Nama milestone
   - **Bobot** - Persentase dari total proyek
   - **Target** - Tanggal target
4. Klik **Simpan**

### Mengupdate Progress

1. Klik milestone di daftar
2. Update **Completion %**
3. Ubah **Status** jika diperlukan
4. Klik **Simpan**

### Kalkulasi Progress

Progress proyek dihitung dari:
```
Progress = Σ (Bobot × Completion%) untuk semua milestone
```

Contoh:
- Milestone A (30%): 100% selesai → kontribusi 30%
- Milestone B (50%): 50% selesai → kontribusi 25%
- Milestone C (20%): 0% selesai → kontribusi 0%
- Total Progress: 55%

## Termin Pembayaran

### Pengertian
Termin pembayaran adalah jadwal pembayaran dari klien yang terkait dengan milestone atau tanggal tertentu.

### Informasi Termin

| Field | Keterangan |
|-------|------------|
| **Nama** | Nama termin (contoh: DP, Termin 1) |
| **Persentase** | Persen dari nilai kontrak |
| **Jumlah** | Nilai nominal pembayaran |
| **Trigger** | Kapan pembayaran jatuh tempo |
| **Milestone** | Milestone terkait (jika trigger = on_milestone) |

### Trigger Pembayaran

| Trigger | Keterangan |
|---------|------------|
| `on_signing` | Saat kontrak ditandatangani |
| `on_milestone` | Saat milestone tertentu selesai |
| `on_completion` | Saat proyek selesai |
| `fixed_date` | Tanggal tetap |

### Menambah Termin

1. Buka detail proyek
2. Di bagian Termin Pembayaran, klik **Tambah Termin**
3. Isi informasi:
   - **Nama** - Nama termin
   - **Persentase** - Persen dari kontrak
   - **Trigger** - Kondisi jatuh tempo
   - **Milestone** - Pilih milestone (jika trigger = on_milestone)
4. Klik **Simpan**

### Revenue Recognition

Saat milestone selesai:
1. Sistem menemukan termin terkait
2. Otomatis membuat jurnal pengakuan pendapatan
3. Jurnal: Dr. Pendapatan Diterima Dimuka / Cr. Pendapatan Jasa

## Cost Overrun Detection

Sistem mendeteksi risiko overrun dengan membandingkan:
- **% Budget terpakai** vs **% Progress proyek**

Contoh warning:
```
Budget:   Rp 50.000.000
Terpakai: Rp 42.000.000 (84%)
Progress: 60%

⚠️ RISIKO OVERRUN: 60% selesai tapi 84% budget terpakai
```

## Tips Penggunaan

1. Tetapkan milestone yang jelas dan terukur
2. Update progress milestone secara berkala
3. Hubungkan semua transaksi terkait ke proyek
4. Monitor cost overrun untuk antisipasi kerugian
5. Gunakan termin pembayaran untuk tracking invoice
