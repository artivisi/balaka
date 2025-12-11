# Industri Manufaktur

> **[TBD]** Bagian ini akan dilengkapi setelah functional test untuk industri manufaktur selesai.

Panduan untuk bisnis manufaktur (Coffee Shop, Bakery, F&B Production).

## Karakteristik Manufaktur

### Ciri Khas

- **Produksi barang** - Mengolah bahan baku menjadi barang jadi
- **Bill of Materials (BOM)** - Daftar komponen untuk memproduksi satu unit
- **Multi-level inventory** - Bahan baku, WIP, barang jadi
- **Cost calculation** - HPP berdasarkan komponen + overhead

### Alur Bisnis Tipikal

```
Bahan Baku → Production Order → WIP → Barang Jadi → Sale
```

---

## Bill of Materials (BOM)

### Konsep BOM

BOM adalah "resep" untuk memproduksi satu unit produk. Berisi:
- Komponen (bahan baku)
- Quantity per unit
- Harga komponen (dari inventory)

### Melihat Daftar BOM

Buka menu **Produksi** > **BOM**.

![Daftar BOM](screenshots/bom-list.png)

### Membuat BOM Baru

![Form BOM](screenshots/bom-form.png)

**Contoh: Kopi Susu Gula Aren**

| Komponen | Qty | Unit | Harga | Total |
|----------|-----|------|-------|-------|
| Espresso Shot | 2 | shot | 3.000 | 6.000 |
| Susu Full Cream | 150 | ml | 30/ml | 4.500 |
| Gula Aren Cair | 30 | ml | 50/ml | 1.500 |
| Cup + Tutup | 1 | pcs | 1.500 | 1.500 |
| **Total Cost** | | | | **13.500** |

Harga jual: Rp 28.000 → Margin: 52%

---

## Production Order

### Konsep Production Order

Production Order adalah perintah produksi untuk menghasilkan sejumlah unit barang jadi.

### Melihat Daftar Production Order

Buka menu **Produksi** > **Production Order**.

![Daftar Production Order](screenshots/production-list.png)

### Membuat Production Order

![Form Production Order](screenshots/production-form.png)

**Workflow:**
```
DRAFT → IN_PROGRESS → COMPLETED
```

**Saat COMPLETED:**
1. Bahan baku keluar dari inventory
2. Barang jadi masuk ke inventory
3. HPP dihitung dari total cost komponen

---

## Kalkulasi Biaya Produksi

### Komponen Biaya

| Komponen | Contoh |
|----------|--------|
| **Direct Material** | Bahan baku sesuai BOM |
| **Direct Labor** | Gaji tenaga produksi |
| **Overhead** | Listrik, sewa, depresiasi mesin |

### HPP per Unit

```
HPP = Direct Material + Direct Labor + Overhead
    = BOM Cost + (Labor/Unit) + (Overhead/Unit)
```

---

## Laporan Produksi

### Laporan yang Tersedia

Buka menu **Produksi** > **Laporan**.

![Laporan Produksi](screenshots/inventory-reports.png)

- Production Cost Report
- BOM Costing Analysis
- Material Usage Report

---

## Skenario Transaksi [TBD]

### Skenario 1: Beli Bahan Baku

```
Dr. Persediaan Bahan Baku       xxx
    Cr. Kas/Bank                    xxx
```

### Skenario 2: Produksi (BOM Execution)

```
Dr. Persediaan Barang Jadi      xxx  (HPP produksi)
    Cr. Persediaan Bahan Baku       xxx
```

### Skenario 3: Jual Barang Jadi

```
Dr. Kas/Bank                    xxx  (harga jual)
    Cr. Penjualan                   xxx

Dr. HPP                         xxx  (dari barang jadi)
    Cr. Persediaan Barang Jadi      xxx
```

---

## Tips Industri Manufaktur [TBD]

1. **BOM akurat** - Update BOM jika ada perubahan resep
2. **Batch production** - Produksi dalam batch untuk efisiensi
3. **FIFO untuk bahan baku** - Terutama untuk bahan perishable
4. **Track waste** - Catat bahan terbuang untuk analisis
5. **Review margin** - Analisis profitabilitas per produk

---

## Status Implementasi

| Fitur | Status |
|-------|--------|
| BOM Entity | ✅ Complete |
| Production Order Entity | ✅ Complete |
| BOM UI | ✅ Complete |
| Production Order UI | ✅ Complete |
| Functional Tests | ⏳ Pending |
| Seed Pack (coffee-shop) | ⏳ Pending |
| User Manual Screenshots | ⏳ Pending |

---

## Lihat Juga

- [Pengantar Industri](06-pengantar-industri.md) - Perbandingan industri
- [Industri Dagang](08-industri-dagang.md) - Inventory management dasar
- [Tutorial Akuntansi](02-tutorial-akuntansi.md) - Jurnal dasar
