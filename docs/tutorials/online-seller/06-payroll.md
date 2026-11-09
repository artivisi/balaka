# Payroll

Toko Gadget Sejahtera memiliki 4 karyawan tetap dengan gaji masing-masing Rp 5.500.000/bulan.

Untuk konsep payroll, BPJS, dan PPh 21, lihat:
- [Panduan Payroll](../common/06-payroll.md)
- [Panduan BPJS](../common/09-bpjs.md)
- [Panduan PPh](../common/08-pph.md)

## Konfigurasi Payroll

| Parameter | Nilai |
|-----------|-------|
| Base Salary | 5.500.000 |
| JKK Risk Class | 1 (perdagangan/retail) |
| Jumlah Karyawan | 4 |
| Total Gross/bulan | 22.000.000 (4 x 5,5jt) |

## Langkah Bulanan

### 1. Buat Payroll Run

Buka **Payroll → Buat Baru**:
- Periode: `2025-01`
- Base Salary: `5500000`
- JKK Risk Class: `1`

Klik **Buat & Kalkulasi**.

### 2. Review Hasil

Sistem menghitung BPJS dan PPh 21 otomatis per karyawan:

| Item | Jan–Nov (per bulan) | Desember |
|------|-------------------|----------|
| Total Gross | 22.000.000 | 22.000.000 |
| Total Employee BPJS | 762.300 | 762.300 |
| Total PPh 21 | 626.250 | 329.250 |
| Total Deductions | 1.388.550 | 1.091.550 |
| Total Net Pay | 20.611.450 | 20.908.450 |
| Total Company BPJS | 2.252.800 | 2.252.800 |

**Catatan tentang PPh 21:** Dengan gaji 5,5 juta per bulan, PPh 21 per karyawan relatif kecil. Pada level gaji ini, tarif TER (Tarif Efektif Rata-rata) menghasilkan potongan yang rendah. Rekonsiliasi Desember menghitung PPh 21 setahun dari penghasilan neto (bruto − biaya jabatan − iuran JHT + JP karyawan) dengan tarif Pasal 17, lalu dikurangi PPh 21 Jan–Nov. Hasilnya 329.250, lebih kecil dari potongan TER bulanan. Karyawan K/1 tidak terutang PPh 21 setahun karena penghasilan netonya di bawah PTKP.

### 3. Approve & Post

- Klik **Approve** → review final
- Klik **Post ke Jurnal** → jurnal "Post Gaji Bulanan" otomatis dibuat

### 4. Bayar Gaji dan BPJS

Setelah payroll diposting, buat transaksi:

```
Payroll Jan:
  Gross: 22.000.000 (4 x 5,5jt)
  Net Pay: 20.611.450

Setelah post payroll:
  Beban Gaji         (D) 22.000.000
  Beban BPJS         (D) 2.252.800
  Hutang Gaji        (C) 20.611.450
  Hutang BPJS        (C) 3.015.100
  Hutang PPh 21      (C) 626.250

Bayar Hutang Gaji:
  Hutang Gaji        (D) 20.611.450
  Bank BCA           (C) 20.611.450

Bayar Hutang BPJS:
  Hutang BPJS        (D) 3.015.100
  Bank BCA           (C) 3.015.100

Setor PPh 21 (bulan berikutnya, tgl 10):
  Hutang PPh 21      (D) 626.250
  Bank BCA           (C) 626.250
```

### 5. Total Payroll per Tahun

| Komponen | Per Bulan | Total 2025 |
|----------|----------|-----------|
| Beban Gaji | 22.000.000 | 264.000.000 |
| Beban BPJS Perusahaan | 2.252.800 | 27.033.600 |
| **Total Beban Karyawan** | **24.252.800** | **291.033.600** |

## Hutang PPh 21 Akhir Tahun

Per 31 Desember 2025, Hutang PPh 21 menunjukkan saldo **329.250** — ini adalah PPh 21 Desember yang akan disetor Januari 2026.

## Tips

- **Gaji 5,5 juta relatif rendah** — PPh 21 per karyawan kecil. Jika ada kenaikan gaji ke atas 8 juta, PPh 21 akan naik signifikan
- **BPJS wajib** — meskipun UMKM, karyawan tetap harus didaftarkan BPJS Kesehatan dan Ketenagakerjaan
- **Bayar gaji tepat waktu** — pastikan withdraw marketplace sudah masuk ke Bank BCA sebelum tanggal gajian

## Langkah Selanjutnya

- [Tutup Bulan & Tahun](07-monthly-closing.md) — checklist closing bulanan
