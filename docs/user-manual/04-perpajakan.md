# Perpajakan

Panduan pencatatan transaksi pajak dan laporan untuk kepatuhan perpajakan Indonesia.

## Jenis Pajak di Indonesia

### Pajak yang Dikelola Aplikasi

| Pajak | Tarif | Kewajiban |
|-------|-------|-----------|
| **PPN** | 11% | PKP yang menyerahkan BKP/JKP |
| **PPh 21** | Progresif | Pemberi kerja (pemotongan gaji) |
| **PPh 23** | 2% (jasa), 15% (dividen) | Pemberi penghasilan |
| **PPh 25** | Angsuran | Wajib pajak badan |
| **PPh 4(2)** | Final (varies) | Transaksi tertentu |

---

## Transaksi PPN

### Konsep PPN

**PPN Keluaran** - PPN yang Anda pungut dari pembeli
**PPN Masukan** - PPN yang Anda bayar ke penjual

```
Net PPN = PPN Keluaran - PPN Masukan
```

### Mencatat Pendapatan dengan PPN

1. Buka menu **Transaksi** > **Transaksi Baru**

![Form Transaksi](screenshots/transactions-form.png)

2. Pilih template **Pendapatan Jasa dengan PPN**
3. Isi jumlah inklusif PPN
4. Preview jurnal:
   ```
   Dr. Bank                    11.100.000
       Cr. Hutang PPN              1.100.000
       Cr. Pendapatan             10.000.000
   ```
5. Klik **Simpan & Posting**

### Mencatat Pembelian dengan PPN Masukan

1. Pilih template **Pembelian dengan PPN**
2. Isi jumlah inklusif PPN
3. Preview jurnal:
   ```
   Dr. Beban/Aset             10.000.000
   Dr. PPN Masukan             1.100.000
       Cr. Bank                   11.100.000
   ```

### Laporan Ringkasan PPN

Buka menu **Laporan** > **Ringkasan PPN**.

![Ringkasan PPN](screenshots/reports-ppn-summary.png)

---

## Transaksi PPh

### PPh 23 - Pemotongan atas Jasa

Saat membayar jasa ke vendor (selain pegawai):

1. Pilih template **Bayar Jasa + PPh 23**
2. Isi nilai bruto
3. Sistem menghitung PPh 23 (2%)
4. Preview jurnal:
   ```
   Dr. Beban Jasa             10.000.000
       Cr. Hutang PPh 23           200.000
       Cr. Bank                  9.800.000
   ```

### PPh 21 - Pemotongan Gaji

Lihat bagian [Penggajian](05-penggajian.md) untuk detail PPh 21 karyawan.

### Laporan PPh

Buka menu **Laporan** > **Bukti Potong PPh 23**.

![Bukti Potong PPh 23](screenshots/reports-pph23-withholding.png)

### Ringkasan Pajak

Buka menu **Laporan** > **Ringkasan Pajak**.

![Ringkasan Pajak](screenshots/reports-tax-summary.png)

---

## Periode Fiskal

### Melihat Periode Fiskal

Buka menu **Pengaturan** > **Periode Fiskal**.

![Daftar Periode Fiskal](screenshots/fiscal-periods-list.png)

### Status Periode

| Status | Arti |
|--------|------|
| OPEN | Transaksi dapat dicatat |
| CLOSED | Transaksi tidak dapat dicatat |

### Menutup Periode

Tutup periode setelah semua transaksi dan penyesuaian selesai:

1. Pilih periode
2. Klik **Tutup Periode**
3. Konfirmasi

---

## Kalender Pajak

### Melihat Kalender Pajak

Buka menu **Pajak** > **Kalender Pajak**.

![Kalender Pajak](screenshots/tax-calendar.png)

### Deadline Pajak Standar

| Pajak | Setor | Lapor |
|-------|-------|-------|
| PPN | Tgl 15 bulan berikutnya | Tgl 20 bulan berikutnya |
| PPh 21 | Tgl 10 bulan berikutnya | Tgl 20 bulan berikutnya |
| PPh 23 | Tgl 10 bulan berikutnya | Tgl 20 bulan berikutnya |
| PPh 25 | Tgl 15 bulan berikutnya | Tgl 20 bulan berikutnya |

### Kalender Tahunan

![Kalender Pajak Tahunan](screenshots/tax-calendar-yearly.png)

### Menandai Selesai

1. Klik deadline yang sudah diselesaikan
2. Isi nomor bukti setor/lapor
3. Klik **Selesai**

---

## Referensi Regulasi

### Tarif PPh 21 (TER 2024)

| PKP Tahunan | Tarif |
|-------------|-------|
| s.d. Rp 60.000.000 | 5% |
| > Rp 60.000.000 - Rp 250.000.000 | 15% |
| > Rp 250.000.000 - Rp 500.000.000 | 25% |
| > Rp 500.000.000 - Rp 5.000.000.000 | 30% |
| > Rp 5.000.000.000 | 35% |

### PTKP (Penghasilan Tidak Kena Pajak)

| Status | PTKP/Tahun |
|--------|------------|
| TK/0 | Rp 54.000.000 |
| K/0 | Rp 58.500.000 |
| K/1 | Rp 63.000.000 |
| K/2 | Rp 67.500.000 |
| K/3 | Rp 72.000.000 |

### Tarif PPh 23

| Objek | Tarif |
|-------|-------|
| Dividen | 15% |
| Bunga | 15% |
| Royalti | 15% |
| Jasa (umum) | 2% |
| Sewa (selain tanah/bangunan) | 2% |

---

## Tips Kepatuhan

1. **Catat tepat waktu** - Jangan menunda pencatatan transaksi pajak
2. **Simpan bukti** - Faktur pajak, bukti potong, NTPN
3. **Rekonsiliasi bulanan** - Cocokkan saldo akun pajak
4. **Gunakan kalender** - Set reminder untuk deadline
5. **Konsultasi** - Hubungi konsultan pajak untuk kasus kompleks

---

## Lihat Juga

- [Penggajian](05-penggajian.md) - PPh 21 karyawan
- [Tutorial Akuntansi](02-tutorial-akuntansi.md) - Jurnal pajak
- [Referensi Template](12-lampiran-template.md) - Template transaksi pajak
