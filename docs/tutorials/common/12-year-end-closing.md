# Tutup Buku Akhir Tahun

Akhir tahun buku (biasanya 31 Desember) memerlukan langkah tambahan: tutup semua bulan, buat koreksi fiskal, dan posting jurnal penutup.

## Langkah-Langkah

### 1. Pastikan Semua Bulan Sudah Ditutup

Semua 12 bulan harus berstatus **MONTH_CLOSED** sebelum tutup tahun. Jika ada bulan yang masih OPEN, tutup dulu sesuai [Panduan Tutup Bulanan](11-monthly-closing.md).

### 2. Rekonsiliasi Fiskal

Laba komersial (dari laporan keuangan) tidak selalu sama dengan laba fiskal (untuk perhitungan pajak). Beberapa beban tidak diakui secara pajak.

Buka **Laporan → Rekonsiliasi Fiskal** dan tambahkan koreksi:

| Jenis Koreksi | Arah | Contoh |
|--------------|------|--------|
| **Beda Tetap** (Permanent) | Positif (+) | Beban entertainment tanpa daftar nominatif |
| **Beda Tetap** | Positif (+) | Sumbangan yang tidak boleh dikurangkan |
| **Beda Waktu** (Temporary) | Positif (+) | Selisih penyusutan komersial vs fiskal |
| **Beda Waktu** | Negatif (−) | Penyusutan fiskal > komersial |

**Koreksi positif** menambah laba fiskal (beban tidak diakui pajak → laba kena pajak naik).
**Koreksi negatif** mengurangi laba fiskal.

Hasil:
```
Laba Komersial                    794.184.240
+ Koreksi Fiskal Positif           10.000.000
− Koreksi Fiskal Negatif                    0
                                ─────────────
Penghasilan Kena Pajak (PKP)      804.184.240
```

### 3. Hitung PPh Badan

Dari PKP, hitung PPh Badan terutang:

| Bracket PKP | Tarif |
|-------------|-------|
| 0 − 60 juta | 5% (UMKM discount 50%) |
| 60 − 250 juta | 15% |
| 250 − 500 juta | 25% |
| > 500 juta | 30% |

Contoh PKP 804.184.240:
- 60.000.000 × 5% = 3.000.000
- 190.000.000 × 15% = 28.500.000
- 250.000.000 × 25% = 62.500.000
- 304.184.240 × 30% = **belum masuk bracket ini**

PPh Badan Terutang = (dihitung otomatis oleh sistem)

Dikurangi kredit pajak:
- Kredit Pajak PPh 23 (bukti potong dari klien)
- Angsuran PPh 25

PPh 29 (kurang bayar) = PPh Terutang − Kredit Pajak − PPh 25

### 4. Posting Jurnal Penutup

Jurnal penutup menutup semua akun **pendapatan** dan **beban** ke **Laba Ditahan**:

Buka **Laporan → Tutup Buku Tahun** dan klik **Eksekusi**.

Sistem otomatis membuat jurnal:
- Debit semua akun pendapatan (menjadikan saldo 0)
- Kredit semua akun beban (menjadikan saldo 0)
- Selisih masuk ke **Laba Ditahan** (jika untung) atau menguranginya (jika rugi)

Setelah posting:
- Akun pendapatan: saldo 0
- Akun beban: saldo 0
- Laba Ditahan: terisi dengan laba/rugi bersih tahun ini
- Neraca tetap balance

### 5. Bayar PPh 29

Jika ada kurang bayar PPh Badan, bayar sebelum batas waktu SPT (April tahun berikutnya).

Template: **Bayar PPh 29**

## Langkah Selanjutnya

- [Export Coretax](13-coretax-export.md) — export data untuk pelaporan SPT via Coretax
