# Industri Pendidikan

> **[TBD]** Bagian ini akan dilengkapi setelah functional test untuk industri pendidikan selesai.

Panduan untuk institusi pendidikan (Universitas, Sekolah, Kursus).

## Karakteristik Institusi Pendidikan

### Ciri Khas

- **Revenue berbasis semester** - SPP, Uang Pangkal, Biaya Praktikum
- **Receivables management** - Piutang mahasiswa dengan cicilan
- **Beasiswa** - Potongan biaya pendidikan
- **Multi-period** - Tahun ajaran, semester

### Alur Bisnis Tipikal

```
Mahasiswa → Pendaftaran → Tagihan → Pembayaran → Semester Berjalan
```

---

## Manajemen Mahasiswa

### Konsep

Data mahasiswa termasuk:
- NIM (Nomor Induk Mahasiswa)
- Program studi
- Angkatan
- Status (Aktif/Cuti/Lulus/DO)

### Melihat Daftar Mahasiswa [TBD]

Screenshot dan panduan akan ditambahkan setelah functional test.

---

## Tagihan SPP

### Jenis Tagihan

| Jenis | Periode | Contoh Nominal |
|-------|---------|----------------|
| SPP | Per semester | Rp 5.000.000 |
| Uang Pangkal | Sekali (masuk) | Rp 15.000.000 |
| Biaya Praktikum | Per semester | Rp 1.000.000 |
| Biaya Wisuda | Sekali (lulus) | Rp 2.000.000 |

### Membuat Tagihan [TBD]

1. Generate tagihan per semester
2. Assign ke mahasiswa aktif
3. Jurnal:
   ```
   Dr. Piutang Mahasiswa        xxx
       Cr. Pendapatan SPP           xxx
   ```

---

## Pembayaran dan Cicilan

### Terima Pembayaran [TBD]

Saat mahasiswa membayar:

```
Dr. Kas/Bank                    xxx
    Cr. Piutang Mahasiswa           xxx
```

### Skema Cicilan

| Cicilan | Tanggal | Nominal |
|---------|---------|---------|
| 1 | Awal semester | 40% |
| 2 | Tengah semester | 30% |
| 3 | Akhir semester | 30% |

---

## Beasiswa dan Potongan

### Jenis Beasiswa

| Jenis | Potongan | Kriteria |
|-------|----------|----------|
| Akademik | 50-100% SPP | IPK ≥ 3.5 |
| Tidak Mampu | 25-75% SPP | Surat keterangan |
| Prestasi | Varies | Lomba nasional/internasional |

### Mencatat Beasiswa [TBD]

```
Dr. Beban Beasiswa              xxx
    Cr. Piutang Mahasiswa           xxx
```

---

## Laporan Pendidikan

### Laporan yang Dibutuhkan [TBD]

| Laporan | Fungsi |
|---------|--------|
| Receivables Aging | Piutang per umur (current, 30, 60, 90 hari) |
| Revenue per Program | Pendapatan per program studi |
| Collection Rate | Persentase pembayaran vs tagihan |
| Beasiswa Summary | Total beasiswa per jenis |

---

## Skenario Transaksi [TBD]

### Skenario 1: Generate Tagihan Semester

1. Pilih semester
2. Pilih mahasiswa (atau batch)
3. Generate tagihan
4. Jurnal per mahasiswa:
   ```
   Dr. Piutang Mahasiswa        6.000.000
       Cr. Pendapatan SPP           5.000.000
       Cr. Pendapatan Praktikum     1.000.000
   ```

### Skenario 2: Terima Pembayaran Penuh

```
Dr. Bank                        6.000.000
    Cr. Piutang Mahasiswa           6.000.000
```

### Skenario 3: Terima Pembayaran Cicilan

Cicilan 1 (40%):
```
Dr. Bank                        2.400.000
    Cr. Piutang Mahasiswa           2.400.000
```

### Skenario 4: Apply Beasiswa

```
Dr. Beban Beasiswa              3.000.000
    Cr. Piutang Mahasiswa           3.000.000
```

---

## Tips Industri Pendidikan [TBD]

1. **Generate tagihan tepat waktu** - Awal semester
2. **Monitor aging** - Follow up piutang overdue
3. **Document beasiswa** - Simpan bukti kriteria beasiswa
4. **Reconcile per semester** - Pastikan semua tagihan ter-settle
5. **Report per program** - Analisis profitabilitas per prodi

---

## Status Implementasi

| Fitur | Status |
|-------|--------|
| Student Entity | ⏳ Pending |
| Billing Entity | ⏳ Pending |
| Payment Entity | ⏳ Pending |
| Scholarship Entity | ⏳ Pending |
| Student UI | ⏳ Pending |
| Billing UI | ⏳ Pending |
| Functional Tests | ⏳ Pending |
| Seed Pack (campus) | ⏳ Pending |
| User Manual Screenshots | ⏳ Pending |

---

## Lihat Juga

- [Pengantar Industri](06-pengantar-industri.md) - Perbandingan industri
- [Tutorial Akuntansi](02-tutorial-akuntansi.md) - Jurnal dasar
- [Perpajakan](04-perpajakan.md) - PPh 21 dosen/lecturer
