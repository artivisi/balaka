# Konfigurasi

Referensi lengkap konfigurasi aplikasi Balaka. File konfigurasi utama: `/opt/aplikasi-akunting/application.properties`.

## Database

| Property | Environment Variable | Keterangan |
|----------|---------------------|------------|
| `spring.datasource.url` | `DATABASE_URL` | JDBC URL PostgreSQL dengan `sslmode=require` |
| `spring.datasource.username` | `DATABASE_USERNAME` | Username database |
| `spring.datasource.password` | `DATABASE_PASSWORD` | Password database |

Contoh:

```properties
spring.datasource.url=${DATABASE_URL:jdbc:postgresql://localhost:5432/accountingdb?sslmode=require}
spring.datasource.username=${DATABASE_USERNAME:akunting}
spring.datasource.password=${DATABASE_PASSWORD:}
```

SSL mode `require` memastikan koneksi database terenkripsi. Tidak ada fallback ke koneksi plaintext.

## Server

| Property | Default | Keterangan |
|----------|---------|------------|
| `server.port` | `10000` | Port HTTP aplikasi |
| `server.servlet.session.timeout` | `15m` | Session timeout |
| `server.servlet.session.cookie.http-only` | `true` | Cookie tidak bisa diakses JavaScript |
| `server.servlet.session.cookie.same-site` | `strict` | Cookie hanya dikirim untuk same-site request |

## Enkripsi

| Property | Environment Variable | Keterangan |
|----------|---------------------|------------|
| `app.encryption.key` | `APP_ENCRYPTION_KEY` | Kunci enkripsi AES-256-GCM untuk field PII |

Generate encryption key:

```bash
openssl rand -base64 32
```

**Kritis:** Simpan key ini di tempat aman. Jika hilang, data PII yang terenkripsi tidak bisa didekripsi.

## Telegram Bot

| Property | Environment Variable | Keterangan |
|----------|---------------------|------------|
| `telegram.bot.enabled` | `TELEGRAM_BOT_ENABLED` | Aktifkan bot Telegram (`true`/`false`) |
| `telegram.bot.token` | `TELEGRAM_BOT_TOKEN` | Bot token dari @BotFather |
| `telegram.bot.username` | `TELEGRAM_BOT_USERNAME` | Username bot |
| `telegram.bot.webhook.url` | `TELEGRAM_WEBHOOK_URL` | URL webhook untuk menerima update |
| `telegram.bot.webhook.secret-token` | `TELEGRAM_WEBHOOK_SECRET` | Secret token untuk validasi webhook |

## Google Cloud Vision (OCR)

| Property | Environment Variable | Keterangan |
|----------|---------------------|------------|
| `google.cloud.vision.enabled` | `GOOGLE_CLOUD_VISION_ENABLED` | Aktifkan OCR (`true`/`false`) |
| `google.cloud.vision.credentials-path` | `GOOGLE_APPLICATION_CREDENTIALS` | Path ke service account JSON |

## Theme

| Property | Default | Keterangan |
|----------|---------|------------|
| `app.theme.name` | `balaka` | Nama theme |
| `app.theme.footer-text` | `Balaka` | Teks footer |
| `app.theme.dir` | `data/themes` | Direktori theme assets |

Theme assets disajikan dari filesystem eksternal (`app.theme.dir`). Jika tidak ditemukan di filesystem, akan dicari di classpath. File theme:

```
<theme-dir>/<theme-name>/
├── logo.svg          # Logo untuk background terang
├── logo-dark.svg     # Logo untuk background gelap (sidebar)
└── theme.css         # Custom CSS
```

**Perhatian:** `logo-dark.svg` harus menggunakan fill warna terang (putih/light). Jangan menggunakan logo biasa di sidebar gelap karena tidak akan terlihat.

## Demo Mode

| Property | Environment Variable | Default | Keterangan |
|----------|---------------------|---------|------------|
| `app.demo-mode` | `APP_DEMO_MODE` | `false` | Tampilkan banner reset di setiap halaman |

Saat `true`, setiap halaman menampilkan banner peringatan bahwa ini adalah instance demo yang di-reset setiap malam. Lihat [Demo Setup](08-demo-setup.md).

## Payroll

| Property | Default | Keterangan |
|----------|---------|------------|
| `app.payroll.template-id` | `e0000000-...000014` | UUID template jurnal untuk posting payroll |
| `app.payroll.schedule-cron` | `0 30 6 * * *` | Jadwal cron pengecekan payroll (06:30 WIB) |

## Document Storage

