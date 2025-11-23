const fs = require('fs');
const path = require('path');
const ejs = require('ejs');

const SCREENSHOTS_DIR = path.join(__dirname, '..', 'screenshots');
const TEMPLATES_DIR = path.join(__dirname, '..', 'templates');
const DIST_DIR = path.join(__dirname, '..', 'dist');

// Manual structure definition
const manualStructure = {
  title: 'Panduan Pengguna - Aplikasi Akunting',
  version: '1.0.0',
  lastUpdated: new Date().toISOString().split('T')[0],
  sections: [
    {
      id: 'introduction',
      title: 'Pendahuluan',
      content: `
## Selamat Datang

Aplikasi Akunting adalah sistem pencatatan keuangan yang dirancang khusus untuk usaha kecil dan menengah (UKM) di Indonesia.
Aplikasi ini mendukung standar akuntansi Indonesia (PSAK) dan kepatuhan perpajakan.

### Fitur Utama

- **Bagan Akun (Chart of Accounts)** - Struktur akun yang sesuai standar akuntansi
- **Template Jurnal** - Mempermudah pencatatan transaksi berulang
- **Transaksi** - Pencatatan transaksi dengan jurnal otomatis
- **Buku Besar** - Laporan saldo dan mutasi per akun
- **Laporan Keuangan** - Neraca, Laba Rugi, dan Arus Kas

### Persyaratan Sistem

- Browser modern (Chrome, Firefox, Safari, Edge)
- Koneksi internet stabil
- Resolusi layar minimal 1024x768
      `,
      pages: []
    },
    {
      id: 'authentication',
      title: 'Login & Autentikasi',
      content: `
## Masuk ke Aplikasi

Untuk menggunakan aplikasi, Anda harus login dengan akun yang telah didaftarkan.

### Langkah-langkah Login

1. Buka halaman aplikasi di browser
2. Masukkan **Username** Anda
3. Masukkan **Password** Anda
4. Klik tombol **Masuk**

### Tips Keamanan

- Jangan bagikan password Anda kepada orang lain
- Gunakan password yang kuat (kombinasi huruf, angka, simbol)
- Logout setelah selesai menggunakan aplikasi
      `,
      pages: ['login']
    },
    {
      id: 'dashboard',
      title: 'Dashboard',
      content: `
## Halaman Dashboard

Dashboard menampilkan ringkasan kondisi keuangan bisnis Anda secara real-time.

### Komponen Dashboard

- **Total Pendapatan** - Jumlah pendapatan periode berjalan
- **Total Pengeluaran** - Jumlah pengeluaran periode berjalan
- **Laba/Rugi Bersih** - Selisih pendapatan dan pengeluaran
- **Transaksi Terakhir** - Daftar transaksi terbaru

### Navigasi

Gunakan menu sidebar di sebelah kiri untuk mengakses fitur lainnya:
- Transaksi
- Buku Besar
- Laporan
- Akun
- Template
      `,
      pages: ['dashboard']
    },
    {
      id: 'accounts',
      title: 'Bagan Akun',
      content: `
## Bagan Akun (Chart of Accounts)

Bagan Akun adalah daftar semua akun yang digunakan untuk mencatat transaksi keuangan.

### Struktur Kode Akun

| Kode | Tipe Akun |
|------|-----------|
| 1.x.xx | Aset (Harta) |
| 2.x.xx | Liabilitas (Kewajiban) |
| 3.x.xx | Ekuitas (Modal) |
| 4.x.xx | Pendapatan |
| 5.x.xx | Beban |

### Menambah Akun Baru

1. Klik tombol **Tambah Akun** di halaman Bagan Akun
2. Isi **Kode Akun** sesuai format (contoh: 1.1.01)
3. Isi **Nama Akun** (contoh: Kas)
4. Pilih **Tipe Akun** (Aset, Liabilitas, dll)
5. Pilih **Akun Induk** jika ada
6. Pilih **Saldo Normal** (Debit/Kredit)
7. Klik **Simpan**

### Saldo Normal

- **Debit** - Untuk akun Aset dan Beban
- **Kredit** - Untuk akun Liabilitas, Ekuitas, dan Pendapatan
      `,
      pages: ['accounts-list', 'accounts-form-new']
    },
    {
      id: 'templates',
      title: 'Template Jurnal',
      content: `
## Template Jurnal

Template Jurnal mempermudah pencatatan transaksi yang berulang dengan pola yang sama.

### Kategori Template

- **Pendapatan** - Untuk mencatat penerimaan dari penjualan/jasa
- **Pengeluaran** - Untuk mencatat biaya operasional
- **Pembayaran** - Untuk mencatat pembayaran hutang
- **Penerimaan** - Untuk mencatat penerimaan piutang
- **Transfer** - Untuk mencatat perpindahan antar akun

### Membuat Template Baru

1. Klik tombol **Template Baru**
2. Isi **Nama Template** (contoh: Pendapatan Jasa Konsultasi)
3. Pilih **Kategori** yang sesuai
4. Pilih **Klasifikasi Arus Kas** (Operasional/Investasi/Pendanaan)
5. Konfigurasi **Baris Jurnal**:
   - Pilih akun untuk setiap baris
   - Tentukan posisi (Debit/Kredit)
   - Masukkan formula jika diperlukan (contoh: \`amount\`, \`amount * 0.11\`)
6. Klik **Simpan Template**

### Formula yang Didukung

| Formula | Keterangan |
|---------|------------|
| \`amount\` | Jumlah yang diinput |
| \`amount * 0.11\` | PPN 11% |
| \`amount / 1.11\` | DPP dari harga inklusif PPN |
      `,
      pages: ['templates-list', 'templates-detail', 'templates-form-new']
    },
    {
      id: 'transactions',
      title: 'Transaksi',
      content: `
## Pencatatan Transaksi

Transaksi adalah pencatatan aktivitas keuangan yang terjadi dalam bisnis Anda.

### Status Transaksi

- **Draft** - Transaksi tersimpan tapi belum mempengaruhi saldo
- **Posted** - Transaksi sudah diposting ke buku besar
- **Void** - Transaksi dibatalkan

### Membuat Transaksi Baru

1. Klik tombol **Transaksi Baru** dan pilih template
2. Isi **Tanggal** transaksi
3. Isi **Jumlah** transaksi
4. Pilih **Akun Sumber** (Kas/Bank)
5. Isi **Keterangan** yang jelas
6. Review **Preview Jurnal** untuk memastikan kebenaran
7. Klik **Simpan Draft** atau **Simpan & Posting**

### Melihat Detail Transaksi

Klik pada nomor transaksi di daftar untuk melihat:
- Informasi lengkap transaksi
- Jurnal yang dihasilkan
- Audit trail (riwayat perubahan)

### Membatalkan Transaksi (Void)

1. Buka detail transaksi yang akan dibatalkan
2. Klik tombol **Void**
3. Pilih **Alasan Pembatalan**
4. Masukkan **Catatan** jika perlu
5. Konfirmasi pembatalan
      `,
      pages: ['transactions-list', 'transactions-form-new', 'transactions-detail']
    },
    {
      id: 'journals',
      title: 'Buku Besar',
      content: `
## Buku Besar (General Ledger)

Buku Besar menampilkan mutasi dan saldo setiap akun berdasarkan jurnal yang tercatat.

### Fitur Buku Besar

- **Filter Akun** - Pilih akun tertentu untuk dilihat
- **Filter Periode** - Tentukan rentang tanggal
- **Running Balance** - Saldo berjalan setiap transaksi
- **Ringkasan** - Total debit, kredit, dan saldo akhir

### Cara Menggunakan

1. Pilih **Akun** yang ingin dilihat
2. Tentukan **Periode** (tanggal mulai - tanggal selesai)
3. Klik **Terapkan Filter**
4. Lihat daftar jurnal dan saldo berjalan

### Memahami Kolom

| Kolom | Keterangan |
|-------|------------|
| Tanggal | Tanggal jurnal dicatat |
| No. Jurnal | Nomor referensi jurnal |
| Keterangan | Deskripsi transaksi |
| Debit | Jumlah di sisi debit |
| Kredit | Jumlah di sisi kredit |
| Saldo | Saldo setelah transaksi |
      `,
      pages: ['journals-list', 'journals-detail']
    }
  ]
};

