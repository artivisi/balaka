# Updates

Prosedur untuk upgrade versi aplikasi Balaka.

## Versi

Menggunakan calendar versioning (CalVer): `YYYY.MM[.PATCH]-RELEASE`

Contoh:
- `2026.03-RELEASE` — Release bulanan
- `2026.03.1-RELEASE` — Patch release pertama
- `2026.03.2-RELEASE` — Patch release kedua

## Persiapan Sebelum Update

### 1. Backup Manual

Selalu lakukan backup sebelum update:

```bash
ssh <user>@<server>
sudo -u akunting /opt/aplikasi-akunting/scripts/backup.sh
```

Verifikasi backup:

```bash
ls -lh /opt/aplikasi-akunting/backup/ | tail -1
```

### 2. Cek Release Notes

Baca release notes untuk versi yang akan di-deploy:

```bash
# Di repository aplikasi
cat docs/releases/<versi>.md
```

Perhatikan bagian:
- **Breaking Changes** — perubahan yang memerlukan tindakan manual
- **Migration Guide** — langkah migrasi yang perlu dilakukan
- **Known Issues** — masalah yang sudah diketahui

## Metode Deployment

### Metode 1: `deploy-remote-build.sh` (Rekomendasi)

Build di server, tanpa upload JAR. Lebih cepat untuk koneksi internet lambat:

```bash
cd aplikasi-akunting-deploy
./deploy-remote-build.sh <client>
```

Proses:
1. SSH ke server
2. `git pull` untuk mengambil source code terbaru
3. `./mvnw clean package -DskipTests` di server
4. Copy JAR ke `/opt/aplikasi-akunting/`
5. Restart service
6. Health check

**Perhatian:** Jika `git pull` gagal (misalnya setelah history rewrite):

```bash
ssh <user>@<server> 'cd ~/aplikasi-akunting && git reset --hard origin/main'
```

### Metode 2: `deploy.sh` (Upload JAR)

Build di lokal, upload JAR ke server:

```bash
cd aplikasi-akunting-deploy
./deploy.sh <client>
```

Proses:
1. Build JAR lokal (`./mvnw clean package -DskipTests`)
2. Upload JAR via SCP
3. Restart service
4. Health check

**Perhatian:** Jangan menjalankan deploy ke beberapa client secara bersamaan. `mvn clean` paralel akan gagal karena konflik pada direktori `target/`. Deploy secara berurutan.

## Flyway Migration Caveats

Balaka menggunakan konsolidasi migrasi V001-V004 yang sudah dijalankan di production, ditambah migrasi baru mulai V005. V001-V004 tidak boleh diubah lagi — skema baru selalu masuk sebagai file migrasi baru. Ini memiliki implikasi saat update:

### Migrasi Baru (Tidak Bermasalah)

Jika versi baru menambah file migrasi baru (misal V005), Flyway akan menjalankannya otomatis saat aplikasi start. Tidak perlu tindakan manual.

### Migrasi yang Dimodifikasi

Jika versi baru memodifikasi migrasi yang sudah dijalankan (misal menambah kolom ke V003), akan terjadi checksum mismatch:

```
Migration checksum mismatch for migration version 003
-> Applied to database : -1535984110
-> Resolved locally    : -1414456606
```

**Perbaikan:**

1. Identifikasi perubahan schema:

```bash
# Diff migrasi antara versi lama dan baru
git diff <old-tag>..<new-tag> -- src/main/resources/db/migration/V003__feature_schema.sql
```

2. Terapkan perubahan manual ke database:

```bash
sudo -u postgres psql -d accountingdb \
  -c "ALTER TABLE <tabel> ADD COLUMN <kolom> <tipe>;"
```

3. Update checksum:

```bash
sudo -u postgres psql -d accountingdb \
  -c "UPDATE flyway_schema_history SET checksum = <checksum_baru> WHERE version = '003';"
```

4. Restart aplikasi:

```bash
sudo systemctl restart aplikasi-akunting
```

## Verifikasi Setelah Update

```bash
# 1. Cek service berjalan
sudo systemctl status aplikasi-akunting

# 2. Health check
curl -s http://localhost:10000/actuator/health

# 3. Cek login page
curl -I https://<domain>/login

# 4. Cek versi (di application log)
sudo journalctl -u aplikasi-akunting | grep "Started" | tail -1

# 5. Login dan verifikasi secara manual
# Buka https://<domain> dan cek fitur-fitur utama
```

## Rollback

### Rollback Aplikasi

Deploy Ansible membuat backup JAR sebelumnya:

```bash
# Cek backup ada
ls -la /opt/aplikasi-akunting/aplikasi-akunting.jar.backup

# Rollback ke versi sebelumnya
mv /opt/aplikasi-akunting/aplikasi-akunting.jar.backup /opt/aplikasi-akunting/aplikasi-akunting.jar
sudo systemctl restart aplikasi-akunting

# Verifikasi
curl -I http://localhost:10000/login
```

### Rollback Database

Jika update melibatkan perubahan schema yang perlu di-rollback:

```bash
sudo /opt/aplikasi-akunting/scripts/restore.sh \
  /opt/aplikasi-akunting/backup/<backup-file>.tar.gz
```

### Rollback Strategi

| Skenario | Tindakan |
|----------|----------|
| Bug di kode, schema tidak berubah | Rollback JAR saja |
| Bug di kode + schema berubah | Restore database + rollback JAR |
| Schema berubah, perlu data baru | Tidak bisa rollback otomatis, perbaiki di versi selanjutnya |

## Checklist Update

- [ ] Backup manual di production
- [ ] Baca release notes
- [ ] Deploy ke server
- [ ] Health check OK
- [ ] Login test
- [ ] Verifikasi fitur utama
- [ ] Monitor log 15 menit setelah deploy

## Selanjutnya

Lihat [Security](06-security.md) untuk konfigurasi keamanan.
