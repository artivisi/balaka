# Security

Konfigurasi keamanan aplikasi Balaka — RBAC, enkripsi, headers, dan audit.

## Role-Based Access Control (RBAC)

Balaka menggunakan 6 role dengan permission yang bersifat aditif (user bisa memiliki lebih dari satu role):

### Daftar Role

| Role | Display Name | Deskripsi |
|------|-------------|-----------|
| `ADMIN` | Administrator | Akses penuh termasuk manajemen user |
| `OWNER` | Pemilik | Semua fitur bisnis, tanpa manajemen user |
| `ACCOUNTANT` | Akuntan | Operasi akuntansi dan laporan |
| `STAFF` | Staf | Operasi harian terbatas (view, buat draft) |
| `AUDITOR` | Auditor | Read-only ke semua laporan |
| `EMPLOYEE` | Karyawan | Akses slip gaji dan profil sendiri saja |

### Perbandingan Permission per Role

| Fitur | ADMIN | OWNER | ACCOUNTANT | STAFF | AUDITOR | EMPLOYEE |
|-------|-------|-------|------------|-------|---------|----------|
| Dashboard | view | view | view | view | view | - |
| Transaksi | full | full | CRUD + post/void | view + create | view | - |
| Jurnal | full | full | full | view | view | - |
| Laporan | full + export | full + export | full + export | view | view + export | - |
| Laporan Pajak | view + export | view + export | view + export | - | view + export | - |
| COA | CRUD | CRUD | view | view | view | - |
| Template | CRUD | CRUD | CRU | view | view | - |
| Client/Vendor | CRUD | CRUD | CRU | view | view | - |
| Invoice/Bill | full | full | full | view | view | - |
| Payroll | full | full | full (tanpa cancel) | - | view + export | - |
| Asset | full | full | full (tanpa delete) | view | view | - |
| Inventory | full | full | full | view | view | - |
| Bank Recon | full + config | full + config | full (tanpa config) | view | view | - |
| Import Data | yes | yes | - | - | - | - |
| Settings | full | full | view + Telegram | view + Telegram | - | - |
| User Management | full | - | - | - | - | - |
| Audit Log | view | view | - | - | view | - |
| Data Subject Rights | full | - | - | - | - | - |
| Profil Sendiri | full | full | full | full | view | full |
| Slip Gaji Sendiri | view | view | view | view | - | view |

### Default Admin User

Migrasi V001 membuat admin default yang di-replace oleh Ansible saat deployment ke production. Credentials dikonfigurasi di `clients/<client>/group_vars/all.yml`.

## Enkripsi Field PII

Data PII (Personally Identifiable Information) dienkripsi di level field menggunakan AES-256-GCM:

```properties
app.encryption.key=${APP_ENCRYPTION_KEY:}
```

Generate key:

```bash
openssl rand -base64 32
```

Field yang dienkripsi termasuk data sensitif karyawan (NIK, NPWP, nomor rekening, dll).

**Kritis:**
- Simpan encryption key di tempat terpisah dari database
- Jika key hilang, data PII yang terenkripsi tidak bisa dipulihkan
- Jangan pernah mengubah key setelah ada data terenkripsi (data lama tidak akan bisa didekripsi dengan key baru)

## Verifikasi Dua Langkah (TOTP)

Setiap pengguna bisa mengaktifkan TOTP dari menu **Verifikasi Dua Langkah**.
Aktivasi bersifat opsional per pengguna, tetapi peran yang tercantum di
`app.mfa.required-roles` wajib memakainya sebelum bisa memakai aplikasi.

| Property | Default | Keterangan |
|----------|---------|------------|
| `app.mfa.enabled` | `true` | Saklar seluruh fitur; `false` untuk suite uji fungsional |
| `app.mfa.required-roles` | `ADMIN,OWNER` | Peran yang wajib MFA; nama peran yang salah menggagalkan startup |
| `app.mfa.max-attempts` | `5` | Percobaan `/mfa-verify` sebelum dikunci |
| `app.mfa.attempt-window-seconds` | `900` | Jendela hitung percobaan |

