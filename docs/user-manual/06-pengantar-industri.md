# Pengantar Industri

Panduan memahami perbedaan praktik akuntansi berdasarkan jenis industri.

## Jenis Industri

### Klasifikasi Industri

| Kategori | Karakteristik | Contoh |
|----------|---------------|--------|
| **Jasa** | Menjual keahlian/waktu | Konsultan, Software House |
| **Dagang** | Beli-jual barang | Toko online, Retailer |
| **Manufaktur** | Produksi barang | Pabrik, F&B Production |
| **Pendidikan** | Layanan edukasi | Universitas, Kursus |

### Perbedaan Utama

| Aspek | Jasa | Dagang | Manufaktur |
|-------|------|--------|------------|
| **Produk** | Tidak berwujud | Barang jadi | Barang diproduksi |
| **Inventory** | Tidak ada | Ada (barang dagang) | Ada (bahan baku + WIP + barang jadi) |
| **HPP** | Tidak ada | Harga beli barang | Biaya produksi |
| **Revenue recognition** | Saat jasa selesai | Saat barang diserahkan | Saat barang diserahkan |

---

## Industri yang Didukung

### 1. Industri Jasa (IT Services PKP)

**Karakteristik:**
- Pendapatan berbasis proyek atau retainer
- Tidak ada persediaan barang
- Biaya utama: gaji karyawan
- Wajib PPN (PKP)

**Fitur utama:**
- Client Management
- Project Management dengan Milestone
- Invoice dan Penagihan
- Profitabilitas per Klien/Proyek

**Seed pack:** `it-service`

Lihat: [Industri Jasa](07-industri-jasa.md)

### 2. Industri Dagang (Online Seller)

**Karakteristik:**
- Pendapatan dari penjualan barang
- Persediaan dengan metode FIFO atau Weighted Average
- HPP dihitung dari harga beli
- Multi-channel (Tokopedia, Shopee, dll)

**Fitur utama:**
- Product Management
- Inventory Transactions (Purchase, Sale, Adjustment)
- Stock Reports
- Profitabilitas Produk

**Seed pack:** `online-seller`

Lihat: [Industri Dagang](08-industri-dagang.md)

### 3. Industri Manufaktur (Coffee Shop) [TBD]

**Karakteristik:**
- Produksi barang dari bahan baku
- Bill of Materials (BOM)
- Kalkulasi HPP produksi
- Persediaan multi-level (bahan baku, WIP, barang jadi)

**Fitur utama:**
- BOM (Bill of Materials)
- Production Orders
- Cost Calculation
- Inventory multi-level

**Seed pack:** `coffee-shop`

Lihat: [Industri Manufaktur](09-industri-manufaktur.md)

### 4. Industri Pendidikan (Campus) [TBD]

**Karakteristik:**
- Pendapatan berbasis semester/tahun ajaran
- Tagihan ke mahasiswa (SPP, Uang Pangkal)
- Beasiswa dan potongan
- Piutang mahasiswa dengan cicilan

**Fitur utama:**
- Student Management
- Billing (SPP, Praktikum, Wisuda)
- Payment dan Installments
- Scholarship Management
- Receivables Aging per Student

**Seed pack:** `campus`

Lihat: [Industri Pendidikan](10-industri-pendidikan.md)

---

## Perbedaan Praktik Akuntansi

### Chart of Accounts

Setiap industri memiliki COA yang disesuaikan:

| Industri | Akun Khusus |
|----------|-------------|
| Jasa | WIP Proyek, Pendapatan Diterima Dimuka |
| Dagang | Persediaan Barang Dagang, HPP |
| Manufaktur | Bahan Baku, WIP, Barang Jadi, Overhead |
| Pendidikan | Piutang Mahasiswa, Pendapatan SPP |

### Template Transaksi

Template disesuaikan dengan alur bisnis industri:

**Jasa:**
- Pendapatan Jasa + PPN
- Terima DP Proyek
- Pengakuan Pendapatan Milestone

**Dagang:**
- Pembelian Barang Dagang
- Penjualan dengan HPP
- Retur Pembelian/Penjualan

**Manufaktur:**
- Pembelian Bahan Baku
- Production Order
- Barang Jadi Masuk Gudang

**Pendidikan:**
- Tagihan SPP
- Terima Pembayaran SPP
- Beasiswa

### Laporan Khusus

| Industri | Laporan Khusus |
|----------|----------------|
| Jasa | Profitabilitas Proyek, Profitabilitas Klien |
| Dagang | Kartu Stok, Valuasi Persediaan, Profitabilitas Produk |
| Manufaktur | Production Cost Report, BOM Costing |
| Pendidikan | Receivables Aging per Student, Revenue per Program |

---

## Memilih Seed Pack

### Pertimbangan

1. **Aktivitas utama** - Apa yang dijual? Jasa, barang, atau produksi?
2. **Kompleksitas inventory** - Apakah ada persediaan? Single atau multi-level?
3. **Revenue recognition** - Kapan pendapatan diakui?
4. **Kewajiban pajak** - PKP atau non-PKP?

### Rekomendasi

| Jika bisnis Anda... | Gunakan seed pack |
|---------------------|-------------------|
| Jual jasa konsultasi, wajib PPN | IT Services |
| Jual barang via marketplace | Online Seller |
| Produksi makanan/minuman | Coffee Shop |
| Institusi pendidikan | Campus |

---

## Custom Industry

Jika tidak ada seed pack yang cocok:

1. Pilih seed pack yang paling mendekati
2. Modifikasi COA sesuai kebutuhan
3. Buat template transaksi custom
4. Hubungi tim ArtiVisi untuk konsultasi

---

## Lihat Juga

- [Setup Awal](01-setup-awal.md) - Import seed pack
- [Industri Jasa](07-industri-jasa.md)
- [Industri Dagang](08-industri-dagang.md)
- [Industri Manufaktur](09-industri-manufaktur.md)
- [Industri Pendidikan](10-industri-pendidikan.md)
