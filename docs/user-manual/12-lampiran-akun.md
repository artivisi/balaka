# Referensi Akun

![Daftar Akun (Chart of Accounts)](screenshots/accounts-list.png)

Daftar akun standar yang tersedia dalam seed data. Akun dikelompokkan berdasarkan tipe dan sub-tipe.

## Struktur Kode Akun

| Kode | Tipe | Saldo Normal |
|------|------|--------------|
| 1.x.xx | Aset | Debit |
| 2.x.xx | Kewajiban | Kredit |
| 3.x.xx | Ekuitas | Kredit |
| 4.x.xx | Pendapatan | Kredit |
| 5.x.xx | Beban | Debit |

---

## 1. Aset (Assets)

### 1.1 Aset Lancar (Current Assets)

| Kode | Nama Akun | Keterangan |
|------|-----------|------------|
| 1.1.01 | Kas Kecil | Uang tunai di tangan |
| 1.1.02 | Kas Besar | Kas operasional |
| 1.1.10 | Bank BCA | Rekening Bank BCA |
| 1.1.11 | Bank Mandiri | Rekening Bank Mandiri |
| 1.1.12 | Bank BNI | Rekening Bank BNI |
| 1.1.15 | Deposito | Deposito berjangka |
| 1.1.20 | Piutang Usaha | Tagihan ke pelanggan |
| 1.1.21 | Piutang Karyawan | Pinjaman ke karyawan |
| 1.1.25 | PPN Masukan | PPN yang dapat dikreditkan |
| 1.1.30 | Sewa Dibayar Dimuka | Sewa yang belum jatuh tempo |
| 1.1.31 | Asuransi Dibayar Dimuka | Premi yang belum jatuh tempo |
| 1.1.35 | Perlengkapan | Perlengkapan kantor |
| 1.1.40 | Persediaan Barang Dagang | Barang untuk dijual (Dagang) |
| 1.1.41 | Persediaan Bahan Baku | Bahan untuk produksi (Manufaktur) |
| 1.1.42 | Persediaan Barang Jadi | Hasil produksi (Manufaktur) |

### 1.2 Aset Tetap (Fixed Assets)

| Kode | Nama Akun | Keterangan |
|------|-----------|------------|
| 1.2.01 | Peralatan Kantor | Komputer, printer, dll |
| 1.2.02 | Akum. Penyusutan Peralatan | Kontra akun peralatan |
| 1.2.05 | Kendaraan | Kendaraan operasional |
| 1.2.06 | Akum. Penyusutan Kendaraan | Kontra akun kendaraan |
| 1.2.10 | Perabotan | Meja, kursi, lemari |
| 1.2.11 | Akum. Penyusutan Perabotan | Kontra akun perabotan |
| 1.2.15 | Bangunan | Gedung dan bangunan |
| 1.2.16 | Akum. Penyusutan Bangunan | Kontra akun bangunan |

### 1.3 Aset Tak Berwujud (Intangible Assets)

| Kode | Nama Akun | Keterangan |
|------|-----------|------------|
| 1.3.01 | Website | Biaya pengembangan website |
| 1.3.02 | Akum. Amortisasi Website | Kontra akun website |
| 1.3.05 | Software | Lisensi software |
| 1.3.06 | Akum. Amortisasi Software | Kontra akun software |

---

## 2. Kewajiban (Liabilities)

### 2.1 Kewajiban Lancar (Current Liabilities)

| Kode | Nama Akun | Keterangan |
|------|-----------|------------|
| 2.1.01 | Hutang Usaha | Hutang ke vendor/supplier |
| 2.1.02 | Hutang Gaji | Gaji yang belum dibayar |
| 2.1.03 | Hutang PPN | PPN yang harus disetor |
| 2.1.07 | Hutang Gaji (Payroll) | Gaji neto dari payroll bulanan |
| 2.1.08 | Hutang BPJS | Iuran BPJS yang belum disetor |
| 2.1.10 | Pendapatan Diterima Dimuka | Pembayaran dimuka dari klien |
| 2.1.20 | Hutang PPh 21 | PPh 21 yang dipotong |
| 2.1.21 | Hutang PPh 23 | PPh 23 yang dipotong |
| 2.1.22 | Hutang PPh 4(2) | PPh final yang dipotong |
| 2.1.23 | Hutang PPh 25 | Angsuran PPh bulanan |
| 2.1.24 | Hutang PPh 29 | PPh kurang bayar tahunan |

### 2.2 Kewajiban Jangka Panjang (Long-term Liabilities)

| Kode | Nama Akun | Keterangan |
|------|-----------|------------|
| 2.2.01 | Hutang Bank | Pinjaman bank jangka panjang |
| 2.2.05 | Hutang Leasing | Hutang sewa guna usaha |

