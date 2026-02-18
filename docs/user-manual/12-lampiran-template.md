# Referensi Template

![Daftar Template](screenshots/service/templates-list.png)

Daftar template transaksi standar yang tersedia dalam seed data. Template mempermudah pencatatan transaksi karena akun-akun sudah dikonfigurasi.

## Kategori Template

| Kategori | Jumlah | Deskripsi |
|----------|--------|-----------|
| Pendapatan | 8 | Template untuk mencatat penerimaan |
| Pengeluaran | 12 | Template untuk mencatat pembayaran |
| Transfer | 3 | Template untuk transfer antar akun |
| Pajak | 6 | Template untuk setoran pajak |
| Payroll | 4 | Template untuk penggajian |
| Inventory | 4 | Template untuk persediaan |

---

## Template Pendapatan

### Pendapatan Jasa + PPN (PKP)

**Digunakan**: Menerima pembayaran jasa dari klien (PKP)

| Field | Input |
|-------|-------|
| Jumlah | Total inklusif PPN |

**Jurnal**:
```
Dr. Bank/Kas                    amount
    Cr. Hutang PPN                  amount * 11/111
    Cr. Pendapatan Jasa             amount * 100/111
```

### Pendapatan Jasa (Non-PKP)

**Digunakan**: Menerima pembayaran jasa tanpa PPN

**Jurnal**:
```
Dr. Bank/Kas                    amount
    Cr. Pendapatan Jasa             amount
```

### Terima DP Proyek

**Digunakan**: Menerima uang muka proyek

**Jurnal**:
```
Dr. Bank/Kas                    amount
    Cr. Pendapatan Diterima Dimuka  amount
```

### Pengakuan Pendapatan (Revenue Recognition)

**Digunakan**: Mengakui pendapatan saat milestone selesai

**Jurnal**:
```
Dr. Pendapatan Diterima Dimuka  amount
    Cr. Pendapatan Jasa             amount
```

### Terima Pelunasan Piutang

**Digunakan**: Klien melunasi piutang

**Jurnal**:
```
Dr. Bank/Kas                    amount
    Cr. Piutang Usaha               amount
```

### Pendapatan Bunga

**Digunakan**: Terima bunga deposito/tabungan

**Jurnal**:
```
Dr. Bank                        amount
    Cr. Pendapatan Bunga            amount
```

### Penjualan Barang + PPN

**Digunakan**: Menjual barang dengan PPN

**Jurnal**:
```
Dr. Bank/Kas                    amount
    Cr. Hutang PPN                  amount * 11/111
    Cr. Penjualan                   amount * 100/111
```

### Penjualan Barang (Non-PKP)

**Digunakan**: Menjual barang tanpa PPN

**Jurnal**:
```
Dr. Bank/Kas                    amount
    Cr. Penjualan                   amount
```

---

## Template Pengeluaran

### Beban Operasional

**Digunakan**: Pengeluaran operasional umum (listrik, internet, dll)

**Field**:
- Jumlah
- Akun Beban (dropdown)

**Jurnal**:
```
Dr. [Akun Beban]                amount
    Cr. Bank/Kas                    amount
```

### Beban dengan PPN (dapat dikreditkan)

**Digunakan**: Pembelian dengan faktur pajak

**Jurnal**:
```
Dr. [Akun Beban]                amount * 100/111
Dr. PPN Masukan                 amount * 11/111
    Cr. Bank/Kas                    amount
```

### Pembelian Aset Tetap

**Digunakan**: Beli peralatan, kendaraan, dll

**Jurnal**:
```
Dr. Aset Tetap                  amount
    Cr. Bank/Kas                    amount
```

### Bayar Jasa + PPh 23

**Digunakan**: Bayar jasa freelancer/vendor dengan potong PPh 23

**Jurnal**:
```
Dr. Beban Jasa                  amount
    Cr. Hutang PPh 23               amount * 2%
    Cr. Bank/Kas                    amount * 98%
```

### Bayar Sewa + PPh 4(2)

**Digunakan**: Bayar sewa gedung/tanah

**Jurnal**:
```
Dr. Beban Sewa                  amount
    Cr. Hutang PPh 4(2)             amount * 10%
    Cr. Bank/Kas                    amount * 90%
```

### Beban Gaji (Simple)

**Digunakan**: Bayar gaji tanpa proses payroll penuh

**Jurnal**:
```
Dr. Beban Gaji                  amount
    Cr. Bank/Kas                    amount
```

---

## Template Transfer

### Transfer Antar Bank

**Digunakan**: Pindah dana antar rekening

**Field**:
- Dari Akun (dropdown)
- Ke Akun (dropdown)
- Jumlah

**Jurnal**:
```
Dr. [Bank Tujuan]               amount
    Cr. [Bank Asal]                 amount
```

### Tarik Tunai

**Digunakan**: Tarik uang dari bank ke kas

**Jurnal**:
```
Dr. Kas                         amount
    Cr. Bank                        amount
```

### Setor Tunai

**Digunakan**: Setor uang dari kas ke bank

**Jurnal**:
```
Dr. Bank                        amount
    Cr. Kas                         amount
```

---

## Template Pajak

### Setor PPN

**Digunakan**: Bayar PPN ke negara

**Jurnal**:
```
Dr. Hutang PPN                  amount
    Cr. Bank                        amount
```

### Setor PPh 21

**Digunakan**: Bayar PPh 21 karyawan ke negara

**Jurnal**:
```
Dr. Hutang PPh 21               amount
    Cr. Bank                        amount
```

