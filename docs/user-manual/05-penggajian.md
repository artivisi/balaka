# Penggajian

Panduan lengkap pengelolaan payroll, BPJS, dan PPh 21 karyawan.

## Setup Komponen Gaji

### Melihat Komponen Gaji

Buka menu **Penggajian** > **Komponen Gaji**.

![Daftar Komponen Gaji](screenshots/salary-components-list.png)

### Jenis Komponen

| Tipe | Contoh | Pengaruh ke Gaji |
|------|--------|------------------|
| **Pendapatan** | Gaji Pokok, Tunjangan | Menambah |
| **Potongan** | BPJS Karyawan, PPh 21 | Mengurangi |

### Menambah Komponen

1. Klik **Komponen Baru**

![Form Komponen Gaji](screenshots/salary-components-form.png)

2. Isi:
   - Nama komponen
   - Tipe (Pendapatan/Potongan)
   - Basis perhitungan (Fixed/Percentage)
   - Kena pajak (Ya/Tidak)
3. Klik **Simpan**

### Komponen Standar (dari Seed)

**Pendapatan:**
- Gaji Pokok
- Tunjangan Jabatan
- Tunjangan Kehadiran
- Tunjangan Makan
- Tunjangan Transport

**Potongan:**
- BPJS Kesehatan (Karyawan)
- BPJS Ketenagakerjaan JHT (Karyawan)
- BPJS Ketenagakerjaan JP (Karyawan)
- PPh 21

---

## Kelola Karyawan

### Melihat Daftar Karyawan

Buka menu **Penggajian** > **Karyawan**.

![Daftar Karyawan](screenshots/employees-list.png)

### Menambah Karyawan

1. Klik **Karyawan Baru**

![Form Karyawan](screenshots/employees-form.png)

2. Isi data:

**Data Pribadi:**
- NIK (Nomor Induk Karyawan)
- Nama lengkap
- Email, telepon, alamat

**Data Pajak:**
- NPWP
- Status PTKP

**Data Kepegawaian:**
- Jabatan, departemen
- Tanggal bergabung
- Tipe (Tetap/Kontrak)

**Data Bank:**
- Nama bank
- Nomor rekening

**Data BPJS:**
- No. BPJS Kesehatan
- No. BPJS Ketenagakerjaan

3. Klik **Simpan**

### Assign Komponen Gaji ke Karyawan

1. Buka detail karyawan
2. Tab **Komponen Gaji**
3. Klik **Tambah Komponen**
4. Pilih komponen dan isi nilai
5. Klik **Simpan**

---

## BPJS

### Tarif BPJS 2024

**BPJS Kesehatan:**
| Pihak | Tarif | Batas UMR |
|-------|-------|-----------|
| Perusahaan | 4% | Maks 12 juta |
| Karyawan | 1% | Maks 12 juta |

**BPJS Ketenagakerjaan:**
| Program | Perusahaan | Karyawan |
|---------|------------|----------|
| JHT | 3.7% | 2% |
| JKK | 0.24-1.74% | - |
| JKM | 0.3% | - |
| JP | 2% | 1% |

### Kalkulator BPJS

Buka menu **Penggajian** > **Kalkulator BPJS**.

![Kalkulator BPJS](screenshots/bpjs-calculator.png)

1. Masukkan gaji pokok
2. Sistem menghitung:
   - BPJS Kes (perusahaan + karyawan)
   - BPJS TK (JHT, JKK, JKM, JP)
   - Total beban perusahaan
   - Total potongan karyawan

---

## PPh 21 Karyawan

### Metode Perhitungan

Aplikasi menggunakan metode **TER (Tarif Efektif Rata-rata)** sesuai PP 58/2023.

### Kalkulator PPh 21

Buka menu **Penggajian** > **Kalkulator PPh 21**.

![Kalkulator PPh 21](screenshots/pph21-calculator.png)

1. Masukkan:
   - Gaji bruto bulanan
   - Status PTKP
   - Tunjangan-tunjangan