async function generateManual() {
  console.log('Generating user manual...');

  // Ensure dist directory exists
  if (!fs.existsSync(DIST_DIR)) {
    fs.mkdirSync(DIST_DIR, { recursive: true });
  }

  // Copy screenshots to dist
  const distScreenshotsDir = path.join(DIST_DIR, 'screenshots');
  if (!fs.existsSync(distScreenshotsDir)) {
    fs.mkdirSync(distScreenshotsDir, { recursive: true });
  }

  // Load captured pages metadata if exists
  let capturedPages = [];
  const metadataPath = path.join(SCREENSHOTS_DIR, 'metadata.json');
  if (fs.existsSync(metadataPath)) {
    capturedPages = JSON.parse(fs.readFileSync(metadataPath, 'utf-8'));
  }

  // Copy screenshots
  if (fs.existsSync(SCREENSHOTS_DIR)) {
    const files = fs.readdirSync(SCREENSHOTS_DIR);
    for (const file of files) {
      if (file.endsWith('.png')) {
        fs.copyFileSync(
          path.join(SCREENSHOTS_DIR, file),
          path.join(distScreenshotsDir, file)
        );
      }
    }
  }

  // Generate HTML
  const templatePath = path.join(TEMPLATES_DIR, 'manual.ejs');
  const template = fs.readFileSync(templatePath, 'utf-8');

  // Map captured pages to sections
  const sectionsWithPages = manualStructure.sections.map(section => {
    const sectionPages = section.pages.map(pageId => {
      return capturedPages.find(p => p.id === pageId) || { id: pageId, captured: false };
    });
    return { ...section, pageData: sectionPages };
  });

  const html = ejs.render(template, {
    ...manualStructure,
    sections: sectionsWithPages,
    capturedPages
  });

  // Write HTML
  const outputPath = path.join(DIST_DIR, 'index.html');
  fs.writeFileSync(outputPath, html);
  console.log(`Manual generated: ${outputPath}`);

  // Generate individual section pages for better navigation
  for (const section of sectionsWithPages) {
    const sectionHtml = ejs.render(template, {
      ...manualStructure,
      sections: sectionsWithPages,
      capturedPages,
      currentSection: section.id
    });
    const sectionPath = path.join(DIST_DIR, `${section.id}.html`);
    fs.writeFileSync(sectionPath, sectionHtml);
  }

  console.log('User manual generation complete!');
}

// Run if called directly
if (require.main === module) {
  generateManual().catch(console.error);
}

module.exports = { generateManual, manualStructure };
