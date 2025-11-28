# Kalkulator PPh 21

## Pendahuluan

Kalkulator PPh 21 membantu menghitung Pajak Penghasilan Pasal 21 bulanan berdasarkan gaji bruto karyawan. Fitur ini berguna untuk:
- Estimasi PPh 21 sebelum proses penggajian
- Memahami komponen perhitungan pajak penghasilan
- Memverifikasi potongan PPh 21 di slip gaji

## Mengakses Kalkulator PPh 21

1. Klik menu **Kalkulator PPh 21** di sidebar
2. Halaman kalkulator akan ditampilkan

## Cara Menggunakan

### Input Data

| Field | Keterangan |
|-------|------------|
| Gaji Bruto Bulanan (Rp) | Gaji pokok + tunjangan + bonus |
| Status PTKP | Penghasilan Tidak Kena Pajak berdasarkan status perkawinan |
| Memiliki NPWP | Centang jika karyawan memiliki NPWP |

### Status PTKP

| Kode | Keterangan | Nilai Tahunan |
|------|------------|---------------|
| TK/0 | Tidak Kawin tanpa tanggungan | Rp 54.000.000 |
| TK/1 | Tidak Kawin 1 tanggungan | Rp 58.500.000 |
| TK/2 | Tidak Kawin 2 tanggungan | Rp 63.000.000 |
| TK/3 | Tidak Kawin 3 tanggungan | Rp 67.500.000 |
| K/0 | Kawin tanpa tanggungan | Rp 58.500.000 |
| K/1 | Kawin 1 tanggungan | Rp 63.000.000 |
| K/2 | Kawin 2 tanggungan | Rp 67.500.000 |
| K/3 | Kawin 3 tanggungan | Rp 72.000.000 |
| K/I/0 | Kawin istri digabung tanpa tanggungan | Rp 112.500.000 |
| K/I/1 | Kawin istri digabung 1 tanggungan | Rp 117.000.000 |
| K/I/2 | Kawin istri digabung 2 tanggungan | Rp 121.500.000 |
| K/I/3 | Kawin istri digabung 3 tanggungan | Rp 126.000.000 |

### Langkah Perhitungan

1. Masukkan **Gaji Bruto Bulanan**
2. Pilih **Status PTKP** sesuai kondisi karyawan
3. Centang **Memiliki NPWP** jika karyawan punya NPWP
4. Klik tombol **Hitung**
5. Hasil perhitungan akan ditampilkan

## Memahami Hasil Perhitungan

### Ringkasan

Empat kartu ringkasan menampilkan:
- **Gaji Bruto**: Penghasilan kotor bulanan
- **PPh 21 Bulanan**: Pajak yang harus dipotong
- **Potongan BPJS**: Iuran BPJS bagian karyawan
- **Take Home Pay**: Gaji bersih yang diterima

### Alur Perhitungan

| Langkah | Komponen | Keterangan |
|---------|----------|------------|
| 1 | Penghasilan Bruto | Gaji + Tunjangan + Bonus |
| 2 | (-) Biaya Jabatan | 5% dari bruto, maks Rp 500.000/bulan |
| 3 | (-) Iuran BPJS | JHT 2% + JP 1% bagian karyawan |
| 4 | = Penghasilan Neto | Bruto - Biaya Jabatan - BPJS |
| 5 | × 12 bulan | Neto Tahunan |
| 6 | (-) PTKP | Sesuai status perkawinan |
| 7 | = PKP | Penghasilan Kena Pajak |
| 8 | × Tarif Progresif | Sesuai lapisan PKP |
| 9 | = PPh 21 Tahunan | Total pajak setahun |
| 10 | ÷ 12 | PPh 21 Bulanan |

### Tarif Pajak Progresif (PP 58/2023)

| PKP Tahunan | Tarif |
|-------------|-------|
| Rp 0 - 60.000.000 | 5% |
| Rp 60.000.001 - 250.000.000 | 15% |
| Rp 250.000.001 - 500.000.000 | 25% |
| Rp 500.000.001 - 5.000.000.000 | 30% |
| > Rp 5.000.000.000 | 35% |

## Contoh Perhitungan

### Gaji Rp 10.000.000 (TK/0, punya NPWP)

| Komponen | Bulanan | Tahunan |
|----------|---------|---------|
| Penghasilan Bruto | Rp 10.000.000 | Rp 120.000.000 |
| Biaya Jabatan | (Rp 500.000) | (Rp 6.000.000) |
| BPJS (JHT+JP) | (Rp 300.000) | (Rp 3.600.000) |
| **Neto** | **Rp 9.200.000** | **Rp 110.400.000** |
| PTKP TK/0 | - | (Rp 54.000.000) |
| **PKP** | - | **Rp 56.400.000** |
| PPh 21 (5%) | - | Rp 2.820.000 |
| **PPh 21 Bulanan** | **Rp 235.000** | - |

### Gaji Rp 20.000.000 (K/2, punya NPWP)

| Komponen | Bulanan | Tahunan |
|----------|---------|---------|
| Penghasilan Bruto | Rp 20.000.000 | Rp 240.000.000 |
| Biaya Jabatan | (Rp 500.000) | (Rp 6.000.000) |
| BPJS (JHT+JP) | (Rp 500.423) | (Rp 6.005.076) |
| **Neto** | **Rp 18.999.577** | **Rp 227.994.924** |
| PTKP K/2 | - | (Rp 67.500.000) |
| **PKP** | - | **Rp 160.494.924** |
| PPh 21 (5%+15%) | - | Rp 18.074.239 |
| **PPh 21 Bulanan** | **Rp 1.506.187** | - |

## Karyawan Tanpa NPWP

Jika karyawan **tidak memiliki NPWP**:
- PPh 21 dikenakan tarif 20% lebih tinggi
- Sistem akan menampilkan peringatan
- Contoh: PPh 21 normal Rp 235.000, tanpa NPWP menjadi Rp 282.000

## Tarif Efektif

Kalkulator juga menampilkan **Tarif Efektif**, yaitu:
- Persentase pajak terhadap penghasilan bruto
- Rumus: (PPh 21 Tahunan / Bruto Tahunan) × 100%
- Berguna untuk perbandingan beban pajak antar karyawan

## Tips Penggunaan

1. **Status PTKP K/I** digunakan jika penghasilan istri digabung dengan suami
2. **Biaya Jabatan** maksimal Rp 6.000.000/tahun atau Rp 500.000/bulan
3. **BPJS JP** memiliki ceiling Rp 10.042.300 (2025)
4. Tanggungan maksimal yang diperhitungkan adalah 3 orang
5. Hasil kalkulator adalah estimasi, slip gaji resmi mungkin berbeda

## Lihat Juga

- [Kelola Karyawan](60-kelola-karyawan.md) - Data karyawan dan status PTKP
- [Komponen Gaji](61-komponen-gaji.md) - Komponen PPh 21 dalam penggajian
- [Kalkulator BPJS](62-kalkulator-bpjs.md) - Perhitungan iuran BPJS
