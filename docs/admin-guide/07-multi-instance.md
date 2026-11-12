# Multi-Instance

Panduan menjalankan beberapa instance Balaka di satu VPS. Cocok untuk melayani beberapa client pada VPS 4GB.

## Arsitektur

```
Internet
    │
    ▼
  Nginx (port 443)
    ├── client-a.example.com → localhost:10001
    ├── client-b.example.com → localhost:10002
    └── client-c.example.com → localhost:10003
    │
PostgreSQL (port 5432)
    ├── accountingdb_client_a
    ├── accountingdb_client_b
    └── accountingdb_client_c
```

## Kapasitas VPS 4GB

Budget memori untuk 3 instance:

| Komponen | Alokasi | Keterangan |
|----------|---------|------------|
| JVM Instance 1 | 384 MB heap + ~100 MB metaspace | `-Xmx384m` |
| JVM Instance 2 | 384 MB heap + ~100 MB metaspace | `-Xmx384m` |
| JVM Instance 3 | 384 MB heap + ~100 MB metaspace | `-Xmx384m` |
| PostgreSQL | ~256 MB | shared_buffers + koneksi |
| Nginx | ~50 MB | |
| OS/Buffers | ~750 MB | Kernel, page cache |
| **Total** | **~3.7 GB** | |

## Setup Database

Buat database terpisah untuk setiap instance:

```bash
sudo -u postgres psql

CREATE USER client_a WITH PASSWORD '<password_a>';
CREATE DATABASE accountingdb_client_a OWNER client_a;
GRANT ALL PRIVILEGES ON DATABASE accountingdb_client_a TO client_a;

CREATE USER client_b WITH PASSWORD '<password_b>';
CREATE DATABASE accountingdb_client_b OWNER client_b;
GRANT ALL PRIVILEGES ON DATABASE accountingdb_client_b TO client_b;

CREATE USER client_c WITH PASSWORD '<password_c>';
CREATE DATABASE accountingdb_client_c OWNER client_c;
GRANT ALL PRIVILEGES ON DATABASE accountingdb_client_c TO client_c;

\q
```

## Setup Direktori

```bash
# Struktur per instance
sudo mkdir -p /opt/aplikasi-akunting-client-a/{documents,backup,scripts,themes}
sudo mkdir -p /opt/aplikasi-akunting-client-b/{documents,backup,scripts,themes}
sudo mkdir -p /opt/aplikasi-akunting-client-c/{documents,backup,scripts,themes}

# Ownership
sudo chown -R akunting:akunting /opt/aplikasi-akunting-client-*

# Log directories
sudo mkdir -p /var/log/aplikasi-akunting-client-{a,b,c}
sudo chown -R akunting:akunting /var/log/aplikasi-akunting-client-*
```

## Konfigurasi per Instance

Setiap instance memiliki `application.properties` sendiri. Yang berbeda per instance:

```properties
# /opt/aplikasi-akunting-client-a/application.properties
server.port=10001
spring.datasource.url=jdbc:postgresql://localhost:5432/accountingdb_client_a?sslmode=require
spring.datasource.username=client_a
spring.datasource.password=<password_a>
app.encryption.key=<key_a>
app.theme.name=client-a
app.theme.footer-text=Client A
app.theme.dir=/opt/aplikasi-akunting-client-a/themes
app.storage.documents.path=/opt/aplikasi-akunting-client-a/documents
```

Perhatikan bahwa `server.port` harus berbeda untuk setiap instance (10001, 10002, 10003).

## Systemd Service per Instance

Buat service file untuk setiap instance:

