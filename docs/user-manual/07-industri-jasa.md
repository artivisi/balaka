# Industri Jasa

Panduan lengkap untuk perusahaan jasa (IT Services, Konsultan, Agency) dengan status PKP.

## Karakteristik Industri Jasa

### Ciri Khas

- **Produk tidak berwujud** - Menjual keahlian dan waktu
- **Project-based** - Pendapatan dari proyek dengan milestone
- **Time & Material** - Biaya berdasarkan jam kerja
- **Retainer** - Pendapatan berulang bulanan
- **Tidak ada inventory** - Tidak ada persediaan barang

### Alur Bisnis Tipikal

```
Klien → Proposal → Kontrak → Proyek → Milestone → Invoice → Pembayaran
```

---

## Client Management

### Melihat Daftar Klien

Buka menu **Klien** > **Daftar Klien**.

![Daftar Klien](screenshots/clients-list.png)

### Detail Klien

Klik klien untuk melihat:
- Informasi kontak
- Daftar proyek
- History invoice
- Total revenue dari klien

![Detail Klien](screenshots/clients-detail.png)

### Menambah Klien Baru

1. Klik **Klien Baru**

![Form Klien](screenshots/clients-form.png)

2. Isi:
   - Kode klien (unik)
   - Nama perusahaan
   - NPWP
   - Alamat
   - Contact person
   - Email, telepon
3. Klik **Simpan**

---

## Project Management

### Melihat Daftar Proyek

Buka menu **Proyek** > **Daftar Proyek**.

![Daftar Proyek](screenshots/projects-list.png)

### Detail Proyek

![Detail Proyek](screenshots/projects-detail.png)

Informasi yang ditampilkan:
- Status proyek
- Progress milestone
- Total nilai kontrak
- Pendapatan yang sudah diakui
- Invoice yang sudah diterbitkan

### Menambah Proyek Baru

1. Klik **Proyek Baru**

![Form Proyek](screenshots/projects-form.png)

2. Isi:
   - Kode proyek
   - Nama proyek
   - Klien (pilih dari dropdown)
   - Nilai kontrak
   - Tanggal mulai & target selesai
   - Deskripsi
3. Tab **Milestone** - Tambah milestone:
   - Nama milestone
   - Bobot (%)
   - Target tanggal
4. Klik **Simpan**

### Workflow Proyek

```
DRAFT → ACTIVE → COMPLETED
```

| Status | Arti |
|--------|------|
| DRAFT | Proyek belum dimulai |
| ACTIVE | Proyek sedang berjalan |
| COMPLETED | Proyek selesai |

### Update Progress Milestone

1. Buka detail proyek
2. Klik milestone
3. Update status:
   - Tanggal aktual selesai
   - Catatan
4. Klik **Simpan**

Saat milestone selesai, pendapatan dapat diakui proporsional sesuai bobot.

---

## Template Transaksi Jasa

### Template Standar

| Template | Fungsi |
|----------|--------|
| Pendapatan Jasa + PPN | Pendapatan dengan PPN 11% |
| Pendapatan Jasa tanpa PPN | Pendapatan tanpa PPN |
| Terima DP Proyek | DP masuk Pendapatan Diterima Dimuka |
| Pengakuan Pendapatan | Recognize revenue dari DDM |
| Beban Operasional | Pengeluaran operasional |

### Melihat Template

Buka menu **Pengaturan** > **Template**.

![Daftar Template](screenshots/templates-list.png)

### Detail Template

![Detail Template](screenshots/templates-detail.png)

---

## Invoice dan Penagihan

### Melihat Daftar Invoice

Buka menu **Invoice** > **Daftar Invoice**.

![Daftar Invoice](screenshots/invoices-list.png)

### Membuat Invoice

1. Klik **Invoice Baru**
2. Pilih klien
3. Pilih proyek (opsional)
4. Isi item invoice:
   - Deskripsi
   - Quantity
   - Harga satuan
5. Sistem menghitung:
   - Subtotal
   - PPN (jika PKP)
   - Total
6. Klik **Simpan**

### Workflow Invoice

```
DRAFT → SENT → PAID
```

### Mencatat Pembayaran Invoice

Saat klien membayar:

1. Buka invoice
2. Klik **Terima Pembayaran**
3. Isi:
   - Tanggal terima
   - Jumlah (bisa partial)
   - Rekening penerima
4. Klik **Simpan**

Jurnal yang dibuat:
```
Dr. Bank                    xxx
    Cr. Piutang Usaha           xxx
```

---

## Profitabilitas Proyek

### Laporan Profitabilitas Proyek

Buka menu **Laporan** > **Profitabilitas Proyek**.

![Profitabilitas Proyek](screenshots/reports-project-profitability.png)

Metrik yang ditampilkan:
- Total revenue proyek
- Total cost (gaji, vendor, dll)
- Gross profit
- Profit margin (%)

### Laporan Profitabilitas Klien

Buka menu **Laporan** > **Profitabilitas Klien**.

![Profitabilitas Klien](screenshots/reports-client-profitability.png)

Agregasi per klien:
- Total revenue dari klien
- Total cost
- Profit
- Jumlah proyek

---

## Skenario Transaksi

### Skenario 1: Terima DP Proyek

1. Buka **Transaksi** > **Transaksi Baru**
2. Pilih template **Terima DP Proyek**
3. Isi:
   - Jumlah DP
   - Proyek terkait
   - Rekening penerima
4. Jurnal:
   ```
   Dr. Bank                        xxx
       Cr. Pendapatan Diterima Dimuka  xxx
   ```

### Skenario 2: Pengakuan Pendapatan (Milestone Selesai)

1. Pilih template **Pengakuan Pendapatan**
2. Isi:
   - Jumlah (sesuai bobot milestone)
   - Proyek
3. Jurnal:
   ```
   Dr. Pendapatan Diterima Dimuka  xxx
       Cr. Pendapatan Jasa             xxx
   ```

### Skenario 3: Terima Pembayaran Invoice

1. Pilih template **Terima Pembayaran Piutang**
2. Isi jumlah
3. Jurnal:
   ```
   Dr. Bank                        xxx
       Cr. Piutang Usaha               xxx
   ```

---

## Tips Industri Jasa

1. **Track time** - Catat jam kerja per proyek untuk analisis cost
2. **Milestone jelas** - Definisikan deliverable yang terukur
3. **Invoice tepat waktu** - Jangan tunda penagihan
4. **Review profitabilitas** - Analisis per proyek secara berkala
5. **Manage cashflow** - Monitor piutang dan aging

---

## Lihat Juga

- [Pengantar Industri](06-pengantar-industri.md) - Perbandingan industri
- [Perpajakan](04-perpajakan.md) - PPN untuk jasa
- [Tutorial Akuntansi](02-tutorial-akuntansi.md) - Jurnal dasar
