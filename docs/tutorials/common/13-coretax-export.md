# Export Data Coretax

Setelah tutup buku akhir tahun, Anda perlu melaporkan SPT Tahunan PPh Badan melalui sistem **Coretax** milik DJP. Balaka menyediakan export data yang bisa langsung diinput ke Coretax.

## Data yang Diekspor

### Transkrip 8A — Laporan Keuangan

Berisi neraca (aktiva dan pasiva) serta laba rugi yang dipetakan ke nomor field Coretax.

| Bagian | Isi |
|--------|-----|
| Neraca Aktiva | Kas, bank, piutang, persediaan, aset tetap |
| Neraca Pasiva | Hutang, modal, laba ditahan |
| Laba Rugi | Pendapatan, HPP, beban usaha, laba bersih |

### Lampiran I — Rekonsiliasi Fiskal

Penyesuaian dari laba komersial ke laba fiskal:
- Pendapatan neto komersial
- Koreksi fiskal positif (beda tetap + beda waktu)
- Koreksi fiskal negatif
- Kompensasi kerugian tahun lalu
- Penghasilan Kena Pajak (PKP)

### Lampiran II — Rincian Beban

Breakdown beban usaha dan beban luar usaha per akun.

### Lampiran III — Kredit Pajak PPh 23

Daftar bukti potong PPh 23 yang diterima dari klien:
- Nama pemotong
- NPWP pemotong
- Nomor bukti potong
- DPP dan PPh 23

Total kredit PPh 23 bisa dikurangkan dari PPh Badan terutang.

### Lampiran V — Daftar Pemegang Saham

Data pemegang saham tidak ada di sistem akuntansi — perlu diisi manual di Coretax.

### PPh Badan

Ringkasan perhitungan:
- PKP (dari Lampiran I)
- PPh Terutang (dari tarif progresif)
- Kredit Pajak (dari Lampiran III + PPh 25)
- PPh 29 Kurang/Lebih Bayar

## Cara Mengakses

### Via Web UI

Buka **Laporan → SPT Tahunan**. Halaman checklist menampilkan status kelengkapan data:
- Laporan keuangan ✓
- Penyusutan aset ✓
- Payroll & PPh 21 ✓
- Rekonsiliasi fiskal ✓

### Via API

Untuk integrasi otomatis:

```
GET /api/tax-export/spt-tahunan/lampiran?year=2025
```

Response berisi semua lampiran dalam format JSON, siap untuk diinput ke Coretax.

## Tips

1. **Pastikan tutup buku sudah selesai** sebelum export — angka di export harus final
2. **Cek rekonsiliasi fiskal** — koreksi fiskal harus lengkap (entertainment, sumbangan, penyusutan)
3. **Cocokkan kredit pajak** — bukti potong PPh 23 harus sesuai dengan yang diterima dari klien
4. **Simpan bukti potong** — bukti potong asli dari klien harus disimpan sebagai lampiran SPT

## Batas Waktu

| Dokumen | Batas Waktu |
|---------|-------------|
| SPT Tahunan PPh Badan | 30 April tahun berikutnya |
| Pembayaran PPh 29 | Sebelum penyampaian SPT |
| Bukti potong PPh 23 | Diterima dari klien, dilaporkan di SPT |