---

## 3. Ekuitas (Equity)

| Kode | Nama Akun | Keterangan |
|------|-----------|------------|
| 3.1.01 | Modal | Modal awal pemilik |
| 3.1.02 | Modal Disetor | Tambahan modal dari pemilik |
| 3.2.01 | Laba Ditahan | Akumulasi laba tahun lalu |
| 3.2.02 | Laba Tahun Berjalan | Laba periode berjalan |
| 3.3.01 | Prive | Pengambilan oleh pemilik |

---

## 4. Pendapatan (Revenue)

### 4.1 Pendapatan Operasional

| Kode | Nama Akun | Keterangan |
|------|-----------|------------|
| 4.1.01 | Pendapatan Jasa | Pendapatan dari jasa utama |
| 4.1.02 | Pendapatan Konsultasi | Pendapatan konsultasi |
| 4.1.05 | Pendapatan Maintenance | Pendapatan maintenance/retainer |
| 4.1.10 | Penjualan | Pendapatan dari penjualan barang |

### 4.2 Pendapatan Lain-lain

| Kode | Nama Akun | Keterangan |
|------|-----------|------------|
| 4.2.01 | Pendapatan Bunga | Bunga deposito/tabungan |
| 4.2.02 | Pendapatan Lain-lain | Pendapatan di luar operasional |

---

## 5. Beban (Expenses)

### 5.1 Beban Operasional

| Kode | Nama Akun | Keterangan |
|------|-----------|------------|
| 5.1.01 | Beban Gaji | Gaji karyawan tetap |
| 5.1.02 | Beban Sewa | Sewa kantor/gedung |
| 5.1.03 | Beban Listrik | Tagihan listrik |
| 5.1.04 | Beban Air | Tagihan air |
| 5.1.05 | Beban Telepon | Tagihan telepon |
| 5.1.06 | Beban Internet | Tagihan internet |
| 5.1.10 | Beban ATK | Alat tulis kantor |
| 5.1.11 | Beban BPJS | Kontribusi BPJS perusahaan |
| 5.1.12 | Beban Perlengkapan | Perlengkapan kantor |
| 5.1.15 | Beban Transportasi | Transport dan perjalanan |
| 5.1.16 | Beban Parkir | Biaya parkir |
| 5.1.20 | Beban Makan | Makan karyawan/meeting |

### 5.2 Beban Profesional

| Kode | Nama Akun | Keterangan |
|------|-----------|------------|
| 5.2.01 | Beban Jasa Profesional | Jasa konsultan/freelancer |
| 5.2.02 | Beban Jasa Hukum | Jasa notaris/pengacara |
| 5.2.03 | Beban Jasa Akuntan | Jasa akuntan/audit |
| 5.2.05 | Beban Software | Subscription software |
| 5.2.06 | Beban Hosting | Hosting dan domain |

### 5.3 Beban Administrasi

| Kode | Nama Akun | Keterangan |
|------|-----------|------------|
| 5.3.01 | Beban Administrasi Bank | Biaya admin bank |
| 5.3.02 | Beban Materai | Bea materai |
| 5.3.05 | Beban Asuransi | Premi asuransi |

### 5.4 Beban Penyusutan & Amortisasi

| Kode | Nama Akun | Keterangan |
|------|-----------|------------|
| 5.4.01 | Beban Penyusutan | Penyusutan aset tetap |
| 5.4.02 | Beban Amortisasi | Amortisasi aset tak berwujud |

### 5.5 Harga Pokok (Cost of Goods)

| Kode | Nama Akun | Keterangan |
|------|-----------|------------|
| 5.5.01 | HPP Barang Dagang | HPP untuk industri dagang |
| 5.5.02 | HPP Produksi | HPP untuk industri manufaktur |
| 5.5.10 | Beban Selisih Persediaan | Selisih stock opname |

---

## Cara Menambah Akun

Jika akun yang Anda butuhkan tidak ada dalam daftar:

1. Tentukan **tipe akun** (Aset/Kewajiban/Ekuitas/Pendapatan/Beban)
2. Tentukan **sub-tipe** yang sesuai
3. Buat **kode akun** mengikuti struktur yang ada
4. Lihat [Setup Awal](01-setup-awal.md) untuk panduan menambah akun

---

## Lihat Juga

- [Setup Awal](01-setup-awal.md) - Menambah dan mengelola akun
- [Tutorial Akuntansi](02-tutorial-akuntansi.md) - Memahami debit dan kredit
- [Glosarium](12-lampiran-glosarium.md) - Penjelasan istilah