### Setor PPh 23

**Digunakan**: Bayar PPh 23 ke negara

**Jurnal**:
```
Dr. Hutang PPh 23               amount
    Cr. Bank                        amount
```

### Setor PPh 25

**Digunakan**: Bayar angsuran PPh bulanan

**Jurnal**:
```
Dr. Hutang PPh 25               amount
    Cr. Bank                        amount
```

### Setor PPh 4(2)

**Digunakan**: Bayar PPh final

**Jurnal**:
```
Dr. Hutang PPh 4(2)             amount
    Cr. Bank                        amount
```

### Setor PPh 29

**Digunakan**: Bayar PPh kurang bayar tahunan

**Jurnal**:
```
Dr. Hutang PPh 29               amount
    Cr. Bank                        amount
```

---

## Template Payroll

### Posting Gaji

**Digunakan**: Posting hasil perhitungan payroll

**Jurnal**:
```
Dr. Beban Gaji                  gross_salary
Dr. Beban BPJS Perusahaan       bpjs_company
    Cr. Hutang Gaji                 net_salary
    Cr. Hutang BPJS                 bpjs_total
    Cr. Hutang PPh 21               pph21
```

### Bayar Gaji

**Digunakan**: Transfer gaji ke rekening karyawan

**Jurnal**:
```
Dr. Hutang Gaji                 amount
    Cr. Bank                        amount
```

### Setor BPJS

**Digunakan**: Bayar iuran BPJS ke BPJS

**Jurnal**:
```
Dr. Hutang BPJS                 amount
    Cr. Bank                        amount
```

---

## Template Inventory

### Pembelian Barang Dagang

**Digunakan**: Beli barang untuk dijual kembali

**Jurnal**:
```
Dr. Persediaan Barang Dagang    amount
    Cr. Bank/Kas                    amount
```

### Penjualan dengan HPP

**Digunakan**: Jual barang (sistem hitung HPP otomatis)

**Jurnal**:
```
Dr. Bank/Kas                    selling_price
    Cr. Penjualan                   selling_price

Dr. HPP                         cogs
    Cr. Persediaan Barang Dagang    cogs
```

### Adjustment Stok (+)

**Digunakan**: Tambah stok (stock opname surplus)

**Jurnal**:
```
Dr. Persediaan                  amount
    Cr. Selisih Persediaan          amount
```

### Adjustment Stok (-)

**Digunakan**: Kurang stok (rusak, hilang)

**Jurnal**:
```
Dr. Beban Selisih Persediaan    amount
    Cr. Persediaan                  amount
```

---

## Membuat Template Baru

Jika template standar tidak mencukupi:

1. Buka menu **Pengaturan** > **Template**
2. Klik **Template Baru**
3. Isi:
   - Nama template
   - Kategori (Pendapatan, Pengeluaran, Transfer, dll)
   - Klasifikasi Arus Kas (Operasional, Investasi, Pendanaan)
   - Tipe Template (Sederhana / Terperinci)
   - Baris jurnal dengan formula
4. (Opsional) Buka bagian **Metadata AI** untuk mengisi metadata yang digunakan AI assistant saat mencocokkan template
5. Klik **Simpan**

Lihat [Setup Awal](01-setup-awal.md) untuk panduan detail.

---

## Metadata AI pada Template

Setiap template memiliki field metadata opsional yang digunakan oleh AI assistant untuk mencocokkan template secara otomatis saat mencatat transaksi. Metadata ini dapat dikelola melalui bagian **Metadata AI** (collapsible) pada form template.

### Field Metadata

| Field | Deskripsi | Contoh |
|-------|-----------|--------|
| **Deskripsi Semantik** | Penjelasan kapan dan bagaimana template digunakan | "Gunakan template ini untuk mencatat pembayaran tagihan listrik bulanan ke PLN" |
| **Keywords** | Kata kunci untuk matching, dipisah koma | `listrik, pln, token, utility` |
| **Contoh Merchant** | Nama merchant yang biasa menggunakan template ini | `PLN, PLN Mobile, Tokopedia PLN` |
| **Rentang Nominal** | Rentang jumlah transaksi tipikal (min-max) | Rp 50.000 - Rp 5.000.000 |
| **Pola Merchant** | Regex pattern untuk matching merchant name | `.*pln.*, .*listrik.*` |

### Tampilan di Halaman Detail

Pada halaman detail template, metadata ditampilkan sebagai card **Metadata AI** dengan:
- Deskripsi semantik sebagai paragraf teks
- Keywords sebagai badge hijau
- Contoh merchant sebagai badge biru
- Rentang nominal dalam format `Rp X - Rp Y`
- Pola merchant sebagai badge monospace

Card ini hanya muncul jika minimal satu field metadata terisi.

![Metadata AI pada Detail Template](screenshots/service/templates-metadata-detail.png)

### Form Edit Metadata

Buka bagian **Metadata AI** (collapsible) pada form edit template untuk mengisi atau mengubah metadata.

![Form Metadata AI](screenshots/service/templates-metadata-form.png)

### Cara AI Menggunakan Metadata

Lihat [Bantuan AI](13-bantuan-ai.md#template-journal-dengan-metadata) untuk penjelasan cara AI matching template berdasarkan metadata.

---

## Lihat Juga

- [Setup Awal](01-setup-awal.md) - Mengelola template
- [Tutorial Akuntansi](02-tutorial-akuntansi.md) - Cara menggunakan template
- [Referensi Akun](12-lampiran-akun.md) - Daftar akun yang digunakan