```ini
# /etc/systemd/system/aplikasi-akunting-client-a.service
[Unit]
Description=Balaka Accounting - Client A
After=network.target postgresql.service

[Service]
Type=simple
User=akunting
Group=akunting
WorkingDirectory=/opt/aplikasi-akunting-client-a

ExecStart=/usr/bin/java \
  -Xms256m -Xmx384m \
  -XX:MetaspaceSize=96m \
  -XX:MaxMetaspaceSize=128m \
  -Xlog:gc*:file=/var/log/aplikasi-akunting-client-a/gc.log:time,tags:filecount=3,filesize=10m \
  -jar /opt/aplikasi-akunting-client-a/aplikasi-akunting.jar \
  --spring.config.location=file:/opt/aplikasi-akunting-client-a/application.properties

Restart=on-failure
RestartSec=10

StandardOutput=journal
StandardError=journal
SyslogIdentifier=akunting-client-a

[Install]
WantedBy=multi-user.target
```

Ulangi untuk client-b (port 10002) dan client-c (port 10003). Kemudian:

```bash
sudo systemctl daemon-reload
sudo systemctl enable aplikasi-akunting-client-a
sudo systemctl enable aplikasi-akunting-client-b
sudo systemctl enable aplikasi-akunting-client-c

sudo systemctl start aplikasi-akunting-client-a
sudo systemctl start aplikasi-akunting-client-b
sudo systemctl start aplikasi-akunting-client-c
```

## Nginx Vhost per Instance

Buat vhost file terpisah untuk setiap instance:

```nginx
# /etc/nginx/sites-available/client-a
server {
    listen 443 ssl;
    server_name client-a.example.com;

    ssl_certificate /etc/letsencrypt/live/client-a.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/client-a.example.com/privkey.pem;

    # Security headers
    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
    add_header X-Frame-Options DENY always;
    add_header X-Content-Type-Options nosniff always;

    # Rate limiting
    limit_req zone=per_ip burst=20 nodelay;

    location / {
        proxy_pass http://127.0.0.1:10001;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}

server {
    listen 80;
    server_name client-a.example.com;
    return 301 https://$host$request_uri;
}
```

Aktifkan vhost:

```bash
sudo ln -s /etc/nginx/sites-available/client-a /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl reload nginx
```

## Resource Limits (cgroup)

Batasi penggunaan resource per instance menggunakan systemd resource control:

```ini
# Tambahkan di bagian [Service] pada systemd unit file

# Batas memori (hard limit)
MemoryMax=600M
MemoryHigh=500M

# Batas CPU (relatif terhadap instance lain)
CPUWeight=100

# Batas proses
TasksMax=100
```

Setelah mengubah service file:

```bash
sudo systemctl daemon-reload
sudo systemctl restart aplikasi-akunting-client-a
```

Cek penggunaan resource:

```bash
# Lihat resource usage per service
systemctl status aplikasi-akunting-client-a
# Baris "Memory:" menunjukkan penggunaan memori

# Atau via cgroup
cat /sys/fs/cgroup/system.slice/aplikasi-akunting-client-a.service/memory.current
```

## Monitoring per Instance

### Status Semua Instance

```bash
# Cek semua instance sekaligus
for svc in client-a client-b client-c; do
  echo "=== $svc ==="
  sudo systemctl is-active aplikasi-akunting-$svc
  curl -s http://localhost:1000${svc: -1}/actuator/health 2>/dev/null || echo "UNREACHABLE"
done
```

### Log per Instance

```bash
# Live log instance tertentu
sudo journalctl -u aplikasi-akunting-client-a -f

# Semua instance
sudo journalctl -u 'aplikasi-akunting-client-*' -f
```

### Memori per Instance

```bash
# RSS setiap instance
for svc in client-a client-b client-c; do
  PID=$(pgrep -f "aplikasi-akunting-$svc")
  RSS=$(ps -o rss= -p $PID 2>/dev/null)
  echo "$svc: PID=$PID RSS=${RSS}KB"
done
```

## Backup per Instance

Setiap instance memiliki script backup sendiri yang terhubung ke database yang sesuai. Pastikan `backup.conf` dan `.pgpass` di setiap direktori instance sudah dikonfigurasi untuk database yang benar.

## Selanjutnya

Lihat [Demo Setup](08-demo-setup.md) untuk menyiapkan instance demo.
