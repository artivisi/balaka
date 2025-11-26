# Dashboard

## Halaman Dashboard

Dashboard adalah halaman utama yang menampilkan ringkasan kondisi keuangan bisnis Anda. Dashboard menggunakan HTMX untuk memuat data KPI secara dinamis.

## Kartu KPI

Dashboard menampilkan 8 kartu KPI (Key Performance Indicator) yang diperbarui secara real-time:

### Kartu Pendapatan & Beban

| Kartu | Deskripsi |
|-------|-----------|
| **Pendapatan** | Total pendapatan bulan berjalan dengan persentase perubahan dari bulan sebelumnya |
| **Beban** | Total beban bulan berjalan dengan persentase perubahan dari bulan sebelumnya |
| **Laba Bersih** | Selisih pendapatan dan beban dengan persentase perubahan dari bulan sebelumnya |
| **Margin Laba** | Persentase laba bersih terhadap pendapatan dengan selisih poin dari bulan sebelumnya |

### Kartu Posisi Keuangan

| Kartu | Deskripsi |
|-------|-----------|
| **Kas & Bank** | Total saldo akun kas dan bank |
| **Piutang** | Total saldo piutang usaha |
| **Hutang** | Total saldo hutang usaha |
| **Transaksi** | Jumlah transaksi yang dicatat pada bulan berjalan |

## Pemilihan Periode

Gunakan pemilih bulan di bagian atas untuk melihat KPI periode lain:

1. Klik pada field pemilih bulan
2. Pilih bulan dan tahun yang diinginkan
3. Data KPI akan diperbarui secara otomatis

Periode yang ditampilkan akan terlihat di header kartu KPI (contoh: "November 2025").

## Indikator Perubahan

Setiap kartu menampilkan indikator perubahan dibandingkan bulan sebelumnya:

- **Warna hijau** dengan panah naik: peningkatan (positif untuk pendapatan/laba, perlu perhatian untuk beban)
- **Warna merah** dengan panah turun: penurunan
- Format: persentase untuk nilai moneter, poin untuk margin

## Navigasi

Gunakan menu sidebar di sebelah kiri untuk mengakses fitur lainnya:

| Menu | Fungsi |
|------|--------|
| Dashboard | Halaman utama |
| Transaksi | Pencatatan transaksi |
| Buku Besar | Laporan per akun |
| Laporan | Laporan keuangan |
| Amortisasi | Jadwal amortisasi |
| Klien | Manajemen klien |
| Proyek | Manajemen proyek |
| Invoice | Daftar invoice |
| Akun | Bagan akun |
| Template | Template jurnal |
