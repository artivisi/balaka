# Laporan Profitabilitas

## Pengertian

Laporan profitabilitas menampilkan analisis kinerja keuangan berdasarkan proyek atau klien. Laporan ini membantu mengidentifikasi proyek/klien yang paling menguntungkan dan mendeteksi potensi kerugian.

## Jenis Laporan

| Laporan | Fungsi |
|---------|--------|
| **Profitabilitas Proyek** | Analisis per proyek individual |
| **Profitabilitas Klien** | Agregasi semua proyek per klien |

## Profitabilitas Proyek

### Mengakses Laporan

1. Klik menu **Laporan** di sidebar
2. Pilih **Profitabilitas Proyek**
3. Pilih filter (opsional):
   - **Periode** - Rentang tanggal transaksi
   - **Klien** - Filter proyek dari klien tertentu
   - **Status** - Filter berdasarkan status proyek
4. Klik **Tampilkan**

### Struktur Laporan

| Bagian | Isi |
|--------|-----|
| **Pendapatan** | Semua transaksi pendapatan terkait proyek |
| **Biaya Langsung** | Biaya yang langsung terkait proyek |
| **Laba Kotor** | Pendapatan - Biaya Langsung |
| **Margin %** | Persentase laba kotor terhadap pendapatan |

### Contoh Laporan

```
Proyek: Website Redesign - PT ABC
Periode: Jan - Mar 2025

Pendapatan:
  Pendapatan Jasa Development    Rp 50.000.000
  ─────────────────────────────────────────────
  Total Pendapatan               Rp 50.000.000

Biaya Langsung:
  Beban Server & Cloud           Rp    500.000
  Beban Software & Lisensi       Rp    300.000
  ─────────────────────────────────────────────
  Total Biaya Langsung           Rp    800.000

Laba Kotor                       Rp 49.200.000
Margin                           98,4%
```

### Analisis Cost Overrun

Laporan juga menampilkan indikator overrun jika:
- Progress proyek lebih rendah dari persentase budget terpakai

```
Budget:   Rp 50.000.000
Terpakai: Rp 42.000.000 (84%)
Progress: 60%

⚠️ RISIKO OVERRUN
Proyeksi Biaya Akhir: Rp 70.000.000 (140% budget)
Proyeksi Kerugian:    Rp 20.000.000
```

## Profitabilitas Klien

### Mengakses Laporan

1. Klik menu **Laporan** di sidebar
2. Pilih **Profitabilitas Klien**
3. Pilih filter (opsional):
   - **Periode** - Rentang tanggal transaksi
4. Klik **Tampilkan**

### Struktur Laporan

Laporan menampilkan semua klien dengan agregasi proyek:

| Kolom | Keterangan |
|-------|------------|
| Klien | Nama klien |
| Jumlah Proyek | Total proyek yang dihitung |
| Total Pendapatan | Agregasi pendapatan semua proyek |
| Total Biaya | Agregasi biaya semua proyek |
| Laba Kotor | Pendapatan - Biaya |
| Margin % | Persentase margin |

### Contoh Laporan

```
Klien: PT ABC
Periode: 2025

Proyek:
  Website Redesign     Rp 50.000.000 revenue   Rp 49.200.000 profit (98,4%)
  Mobile App Dev       Rp 80.000.000 revenue   Rp 65.000.000 profit (81,3%)
  Maintenance Q1-Q4    Rp 24.000.000 revenue   Rp 22.000.000 profit (91,7%)
  ───────────────────────────────────────────────────────────────────────────
  Total               Rp 154.000.000 revenue  Rp 136.200.000 profit (88,4%)

Ranking: #1 dari 12 klien (28% dari total revenue)
```

### Ranking Klien

Laporan menampilkan ranking klien berdasarkan:
- Total pendapatan
- Persentase kontribusi terhadap total revenue perusahaan

## Metrik Penting

### Margin Laba Kotor

```
Margin = (Laba Kotor / Pendapatan) × 100%
```

Interpretasi:
- > 50%: Sangat baik
- 30-50%: Baik
- 10-30%: Perlu perhatian
- < 10%: Kritis

### Client Concentration

```
Konsentrasi = (Revenue Klien / Total Revenue) × 100%
```

Risiko jika satu klien > 40% total revenue:
- Ketergantungan tinggi
- Risiko besar jika klien hilang

## Ekspor Laporan

Semua laporan dapat diekspor:

| Format | Kegunaan |
|--------|----------|
| **PDF** | Presentasi dan dokumentasi |
| **Excel** | Analisis detail lebih lanjut |

## Tips Penggunaan

1. Review laporan profitabilitas minimal bulanan
2. Monitor proyek dengan margin < 30%
3. Identifikasi klien dengan konsentrasi tinggi
4. Gunakan data untuk negosiasi kontrak berikutnya
5. Bandingkan margin antar proyek untuk benchmarking
6. Perhatikan tren margin dari waktu ke waktu
