# Demo Setup

Panduan menyiapkan instance demo Balaka dengan 4 jenis industri, reset otomatis setiap malam.

## Arsitektur Demo

Satu VPS 4GB menjalankan 4 instance demo (satu per industri):

| Instance | Port | Domain | Database | Industri |
|----------|------|--------|----------|----------|
| demo-it | 10001 | demo-it.example.com | demodb_it | IT Service |
| demo-seller | 10002 | demo-seller.example.com | demodb_seller | Online Seller |
| demo-coffee | 10003 | demo-coffee.example.com | demodb_coffee | Coffee Shop |
| demo-campus | 10004 | demo-campus.example.com | demodb_campus | Campus |

## Industry Seed Packs

Setiap industri memiliki paket data awal di `industry-seed/<industri>/seed-data/`:

```
industry-seed/
├── it-service/seed-data/
│   ├── 01_company_config.csv
│   ├── 02_chart_of_accounts.csv
│   ├── 03_salary_components.csv
│   ├── 04_journal_templates.csv
│   ├── 05_journal_template_lines.csv
│   ├── ...
│   ├── 18_transactions.csv
│   ├── 20_journal_entries.csv
│   └── MANIFEST.md
├── online-seller/seed-data/
├── coffee-shop/seed-data/
└── campus/seed-data/
```

Data di-import via `DataImportService` saat pertama kali setup.

## Demo Users

Setiap instance demo memiliki user berikut (di-seed via migrasi atau data import):

| Username | Password | Role | Deskripsi |
|----------|----------|------|-----------|
| `pemilik` | `pemilik123` | OWNER | Pemilik usaha |
| `akuntan` | `akuntan123` | ACCOUNTANT | Akuntan |
| `pembukuan` | `pembukuan123` | STAFF | Staf pembukuan |
| `karyawan` | `karyawan123` | EMPLOYEE | Karyawan biasa |
| `auditor` | `auditor123` | AUDITOR | Auditor read-only |

**Catatan:** Password demo ini hanya untuk instance demo. Di production, gunakan password yang kuat dan unik.

## Demo Mode Banner

Aktifkan demo mode untuk menampilkan banner peringatan:

```properties
# application.properties
app.demo-mode=true
```

Atau via environment variable:

```bash
APP_DEMO_MODE=true
```

Banner ini muncul di setiap halaman, memberitahu pengguna bahwa ini adalah instance demo yang di-reset setiap malam.

## Setup Instance Demo

### 1. Buat Database

```bash
sudo -u postgres psql

CREATE USER demo WITH PASSWORD '<password>';
CREATE DATABASE demodb_it OWNER demo;
CREATE DATABASE demodb_seller OWNER demo;
CREATE DATABASE demodb_coffee OWNER demo;
CREATE DATABASE demodb_campus OWNER demo;
GRANT ALL PRIVILEGES ON DATABASE demodb_it TO demo;
GRANT ALL PRIVILEGES ON DATABASE demodb_seller TO demo;
GRANT ALL PRIVILEGES ON DATABASE demodb_coffee TO demo;
GRANT ALL PRIVILEGES ON DATABASE demodb_campus TO demo;

\q
```

### 2. Buat Direktori

```bash
for industry in it seller coffee campus; do
  sudo mkdir -p /opt/demo-$industry/{documents,backup,themes}
  sudo chown -R akunting:akunting /opt/demo-$industry
  sudo mkdir -p /var/log/demo-$industry
  sudo chown -R akunting:akunting /var/log/demo-$industry
done
```

### 3. Konfigurasi per Instance

```properties
# /opt/demo-it/application.properties
server.port=10001
spring.datasource.url=jdbc:postgresql://localhost:5432/demodb_it?sslmode=require
spring.datasource.username=demo
spring.datasource.password=<password>
app.encryption.key=<key>
app.remember-me.key=<key>
app.demo-mode=true
app.theme.name=balaka
app.theme.footer-text=Balaka Demo - IT Service
app.theme.dir=/opt/demo-it/themes
app.storage.documents.path=/opt/demo-it/documents
```

