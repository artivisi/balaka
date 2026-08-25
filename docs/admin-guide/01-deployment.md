# Panduan Deployment

Panduan untuk melakukan deployment Balaka pada VPS berbasis Ubuntu.

## Kebutuhan VPS

| Resource | Minimum | Rekomendasi |
|----------|---------|-------------|
| OS | Ubuntu 22.04 LTS | Ubuntu 24.04 LTS |
| CPU | 1 vCPU | 2 vCPU |
| RAM | 2 GB | 4 GB |
| Disk | 20 GB SSD | 40 GB SSD |
| Provider | Biznet, DigitalOcean, dll | Biznet VPS |

VPS 2GB cukup untuk single instance. VPS 4GB bisa menjalankan hingga 3 instance (lihat [Multi-Instance](07-multi-instance.md)).

## Prasyarat

### 1. Repository Deployment (Privat)

Konfigurasi deployment (Ansible playbook, inventory, credentials) disimpan di repository terpisah yang bersifat privat. Clone repository tersebut terlebih dahulu:

```bash
# Clone deploy repository
git clone <url-repo-deploy> aplikasi-akunting-deploy
cd aplikasi-akunting-deploy
```

Struktur repository deploy:

```
aplikasi-akunting-deploy/
├── site.yml              # Server setup playbook
├── deploy.yml            # Application deployment playbook
├── deploy.sh             # Wrapper script (upload JAR)
├── deploy-remote-build.sh # Wrapper script (build di server)
├── inventory.ini
├── clients/
│   ├── artivisi/
│   │   ├── group_vars/all.yml   # Credentials & config
│   │   └── themes/artivisi/     # Theme files
│   └── tazkia-stmik/
│       ├── group_vars/all.yml
│       └── themes/tazkia/
└── upgrade-postgresql.yml
```

### 2. Konfigurasi Client

Edit `clients/<client>/group_vars/all.yml` untuk mengisi:

- Database credentials
- Domain name
- Admin user credentials
- Encryption key
- Telegram bot token (opsional)
- Theme settings

## Proses Deployment

### Langkah 1: Setup Server

Jalankan `site.yml` untuk menginstall dan mengkonfigurasi:

