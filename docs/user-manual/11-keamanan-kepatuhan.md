# Keamanan & Kepatuhan Data

Panduan fitur keamanan dan kepatuhan regulasi (GDPR, UU PDP).

## Enkripsi Dokumen & PII

### Field-Level Encryption

Aplikasi mengenkripsi data sensitif (PII) menggunakan AES-256-GCM:

**Data yang dienkripsi:**
- NPWP karyawan
- Nomor rekening bank
- Alamat
- Nomor BPJS
- Nomor telepon

### Document Storage Encryption

Dokumen yang diupload (struk, invoice, bukti potong) dienkripsi:
- Encryption at rest
- Unique key per document
- Backward compatible dengan dokumen lama

### Implikasi untuk User

- Data terenkripsi tidak bisa di-query langsung di database
- Export data tetap menampilkan data asli (untuk authorized user)
- Backup database tetap aman (data terenkripsi)

---

## Audit Log Keamanan

### Apa yang Di-log?

| Aktivitas | Contoh |
|-----------|--------|
| **Login** | User login/logout, failed attempts |
| **Data Access** | View data sensitif |
| **Data Modification** | Create, Update, Delete |
| **Export** | Download report, export data |
| **Security Events** | Password change, role change |

### Melihat Audit Log

Buka menu **Pengaturan** > **Audit Log**.

![Audit Log](screenshots/settings-audit-logs.png)

**Filter yang tersedia:**
- Tanggal (dari-sampai)
- User
- Tipe aktivitas
- Entity yang diakses

### Retention Policy

- Audit log disimpan minimal 2 tahun
- Log tidak bisa dihapus oleh user biasa
- Hanya ADMIN yang bisa mengakses audit log

---

## Kebijakan Data (GDPR / UU PDP)

### Prinsip yang Diimplementasikan

| Prinsip | Implementasi |
|---------|--------------|
| **Lawfulness** | Consent management |
| **Purpose limitation** | Data hanya untuk tujuan yang dinyatakan |
| **Data minimization** | Hanya kumpulkan data yang diperlukan |
| **Accuracy** | User bisa update data sendiri |
| **Storage limitation** | Retention policy |
| **Integrity** | Encryption, audit log |
| **Accountability** | Audit trail |

### Data Subject Management

Buka menu **Pengaturan** > **Data Subjects**.

![Data Subjects](screenshots/settings-data-subjects.png)

**Fitur:**
- Register data subject (karyawan, klien)
- Track consent
- Manage data processing activities

### Privacy Settings

Buka menu **Pengaturan** > **Privacy**.

![Privacy Settings](screenshots/settings-privacy.png)

**Konfigurasi:**
- Data retention period
- Consent requirements
- Data masking rules

---

## Ekspor Data Subjek (DSAR)

### Apa itu DSAR?

**Data Subject Access Request** - Hak subjek data untuk mendapatkan salinan data pribadinya.

### Proses DSAR

1. Terima request dari data subject
2. Verifikasi identitas
3. Generate export:
   - Data pribadi
   - History transaksi terkait
   - Log aktivitas
4. Deliver dalam format portable (JSON/PDF)

### Batas Waktu

- GDPR: 30 hari
- UU PDP: 3×24 jam

### Cara Generate Export

1. Buka **Pengaturan** > **Data Subjects**
2. Cari data subject
3. Klik **Export Data**
4. Sistem generate file
5. Download dan deliver ke subject

---

## Keamanan Password

### Kebijakan Password

| Requirement | Value |
|-------------|-------|
| Minimum length | 12 karakter |
| Uppercase | Minimal 1 |
| Lowercase | Minimal 1 |
| Number | Minimal 1 |
| Special char | Minimal 1 |

### Account Lockout

| Parameter | Value |
|-----------|-------|
| Max failed attempts | 5 |
| Lockout duration | 30 menit |
| Reset after | Successful login |

### Password Reset

1. User request reset via "Lupa Password"
2. Sistem kirim link ke email
3. Link valid 1 jam
4. User set password baru

---

## Rate Limiting

### Login Endpoint

| Parameter | Value |
|-----------|-------|
| Max requests | 10 per menit |
| Block duration | 5 menit |

### API Endpoints

| Parameter | Value |
|-----------|-------|
| Max requests | 100 per menit |
| Block duration | 1 menit |

---

## Data Anonymization

### Kapan Digunakan?

- Data subject request deletion
- Retention period expired
- Test/development environment

### Proses

1. Identifikasi data subject
2. Anonymize PII fields:
   - Nama → "Anonymized User #123"
   - NPWP → "XX.XXX.XXX.X-XXX.XXX"
   - Alamat → "Anonymized Address"
3. Retain non-PII untuk reporting

### Data yang TIDAK di-anonymize

- Transaction amounts
- Account codes
- Dates
- Aggregated reports

---

## Breach Response

### Prosedur Breach

1. **Detect** - Sistem mendeteksi anomali
2. **Contain** - Isolasi sistem terdampak
3. **Assess** - Evaluasi scope dan impact
4. **Notify** - Lapor ke:
   - Regulator (72 jam untuk GDPR)
   - Data subjects yang terdampak
5. **Remediate** - Perbaiki vulnerability
6. **Document** - Catat seluruh proses

### Monitoring

Sistem memonitor:
- Unusual login patterns
- Bulk data access
- Failed authentication spikes
- API abuse

---

## Compliance Checklist

### GDPR

- [x] Lawful basis for processing
- [x] Privacy notice
- [x] Consent management
- [x] Data subject rights (access, rectification, erasure)
- [x] Data breach notification procedure
- [x] Encryption at rest
- [x] Audit logging

### UU PDP (Indonesia)

- [x] Persetujuan pemrosesan
- [x] Hak akses data pribadi
- [x] Hak koreksi
- [x] Hak penghapusan
- [x] Keamanan data
- [ ] Consent management UI (pending)
- [ ] DPO appointment (organizational)

---

## Tips Keamanan

1. **Strong passwords** - Gunakan password manager
2. **Regular review** - Audit log review mingguan
3. **Principle of least privilege** - Berikan akses minimal
4. **Update software** - Patch security updates
5. **Backup encrypted** - Backup database yang sudah terenkripsi
6. **Train users** - Edukasi tentang phishing

---

## Lihat Juga

- [Setup Awal](01-setup-awal.md) - User management
- [Penggajian](05-penggajian.md) - Data PII karyawan
- [Tutorial Akuntansi](02-tutorial-akuntansi.md) - Audit trail transaksi