### 4. Systemd Service

Buat service file untuk setiap instance (lihat contoh di [Multi-Instance](07-multi-instance.md)).

### 5. First Run dan Data Import

Saat pertama dijalankan:

1. Flyway akan membuat schema (V001-V005)
2. Login sebagai admin
3. Buka **Pengaturan > Import Data**
4. Import seed pack sesuai industri

### 6. Simpan Snapshot Database

Setelah data demo lengkap, buat snapshot untuk nightly reset:

```bash
# Dump setiap database demo
for industry in it seller coffee campus; do
  sudo -u postgres pg_dump -Fc demodb_$industry > /opt/demo-snapshots/demodb_$industry.dump
done
```

## Nightly Reset

### Script Reset

```bash
#!/bin/bash
# /opt/demo-snapshots/reset-demo.sh

set -euo pipefail

INDUSTRIES="it seller coffee campus"
SNAPSHOT_DIR="/opt/demo-snapshots"
LOG_FILE="/var/log/demo-reset.log"

echo "$(date '+%Y-%m-%d %H:%M:%S') Starting demo reset" >> "$LOG_FILE"

for industry in $INDUSTRIES; do
  DB="demodb_$industry"
  SERVICE="demo-$industry"
  DUMP="$SNAPSHOT_DIR/$DB.dump"

  echo "$(date '+%Y-%m-%d %H:%M:%S') Resetting $DB" >> "$LOG_FILE"

  # Stop aplikasi
  sudo systemctl stop "$SERVICE"

  # Drop dan recreate database
  sudo -u postgres psql -c "DROP DATABASE IF EXISTS $DB;"
  sudo -u postgres psql -c "CREATE DATABASE $DB OWNER demo;"

  # Restore dari snapshot
  sudo -u postgres pg_restore -d "$DB" "$DUMP"

  # Start aplikasi
  sudo systemctl start "$SERVICE"

  echo "$(date '+%Y-%m-%d %H:%M:%S') $DB reset complete" >> "$LOG_FILE"
done

echo "$(date '+%Y-%m-%d %H:%M:%S') Demo reset complete" >> "$LOG_FILE"
```

Set permission:

```bash
sudo chmod +x /opt/demo-snapshots/reset-demo.sh
```

### Cron Job

Jadwalkan reset setiap malam pukul 02:00 WIB:

```bash
sudo crontab -e
```

Tambahkan:

```
0 2 * * * /opt/demo-snapshots/reset-demo.sh >> /var/log/demo-reset.log 2>&1
```

### Verifikasi Reset

```bash
# Cek log reset
tail -20 /var/log/demo-reset.log

# Cek semua instance berjalan
for industry in it seller coffee campus; do
  echo "=== demo-$industry ==="
  sudo systemctl is-active demo-$industry
done
```

## Playwright Demo Data Loaders

Untuk pengembangan dan testing, data demo juga bisa di-load via Playwright test framework menggunakan `@TestConfiguration` dengan `@Profile("functional")` dan `@PostConstruct`:

```
src/test/java/.../functional/
├── ItServiceTestDataInitializer.java
├── OnlineSellerTestDataInitializer.java
├── CoffeeTestDataInitializer.java
└── CampusTestDataInitializer.java
```

Initializer ini membaca CSV dari `industry-seed/` dan mengimpor data via `DataImportService`. Berguna untuk:
- Mereproduksi state demo di environment test
- Menjalankan automated test terhadap data demo

## Update Snapshot Demo

Jika ada perubahan schema atau data seed:

1. Stop semua instance demo
2. Reset satu instance dan jalankan migrasi baru
3. Import ulang data jika diperlukan
4. Buat snapshot baru:
   ```bash
   sudo -u postgres pg_dump -Fc demodb_it > /opt/demo-snapshots/demodb_it.dump
   ```
5. Start kembali semua instance

## Selanjutnya

Lihat [Troubleshooting](09-troubleshooting.md) untuk panduan menyelesaikan masalah umum.