Detail implementasi:

- Algoritma HMAC-SHA1, 6 digit, periode 30 detik, toleransi satu langkah untuk
  selisih jam. Google Authenticator dan aplikasi lain kompatibel.
- Nilai di atas bisa dioverride deployment lewat
  `--app.mfa.required-roles=ADMIN,OWNER,ACCOUNTANT` atau
  `SPRING_APPLICATION_JSON`; tidak ada nilai cadangan di kode.
- Rahasia dibuat di server saat halaman setup dibuka dan disimpan di sesi sampai
  kode konfirmasi diterima; parameter `secret` dari form tidak pernah dipakai.
  Rahasia disimpan terenkripsi memakai `EncryptedStringConverter` (AES-256-GCM
  dengan `APP_ENCRYPTION_KEY`); tanpa kunci itu aktivasi MFA ditolak.
- Pengguna yang sudah aktif tidak bisa mendaftarkan ulang authenticator dari sesi
  lain: harus menonaktifkan dengan kode OTP atau minta administrator mereset.
- Setiap kode hanya diterima sekali: langkah waktu terakhir disimpan dan kode
  pada langkah yang sama atau sebelumnya ditolak.
- Sepuluh kode pemulihan dibuat saat aktivasi dan saat regenerasi; hanya hash
  SHA-256 yang disimpan dan setiap kode berlaku sekali.
- Administrator dapat mereset MFA pengguna (tombol **Reset MFA** di halaman
  pengguna) ketika authenticator hilang.
- Penghitung percobaan disimpan di memori proses. Restart aplikasi mengosongkan
  kuncian, dan pada deployment multi-instance hitungan tidak dibagi antar
  instance; kalau itu penting, pakai penyimpanan bersama sebelum menaikkan
  jumlah instance.
- Reset administrator dan penonaktifan oleh pengguna tidak memutus sesi yang
  sudah lolos verifikasi: sesi itu tetap berlaku sampai berakhir sendiri.
  Setelah reset, minta pengguna keluar dari semua perangkat bila itu penting;
  daftar sesi per pengguna belum disediakan.
- Semua peristiwa (`MFA_ENABLED`, `MFA_DISABLED`, `MFA_VERIFY_SUCCESS`,
  `MFA_VERIFY_FAILED`, `MFA_LOCKED`, `MFA_RECOVERY_USED`, `MFA_ADMIN_RESET`)
  tercatat di audit log keamanan.

## Content Security Policy (CSP)

Balaka mengimplementasikan CSP strict dengan dynamic nonce:

```
Content-Security-Policy:
  default-src 'self';
  script-src 'self' 'nonce-<random>';
  style-src 'self' 'nonce-<random>';
  img-src 'self' data:;
  font-src 'self';
  frame-ancestors 'none';
  form-action 'self';
```

Nonce di-generate per request oleh `CspNonceFilter` dan disematkan di setiap tag `<script>` dan `<style>`. Tidak menggunakan `unsafe-inline` atau `unsafe-eval`.

Semua komponen Alpine.js menggunakan CSP build — didaftarkan via `Alpine.data()` di `alpine-components.js`, tanpa ekspresi inline di template HTML.

## Security Headers

| Header | Value | Keterangan |
|--------|-------|------------|
| `Content-Security-Policy` | Dynamic nonce | Mencegah XSS |
| `X-Frame-Options` | `DENY` | Mencegah clickjacking |
| `X-Content-Type-Options` | `nosniff` | Mencegah MIME sniffing |
| `Strict-Transport-Security` | `max-age=31536000; includeSubDomains` | Paksa HTTPS (1 tahun) |
| `Referrer-Policy` | `strict-origin-when-cross-origin` | Batasi Referer header |
| `Permissions-Policy` | `geolocation=(), microphone=(), camera=(), payment=()` | Nonaktifkan fitur browser yang tidak diperlukan |

