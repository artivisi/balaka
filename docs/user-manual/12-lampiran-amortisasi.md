# Referensi Amortisasi & Depresiasi

Tabel masa manfaat dan tarif penyusutan sesuai regulasi Indonesia.

## Kelompok Aset Tetap (PMK 96/PMK.03/2009)

### Kelompok 1 (Masa Manfaat 4 Tahun)

| Tarif | Garis Lurus | Saldo Menurun |
|-------|-------------|---------------|
| Per Tahun | 25% | 50% |

**Contoh Aset:**
- Komputer dan laptop
- Printer dan scanner
- Mesin ketik
- Peralatan komunikasi (telepon, fax)
- Kendaraan roda 2 (motor)
- Mebel dan peralatan dari kayu/rotan

### Kelompok 2 (Masa Manfaat 8 Tahun)

| Tarif | Garis Lurus | Saldo Menurun |
|-------|-------------|---------------|
| Per Tahun | 12.5% | 25% |

**Contoh Aset:**
- Kendaraan roda 4 (mobil)
- Mebel dan peralatan dari logam
- Peralatan AC dan refrigerator
- Mesin produksi ringan
- Perabotan kantor

### Kelompok 3 (Masa Manfaat 16 Tahun)

| Tarif | Garis Lurus | Saldo Menurun |
|-------|-------------|---------------|
| Per Tahun | 6.25% | 12.5% |

**Contoh Aset:**
- Mesin produksi berat
- Peralatan pabrik
- Instalasi listrik
- Tangki dan kontainer

### Kelompok 4 (Masa Manfaat 20 Tahun)

| Tarif | Garis Lurus | Saldo Menurun |
|-------|-------------|---------------|
| Per Tahun | 5% | 10% |

**Contoh Aset:**
- Bangunan permanen
- Konstruksi
- Jalan, jembatan
- Saluran air

---

## Aset Tak Berwujud

### Amortisasi Standar

| Jenis | Masa Manfaat | Tarif/Tahun |
|-------|--------------|-------------|
| Software | 4 tahun | 25% |
| Website | 4 tahun | 25% |
| Lisensi | Sesuai masa lisensi | Varies |
| Hak Paten | 20 tahun | 5% |
| Hak Cipta | Usia ekonomis | Varies |

---

## Beban Dibayar Dimuka

### Amortisasi Bulanan

| Jenis | Masa Amortisasi |
|-------|-----------------|
| Sewa Tahunan | 12 bulan |
| Asuransi Tahunan | 12 bulan |
| Langganan Software | Sesuai periode |
| Domain & Hosting | 12 bulan |

### Contoh Perhitungan

**Sewa Kantor:**
- Total: Rp 60.000.000/tahun
- Periode: Januari - Desember 2025
- Amortisasi/bulan: Rp 5.000.000

**Jurnal awal (bayar sewa):**
```
Dr. Sewa Dibayar Dimuka         60.000.000
    Cr. Bank                        60.000.000
```

**Jurnal bulanan (amortisasi):**
```
Dr. Beban Sewa                  5.000.000
    Cr. Sewa Dibayar Dimuka         5.000.000
```

---

## Metode Penyusutan

### Garis Lurus (Straight Line)

**Rumus:**
```
Penyusutan/Tahun = (Harga Perolehan - Nilai Residu) / Masa Manfaat
```

**Karakteristik:**
- Beban sama setiap tahun
- Sederhana
- Cocok untuk aset dengan pemakaian merata

**Contoh:**
| Data | Nilai |
|------|-------|
| Harga Perolehan | Rp 12.000.000 |
| Nilai Residu | Rp 0 |
| Masa Manfaat | 4 tahun |
| **Penyusutan/Tahun** | **Rp 3.000.000** |

### Saldo Menurun (Declining Balance)

**Rumus:**
```
Penyusutan Tahun ke-n = Nilai Buku Awal Tahun × Tarif
```

**Karakteristik:**
- Beban besar di awal
- Sesuai untuk aset yang kehilangan nilai cepat di awal
- Lebih agresif dari garis lurus

**Contoh:**
| Tahun | Nilai Buku Awal | Penyusutan (50%) | Nilai Buku Akhir |
|-------|-----------------|------------------|------------------|
| 1 | 12.000.000 | 6.000.000 | 6.000.000 |
| 2 | 6.000.000 | 3.000.000 | 3.000.000 |
| 3 | 3.000.000 | 1.500.000 | 1.500.000 |
| 4 | 1.500.000 | 1.500.000 | 0 |

---

## Kategori Aset Standar (Seed Data)

| Kategori | Kelompok | Masa Manfaat | Metode |
|----------|----------|--------------|--------|
| Komputer & Laptop | 1 | 4 tahun | Garis Lurus |
| Peralatan Kantor | 1 | 4 tahun | Garis Lurus |
| Printer & Scanner | 1 | 4 tahun | Garis Lurus |
| Kendaraan Roda 2 | 1 | 4 tahun | Garis Lurus |
| Kendaraan Roda 4 | 2 | 8 tahun | Garis Lurus |
| Perabotan | 2 | 8 tahun | Garis Lurus |
| AC & Refrigerator | 2 | 8 tahun | Garis Lurus |
| Mesin Produksi | 3 | 16 tahun | Garis Lurus |
| Bangunan | 4 | 20 tahun | Garis Lurus |

---

## Implikasi Pajak

### Deductibility

- Penyusutan aset adalah beban yang dapat mengurangi penghasilan kena pajak
- Metode dan masa manfaat harus sesuai PMK 96/PMK.03/2009
- Aset di bawah Rp 10.000.000 boleh langsung dibebankan (tidak di-kapitalisasi)

### Dokumentasi

Untuk keperluan audit pajak, simpan:
- Faktur pembelian aset
- Bukti pembayaran
- Kartu aset dengan jadwal penyusutan
- Laporan Penyusutan format Lampiran 1A

---

## Lihat Juga

- [Aset Tetap](03-aset-tetap.md) - Pencatatan dan manajemen aset
- [Perpajakan](04-perpajakan.md) - Deductibility beban
- [Glosarium](12-lampiran-glosarium.md) - Definisi istilah