| Property | Default | Keterangan |
|----------|---------|------------|
| `app.storage.documents.path` | wajib, tanpa default (`APP_DOCUMENT_STORAGE_PATH`) | Direktori penyimpanan dokumen upload. Aplikasi menolak start bila tidak diisi |
| `app.storage.documents.max-file-size` | `10485760` | Ukuran maksimal file (10 MB) |
| `app.storage.documents.allowed-types` | `image/jpeg,image/png,image/gif,application/pdf` | MIME type yang diizinkan |

Deployment Ansible mengisi nilai ini di `application.properties` miliknya. Image Docker
mengisi `APP_DOCUMENT_STORAGE_PATH=/opt/app/documents`. Deployment lain dan instance
lokal wajib mengisi environment variable ini:

```bash
APP_DOCUMENT_STORAGE_PATH=/var/lib/balaka/documents
```

## Reverse Proxy

`server.forward-headers-strategy=native` aktif secara default, sehingga redirect
HTTPS dan URL absolut mengikuti header `X-Forwarded-*` dari reverse proxy atau
Cloudflare tunnel. Tanpa ini aplikasi membangun URL `http://` dan redirect ke
halaman login berakhir di skema yang salah.

`native` memakai `RemoteIpValve` milik Tomcat yang hanya memercayai
`X-Forwarded-*` dari peer internal atau loopback. Jangan diganti ke `framework`:
`ForwardedHeaderFilter` memercayai header dari peer mana pun, sehingga klien yang
bisa menjangkau port aplikasi (10000) langsung dapat memalsukan IP untuk rate
limiter dan audit log, serta skema URL. Template Ansible
(`deploy/ansible/roles/app/templates/application.properties.j2`) memakai nilai
yang sama.

## OpenAPI / Swagger

| Property | Default | Keterangan |
|----------|---------|------------|
| `springdoc.packages-to-scan` | `com.artivisi.accountingfinance.controller.api` | Package yang di-scan |
| `springdoc.paths-to-match` | `/api/**` | Path pattern untuk API docs |
| `springdoc.swagger-ui.path` | `/swagger-ui.html` | URL Swagger UI |

## Transaction API

| Property | Default | Keterangan |
|----------|---------|------------|
| `transaction.api.enabled` | `true` | Aktifkan Transaction API |
| `transaction.api.require-auth` | `false` | Wajibkan autentikasi untuk API |

## JVM Heap Settings

Konfigurasi JVM ada di systemd service file. Untuk single instance pada VPS 2GB:

```bash
-Xms512m -Xmx1024m
-XX:MetaspaceSize=128m
-XX:MaxMetaspaceSize=192m
```

Untuk multi-instance pada VPS 4GB (3 instance):

```bash
-Xms256m -Xmx384m
-XX:MetaspaceSize=96m
-XX:MaxMetaspaceSize=128m
```

Budget memori VPS 2GB:

| Komponen | Alokasi | Keterangan |
|----------|---------|------------|
| JVM Heap | 512-1024 MB | Dynamic sizing |
| JVM Metaspace | 128-192 MB | Class metadata |
| PostgreSQL | ~256 MB | shared_buffers + koneksi |
| OS/Buffers | ~512 MB | Page cache, kernel |

## Error Handling

| Property | Default | Keterangan |
|----------|---------|------------|
| `server.error.include-message` | `never` | Sembunyikan pesan error |
| `server.error.include-stacktrace` | `never` | Sembunyikan stack trace |
| `server.error.include-exception` | `false` | Sembunyikan nama exception |
| `server.error.include-binding-errors` | `never` | Sembunyikan validation errors |

Konfigurasi ini memastikan informasi sensitif tidak ditampilkan ke end user.

## Contoh application.properties Lengkap (Production)

```properties
spring.application.name=accounting-finance
server.port=10000

# Database
spring.datasource.url=jdbc:postgresql://localhost:5432/accountingdb?sslmode=require
spring.datasource.username=akunting
spring.datasource.password=<password>

# JPA
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=false

# Timezone
spring.jackson.time-zone=Asia/Jakarta

# Thymeleaf
spring.thymeleaf.cache=true

# Encryption
app.encryption.key=<base64-encoded-32-byte-key>

# Session
server.servlet.session.timeout=15m
server.servlet.session.cookie.http-only=true
server.servlet.session.cookie.same-site=strict

# Theme
app.theme.name=balaka
app.theme.footer-text=Balaka
app.theme.dir=/opt/aplikasi-akunting/themes

# Document storage
app.storage.documents.path=/opt/aplikasi-akunting/documents

# Error handling
server.error.include-message=never
server.error.include-stacktrace=never
server.error.include-exception=false
server.error.include-binding-errors=never
```

## Selanjutnya

Lihat [Database](03-database.md) untuk konfigurasi PostgreSQL dan prosedur backup.
