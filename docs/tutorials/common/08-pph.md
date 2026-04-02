# PPh (Pajak Penghasilan)

PPh adalah pajak atas penghasilan. Ada beberapa jenis PPh yang relevan untuk usaha kecil-menengah.

## Ringkasan Jenis PPh

| Jenis | Tarif | Siapa Bayar | Kapan |
|-------|-------|-------------|-------|
| **PPh 21** | TER (bervariasi) | Perusahaan (potong gaji karyawan) | Setiap bulan |
| **PPh 23** | 2% | Klien (potong dari pembayaran ke Anda) | Saat bayar jasa |
| **PPh 4(2)** | 10% | Penyewa (potong dari pembayaran sewa) | Saat bayar sewa |
| **PPh 25** | Bervariasi | Perusahaan (angsuran bulanan) | Setiap bulan |
| **PPh 29** | Sisa kurang bayar | Perusahaan (akhir tahun) | April tahun berikut |
| **PPh Final UMKM** | 0,5% dari omzet | Perusahaan UMKM | Setiap bulan |

## PPh 21 — Pajak Gaji Karyawan

Dipotong dari gaji karyawan setiap bulan. Dihitung otomatis oleh fitur payroll.

Untuk detail lengkap, lihat [Panduan Payroll](06-payroll.md#pph-21-ter).

**Template:** Setor PPh 21 (menyetor ke kas negara, paling lambat tanggal 10 bulan berikutnya)

## PPh 23 — Pajak Jasa

Berlaku saat klien membayar jasa Anda. Klien memotong 2% dan menyerahkan bukti potong.

### Anda sebagai penerima jasa (PPh 23 dipotong klien)

Klien memotong 2% dari nilai jasa. Anda menerima 98%. Potongan 2% menjadi **kredit pajak** yang bisa dikurangkan dari PPh Badan tahunan.

Template: **Pendapatan Jasa + PPh 23 Dipotong**

| Akun | Debit | Kredit |
|------|-------|--------|
| Bank BCA | amount × 0,98 | |
| Kredit Pajak PPh 23 | amount × 0,02 | |
| Pendapatan | | amount |

### Anda sebagai pembayar jasa (PPh 23 Anda potong)

Saat Anda membayar jasa vendor/freelancer, Anda memotong 2% dan menyetornya ke negara.

Template: **Pembayaran Jasa dengan PPh 23**

| Akun | Debit | Kredit |
|------|-------|--------|
| Beban Jasa | amount | |
| Bank BCA | | amount × 0,98 |
| Hutang PPh 23 | | amount × 0,02 |

**Setor:** paling lambat tanggal 10 bulan berikutnya via **Setor PPh 23**.

## PPh 4(2) — Pajak Final Sewa

Saat membayar sewa gedung/tanah, Anda memotong 10% PPh Final.

Template: **Pembayaran Sewa dengan PPh 4(2)**

| Akun | Debit | Kredit |
|------|-------|--------|
| Beban Sewa | amount | |
| Bank BCA | | amount × 0,90 |
| Hutang PPh 4(2) | | amount × 0,10 |

Pemilik gedung menerima 90%, Anda setor 10% ke negara.

## PPh 25 — Angsuran Bulanan

Angsuran PPh Badan yang dibayar setiap bulan berdasarkan SPT tahun lalu.

Template: **Bayar PPh 25**

## PPh 29 — Kurang Bayar Tahunan

Sisa PPh Badan yang kurang bayar setelah dikurangi kredit pajak dan angsuran PPh 25. Dibayar saat penyampaian SPT Tahunan.

Template: **Bayar PPh 29**

## PPh Final UMKM (PP 55/2022)

Untuk usaha dengan omzet **di bawah 4,8 miliar/tahun**: bayar PPh Final 0,5% dari omzet bruto setiap bulan.

**Berlaku untuk:** Online Seller, Coffee Shop, dan usaha kecil lainnya yang belum PKP.

Template: **Bayar PPh Final UMKM**

Cara hitung: total penjualan bulan ini × 0,5%. Disetor paling lambat tanggal 15 bulan berikutnya.

**Contoh:** Penjualan Januari Rp 60.000.000 → PPh Final = 60.000.000 × 0,5% = Rp 300.000

## Langkah Selanjutnya

- [BPJS](09-bpjs.md) — iuran jaminan sosial karyawan
- [Monthly Closing](11-monthly-closing.md) — jadwal setor semua pajak