- Java 25 (Azul Zulu)
- PostgreSQL 18 (dari PGDG repository)
- Nginx dengan SSL (Let's Encrypt)
- Direktori aplikasi
- Systemd service dengan JVM settings
- Script backup dan cron job

```bash
ansible-playbook -i inventory.ini site.yml
```

### Langkah 2: Deploy Aplikasi

Ada dua metode deployment:

#### Metode 1: `deploy.sh` (Upload JAR)

Build JAR di mesin lokal, upload ke server:

```bash
./deploy.sh <client>
```

Proses yang dilakukan:
1. Build JAR lokal (`./mvnw clean package -DskipTests`)
2. Upload JAR ke server via SCP
3. Konfigurasi admin user
4. Restart service
5. Health check

#### Metode 2: `deploy-remote-build.sh` (Build di Server)

Build langsung di server (lebih cepat, tanpa upload JAR):

```bash
./deploy-remote-build.sh <client>
```

Proses yang dilakukan:
1. SSH ke server
2. `git pull` source code
3. Build JAR di server
4. Restart service
5. Health check

**Perhatian:** Jika `git pull` gagal karena history divergence (misalnya setelah force push), perbaiki dulu:

```bash
ssh <user>@<server> 'cd ~/aplikasi-akunting && git reset --hard origin/main'
```

**Perhatian:** Jangan menjalankan `deploy.sh` ke beberapa client secara bersamaan. Perintah `mvn clean` yang paralel akan gagal karena konflik pada direktori `target/`. Deploy secara berurutan.

### Langkah 3: Verifikasi

```bash
# Cek status service
ssh <user>@<server> 'sudo systemctl status aplikasi-akunting'

# Health check
curl -s http://<server>:10000/actuator/health

# Cek halaman login (via Nginx/SSL)
curl -I https://<domain>/login
# Harus mendapat HTTP 200
```

## Struktur Direktori di Server

```
/opt/aplikasi-akunting/
├── aplikasi-akunting.jar        # Aplikasi
├── application.properties       # Konfigurasi
├── documents/                   # Upload dokumen
├── backup/                      # Backup lokal
├── scripts/
│   ├── backup.sh
│   ├── backup-b2.sh
│   ├── backup-gdrive.sh
│   └── restore.sh
├── backup.conf
├── .backup-key                  # Encryption key untuk backup
└── .pgpass                      # PostgreSQL password file

/var/log/aplikasi-akunting/
├── app.log                      # Application log
├── gc.log                       # JVM GC log
├── backup.log                   # Backup log
└── restore.log                  # Restore log
```

## DNS dan SSL

### DNS

Arahkan domain ke IP server:

```
A    balaka.example.com    103.31.204.12
```

### SSL (Let's Encrypt)

SSL dikonfigurasi otomatis oleh Ansible (`site.yml`). Untuk pengelolaan manual:

```bash
# Cek sertifikat
sudo certbot certificates

# Test renewal
sudo certbot renew --dry-run

# Force renewal
sudo certbot renew --force-renewal

# Cek expiry
echo | openssl s_client -servername <domain> \
  -connect <domain>:443 2>/dev/null | \
  openssl x509 -noout -dates
```

Certbot auto-renewal sudah dikonfigurasi via systemd timer. Periksa statusnya:

```bash
sudo systemctl status certbot.timer
```

## Systemd Service

Aplikasi berjalan sebagai systemd service `aplikasi-akunting`:

```bash
# Start/stop/restart
sudo systemctl start aplikasi-akunting
sudo systemctl stop aplikasi-akunting
sudo systemctl restart aplikasi-akunting

# Status
sudo systemctl status aplikasi-akunting

# Logs (live)
sudo journalctl -u aplikasi-akunting -f

# Logs (100 baris terakhir)
sudo journalctl -u aplikasi-akunting -n 100
```

## JVM Configuration

JVM menggunakan G1GC (default Java 25, optimal untuk heap < 4GB):

```bash
-Xms512m -Xmx1024m               # Dynamic heap sizing
-XX:MetaspaceSize=128m
-XX:MaxMetaspaceSize=192m
```

Untuk multi-instance pada VPS 4GB, gunakan heap lebih kecil:

```bash
-Xms256m -Xmx384m
```

GC logging disimpan di `/var/log/aplikasi-akunting/gc.log`.

## Nginx Configuration

| Setting | Value | Keterangan |
|---------|-------|------------|
| worker_processes | auto | Sesuai jumlah CPU core |
| worker_connections | 1024 | Per worker |
| keepalive_timeout | 65s | Connection reuse |
| rate_limit | 10r/s | Per IP, burst 20 |
| gzip | on | Level 5 compression |

Security headers (HSTS, X-Frame-Options, CSP) dikonfigurasi di SSL site template.

```bash
# Test konfigurasi
sudo nginx -t

# Reload
sudo systemctl reload nginx

# Access logs
tail -f /var/log/nginx/access.log
```

## First-Run Health Check

Setelah deployment pertama, pastikan:

1. **Flyway migrations** berhasil dijalankan (V001-V005)
2. **Health endpoint** merespons:
   ```bash
   curl -s http://localhost:10000/actuator/health
   # {"status":"UP"}
   ```
3. **Login page** bisa diakses:
   ```bash
   curl -I https://<domain>/login
   # HTTP/2 200
   ```
4. **Admin user** bisa login dengan credentials yang dikonfigurasi di `group_vars/all.yml`

Jika aplikasi gagal start, periksa log:

```bash
sudo journalctl -u aplikasi-akunting --no-pager | tail -50
```

Penyebab umum:
- Database connection gagal (cek credentials di `application.properties`)
- Flyway migration error (lihat [Troubleshooting](09-troubleshooting.md))
- Port 10000 sudah digunakan proses lain

## Selanjutnya

Setelah deployment berhasil, lanjutkan ke [Konfigurasi](02-configuration.md) untuk mengatur parameter aplikasi.
