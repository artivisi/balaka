# Industri Dagang

Panduan lengkap untuk bisnis perdagangan (Online Seller, Retailer, Distributor).

## Karakteristik Industri Dagang

### Ciri Khas

- **Jual barang jadi** - Tidak ada proses produksi
- **Inventory management** - Persediaan barang dagang
- **HPP (Harga Pokok Penjualan)** - Biaya barang yang dijual
- **Multi-channel** - Marketplace, toko fisik, website

### Alur Bisnis Tipikal

```
Supplier → Purchase → Inventory → Sale → Customer
```

---

## Manajemen Produk

### Melihat Daftar Produk

Buka menu **Inventori** > **Produk**.

![Daftar Produk](screenshots/products-list.png)

### Menambah Produk Baru

1. Klik **Produk Baru**

![Form Produk](screenshots/products-form.png)

2. Isi:
   - SKU (Stock Keeping Unit)
   - Nama produk
   - Kategori
   - Harga jual
   - Metode costing (FIFO / Weighted Average)
   - Akun persediaan
   - Akun HPP
3. Klik **Simpan**

### Kategori Produk

Buka menu **Inventori** > **Kategori**.

![Daftar Kategori](screenshots/product-categories-list.png)

---

## Metode Penilaian Persediaan

### FIFO (First In First Out)

Barang yang masuk lebih dulu, keluar lebih dulu.

**Contoh:**
| Tanggal | Transaksi | Qty | Harga | Total |
|---------|-----------|-----|-------|-------|
| 1 Jan | Beli | 10 | 100.000 | 1.000.000 |
| 5 Jan | Beli | 10 | 110.000 | 1.100.000 |
| 10 Jan | Jual | 15 | - | HPP = 10×100.000 + 5×110.000 = 1.550.000 |

### Weighted Average

Harga rata-rata tertimbang.

**Contoh:**
| Tanggal | Transaksi | Qty | Harga | Total | Avg Cost |
|---------|-----------|-----|-------|-------|----------|
| 1 Jan | Beli | 10 | 100.000 | 1.000.000 | 100.000 |
| 5 Jan | Beli | 10 | 110.000 | 1.100.000 | 105.000 |
| 10 Jan | Jual | 15 | - | HPP = 15×105.000 = 1.575.000 | - |

---

## Transaksi Pembelian

### Mencatat Pembelian Barang

1. Buka menu **Inventori** > **Transaksi** > **Pembelian Baru**

![Form Pembelian](screenshots/inventory-purchase.png)

2. Isi:
   - Tanggal
   - Supplier
   - Produk, qty, harga beli
   - Rekening pembayaran
3. Klik **Simpan & Posting**

### Jurnal Pembelian

```
Dr. Persediaan Barang Dagang    xxx
    Cr. Kas/Bank                    xxx
```

---

## Transaksi Penjualan

### Mencatat Penjualan Barang

1. Buka menu **Inventori** > **Transaksi** > **Penjualan Baru**

![Form Penjualan](screenshots/inventory-sale.png)

2. Isi:
   - Tanggal
   - Customer/Channel (Tokopedia, Shopee, dll)
   - Produk, qty, harga jual
   - Rekening penerima
3. Klik **Simpan & Posting**

### Jurnal Penjualan (Auto-COGS)

Sistem otomatis menghitung HPP berdasarkan metode costing:

```
Dr. Kas/Bank                    xxx  (harga jual)
    Cr. Penjualan                   xxx

Dr. HPP                         xxx  (harga beli FIFO/WA)
    Cr. Persediaan Barang Dagang    xxx
```

---

## Laporan Persediaan

### Stok Barang

Buka menu **Inventori** > **Stok**.

![Stok Barang](screenshots/stock-list.png)

### Transaksi Inventori

Buka menu **Inventori** > **Transaksi**.

![Transaksi Inventori](screenshots/inventory-transactions.png)

### Laporan Saldo Stok

Buka menu **Inventori** > **Laporan** > **Saldo Stok**.

![Saldo Stok](screenshots/inventory-stock-balance.png)

### Laporan Mutasi Stok

Buka menu **Inventori** > **Laporan** > **Mutasi Stok**.

![Mutasi Stok](screenshots/inventory-stock-movement.png)

Menampilkan kartu stok per produk:
- Tanggal transaksi
- Tipe (masuk/keluar)
- Qty
- Harga
- Saldo running

---

## Profitabilitas Produk

### Laporan Profitabilitas

Buka menu **Inventori** > **Laporan** > **Profitabilitas Produk**.

![Profitabilitas Produk](screenshots/inventory-reports-profitability.png)

Metrik per produk:
- Total penjualan (revenue)
- Total HPP (cost)
- Gross profit
- Margin (%)

---

## Skenario Transaksi

### Skenario 1: Beli Barang dari Supplier

1. Buka **Inventori** > **Transaksi** > **Pembelian Baru**
2. Isi produk dan qty
3. Jurnal:
   ```
   Dr. Persediaan               5.000.000
       Cr. Bank                     5.000.000
   ```

### Skenario 2: Jual Barang via Tokopedia

1. Buka **Inventori** > **Transaksi** > **Penjualan Baru**
2. Pilih channel: Tokopedia
3. Isi produk dan qty
4. Sistem hitung HPP (FIFO/WA)
5. Jurnal:
   ```
   Dr. Bank                     7.500.000
       Cr. Penjualan                7.500.000

   Dr. HPP                      5.000.000
       Cr. Persediaan               5.000.000
   ```
   Gross margin: 33%

### Skenario 3: Adjustment Stok (Selisih)

1. Buka **Inventori** > **Transaksi** > **Adjustment Baru**
2. Pilih produk
3. Isi qty adjustment (+/-)
4. Pilih reason (Rusak, Hilang, Stock Opname)
5. Jurnal (jika minus):
   ```
   Dr. Beban Selisih Persediaan xxx
       Cr. Persediaan               xxx
   ```

---

## Tips Industri Dagang

1. **Stock opname rutin** - Minimal bulanan untuk produk fast-moving
2. **Monitor aging** - Produk slow-moving berisiko obsolete
3. **Track per channel** - Analisis profitabilitas per marketplace
4. **Safety stock** - Jaga minimum stock untuk produk laris
5. **Reorder point** - Set alert ketika stok menipis

---

## Lihat Juga

- [Pengantar Industri](06-pengantar-industri.md) - Perbandingan industri
- [Tutorial Akuntansi](02-tutorial-akuntansi.md) - Jurnal dasar
- [Perpajakan](04-perpajakan.md) - PPN pembelian/penjualan