2. Sistem menghitung:
   - Penghasilan bruto
   - Biaya jabatan (5%, maks 500rb)
   - BPJS yang dibayar karyawan
   - Penghasilan neto
   - PKP (Penghasilan Kena Pajak)
   - PPh 21 terutang

### Referensi (lihat [Perpajakan](04-perpajakan.md))

- Tarif PPh 21 progresif
- PTKP per status

---

## Proses Penggajian

### Melihat Daftar Payroll

Buka menu **Penggajian** > **Payroll**.

![Daftar Payroll](screenshots/payroll-list.png)

### Membuat Payroll Baru

1. Klik **Payroll Baru**

![Form Payroll](screenshots/payroll-form.png)

2. Isi:
   - Periode (bulan/tahun)
   - Tanggal pembayaran
3. Klik **Buat**
4. Sistem generate slip gaji untuk semua karyawan aktif

### Workflow Payroll

```
DRAFT → CALCULATED → APPROVED → POSTED
```

| Status | Aksi |
|--------|------|
| DRAFT | Edit komponen individual |
| CALCULATED | Review perhitungan |
| APPROVED | Siap bayar |
| POSTED | Jurnal gaji dibuat |

### Melihat Detail Payroll

Klik payroll untuk melihat detail:

![Detail Payroll](screenshots/payroll-detail.png)

**Informasi per karyawan:**
- Gaji pokok
- Tunjangan (jabatan, kehadiran, makan, transport)
- Total pendapatan (bruto)
- BPJS Karyawan
- PPh 21
- Total potongan
- Gaji bersih (take home pay)

### Posting Payroll

1. Pastikan status APPROVED
2. Klik **Posting**
3. Sistem membuat jurnal:
   ```
   Dr. Beban Gaji              xxx
   Dr. Beban BPJS Perusahaan   xxx
       Cr. Hutang Gaji             xxx
       Cr. Hutang BPJS             xxx
       Cr. Hutang PPh 21           xxx
   ```

### Membayar Gaji

Setelah transfer ke rekening karyawan:

1. Buka menu **Transaksi** > **Transaksi Baru**
2. Pilih template **Bayar Gaji**
3. Isi jumlah total gaji bersih
4. Posting

Jurnal:
```
Dr. Hutang Gaji             xxx
    Cr. Bank                    xxx
```

---

## Layanan Mandiri Karyawan

### Fitur Self-Service

Karyawan dengan role EMPLOYEE dapat mengakses:

**Slip Gaji:**

![Self Service Payslips](screenshots/self-service-payslips.png)

**Bukti Potong PPh 21:**

![Self Service Bukti Potong](screenshots/self-service-bukti-potong.png)

**Profil:**

![Self Service Profile](screenshots/self-service-profile.png)

### Mengaktifkan Self-Service

1. Buat user untuk karyawan dengan role EMPLOYEE
2. Link user ke data karyawan
3. Karyawan login dengan kredensialnya

---

## Bukti Potong PPh 21

### Generate Bukti Potong Tahunan (1721-A1)

1. Buka menu **Penggajian** > **Bukti Potong**
2. Pilih tahun pajak
3. Pilih karyawan (atau semua)
4. Klik **Generate**
5. Download PDF

### Isi Bukti Potong

- Identitas pemotong (perusahaan)
- Identitas penerima (karyawan)
- Rincian penghasilan bruto
- BPJS dan biaya jabatan
- Penghasilan neto
- PTKP
- PKP
- PPh 21 terutang
- PPh 21 dipotong

---

## Tips Penggajian

1. **Setup komponen dulu** - Sebelum input karyawan
2. **Verifikasi BPJS** - Pastikan nomor BPJS valid
3. **Review sebelum posting** - Cek perhitungan PPh 21
4. **Backup sebelum posting** - Jurnal tidak bisa di-reverse
5. **Arsip bukti potong** - Simpan PDF untuk audit

---

## Lihat Juga

- [Perpajakan](04-perpajakan.md) - Tarif PPh 21, PTKP
- [Tutorial Akuntansi](02-tutorial-akuntansi.md) - Jurnal gaji
- [Keamanan](11-keamanan-kepatuhan.md) - Enkripsi data karyawan