## CSRF Protection

CSRF protection aktif untuk semua form-based request. Dikecualikan hanya untuk:

- `/api/**` — menggunakan Bearer token authentication
- Telegram webhook — menggunakan secret token

Konfigurasi di `SecurityConfig.java`:

```java
.csrf(csrf -> csrf
    .ignoringRequestMatchers("/*/api/**", "/api/**")
)
```

## Session Management

| Parameter | Value | Keterangan |
|-----------|-------|------------|
| Session Timeout | 15 menit | Configurable via `server.servlet.session.timeout` |
| Cookie HttpOnly | `true` | Cookie tidak bisa diakses JavaScript |
| Cookie SameSite | `strict` | Cookie hanya dikirim untuk same-site request |
| Remember Me | Tidak tersedia | Setiap login baru wajib memasukkan password; sesi berakhir setelah 15 menit tidak aktif |

## Password Security

- Hashing: BCrypt (10 rounds)
- Account lockout: Ditangani oleh `AuthenticationEventListener`
- Password strength: Enforced di UI

## API Security

- Bearer token authentication untuk semua `/api/**` endpoint
- OAuth 2.0 device authorization flow untuk token acquisition
- Token management UI tersedia untuk user
- 401 Unauthorized dikembalikan untuk request API tanpa token (bukan redirect ke login)

## SpotBugs / OWASP

Analisis keamanan statis:

```bash
# Jalankan SpotBugs
./mvnw spotbugs:check

# Hasil: target/spotbugsXml.xml
```

Target: 0 issue. Exclusion di `spotbugs-exclude.xml` harus memiliki justifikasi lengkap.

### DAST (Dynamic Application Security Testing)

```bash
# Full DAST scan
./mvnw test -Dtest=ZapDastTest

# Quick mode (passive scan saja, ~1 menit)
./mvnw test -Dtest=ZapDastTest -Ddast.quick=true

# Hasil: target/security-reports/zap-*.html
```

## Audit Logging

Aktivitas user dicatat di audit log:
- Login/logout
- Perubahan data
- Akses laporan sensitif

Lihat audit log di menu **Pengaturan > Audit Log** (memerlukan permission `AUDIT_LOG_VIEW`).

## Data Subject Rights (UU PDP)

Untuk kepatuhan UU Perlindungan Data Pribadi:

| Permission | Fungsi |
|-----------|--------|
| `DATA_SUBJECT_VIEW` | Lihat data pribadi user |
| `DATA_SUBJECT_EXPORT` | Export data pribadi |
| `DATA_SUBJECT_ANONYMIZE` | Anonimisasi data |

Hanya role ADMIN yang memiliki permission ini.

## Rekomendasi Hardening Server

1. **Firewall:** Hanya buka port 22 (SSH), 80 (HTTP redirect), 443 (HTTPS)
2. **SSH:** Disable password authentication, gunakan key-based auth
3. **Fail2ban:** Install untuk mencegah brute force SSH
4. **Automatic updates:** Enable unattended-upgrades untuk security patches
5. **File permissions:** Pastikan `application.properties` hanya readable oleh user `akunting`

```bash
# Firewall
sudo ufw allow 22/tcp
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw enable

# SSH key-only auth
sudo sed -i 's/#PasswordAuthentication yes/PasswordAuthentication no/' /etc/ssh/sshd_config
sudo systemctl restart sshd

# Fail2ban
sudo apt install fail2ban
sudo systemctl enable fail2ban

# File permissions
sudo chmod 600 /opt/aplikasi-akunting/application.properties
sudo chown akunting:akunting /opt/aplikasi-akunting/application.properties
```

## Selanjutnya

Lihat [Multi-Instance](07-multi-instance.md) untuk menjalankan beberapa instance di satu server.
