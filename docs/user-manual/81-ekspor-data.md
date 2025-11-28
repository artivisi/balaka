# Ekspor Data

## Ringkasan

Fitur ekspor data memungkinkan Anda mengunduh seluruh data perusahaan dalam format ZIP. Fitur ini tersedia untuk memenuhi:

- Kewajiban perpajakan (penyimpanan data 10 tahun - UU No. 28/2007)
- Portabilitas data (hak akses data sesuai PP 50/2022 PDPJP)
- Backup tambahan di luar sistem

## Mengakses Fitur Ekspor

1. Buka menu **Pengaturan** dari sidebar
2. Scroll ke bagian **Pengaturan Lainnya**
3. Klik **Ekspor Data**

## Halaman Ekspor Data

Halaman ekspor data menampilkan:

### Statistik Data

Ringkasan jumlah data yang akan diekspor:

| Kategori | Keterangan |
|----------|------------|
| Akun (COA) | Jumlah akun di Chart of Accounts |
| Jurnal | Jumlah jurnal entry |
| Transaksi | Jumlah transaksi |
| Klien | Jumlah data klien |
| Proyek | Jumlah proyek |
| Invoice | Jumlah invoice |
| Karyawan | Jumlah karyawan |
| Payroll | Jumlah payroll run |
| Dokumen | Jumlah file dokumen |

### Isi File Ekspor

File ZIP yang diunduh berisi:

| File | Isi |
|------|-----|
| `chart_of_accounts.csv` | Daftar akun (COA) |
| `journal_entries.csv` | Semua jurnal termasuk yang void |
| `transactions.csv` | Semua transaksi |
| `clients.csv` | Data klien |
| `projects.csv` | Data proyek |
| `invoices.csv` | Semua invoice |
| `employees.csv` | Data karyawan |
| `payroll_runs.csv` | Riwayat payroll |
| `payroll_details.csv` | Detail slip gaji |
| `audit_logs.csv` | Log aktivitas |
| `documents/` | File dokumen lampiran |
| `MANIFEST.md` | Metadata ekspor |

## Cara Mengekspor Data

1. Buka halaman **Ekspor Data** (lihat langkah di atas)
2. Review statistik data yang akan diekspor
3. Klik tombol **Ekspor Semua Data**
4. Tunggu proses ekspor selesai
5. File ZIP akan terunduh otomatis

## Format File CSV

Semua file CSV menggunakan:

- **Encoding:** UTF-8
- **Delimiter:** Koma (,)
- **Quote character:** Petik ganda (")
- **Header:** Baris pertama adalah nama kolom

## File Manifest

File `MANIFEST.md` berisi:

- Tanggal ekspor
- Informasi perusahaan (nama, NPWP)
- Jumlah record per kategori

## Tips dan Catatan

1. **Waktu proses:** Ekspor data besar dapat memakan waktu beberapa menit
2. **Ukuran file:** Tergantung jumlah data dan dokumen
3. **Backup rutin:** Disarankan ekspor bulanan sebagai backup tambahan
4. **Penyimpanan:** Simpan file ekspor di lokasi terpisah dari server

## Lihat Juga

- [Kebijakan Penyimpanan Data](80-kebijakan-data.md) - Informasi tentang retensi data
