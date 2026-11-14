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

> **Sejak guard startup aktif, jalur deploy harus memakai playbook Ansible.**
> `deploy.yml` menulis ulang unit systemd dari `roles/app/templates/app.service.j2`
> (termasuk `--spring.profiles.active=production`) lalu `daemon-reload`, dan
> menjalankan migrasi dalam profile `bootstrap` sebelum menukar admin bawaan.
> Skrip yang hanya me-restart service (`deploy-remote-build.sh`, `deploy.sh`)
> memakai unit lama; kalau unit itu belum membawa profile `production`, aplikasi
> sekarang **menolak start**. Guard aktif untuk semua profile kecuali `dev`,
> `test`, `functional`, dan `bootstrap` yang dijalankan dengan
> `--app.bootstrap.exit-after-migrate=true`; profile `bootstrap` tanpa flag itu
> diperlakukan seperti deployment biasa. Jadi restart manual saja tidak cukup.

### Upgrade ke rilis dengan startup guard

Rilis ini mengubah syarat start, jadi server yang sudah berjalan tidak akan hidup
kembali dengan cara lama. Tiga hal yang harus ada sebelum service dinyalakan:

1. **`APP_ENCRYPTION_KEY` wajib.** Nilai Base64 dari 32 byte acak
   (`openssl rand -base64 32`). Tanpa nilai yang valid, aplikasi berhenti sebelum
   context dibuat dan tidak pernah membuka port. Kunci ini mengenkripsi PII di
   database, jadi simpan di vault atau file ber-mode 600, bukan di repo.
2. **`APP_SETUP_ENABLED` wajib eksplisit.** `app.setup.enabled` tidak punya
   default lagi, sehingga instalasi baru tidak bisa diklaim anonim lewat wizard.
   Set `false` untuk server yang sudah punya user, `true` hanya untuk bootstrap
   pertama di instance yang belum publik, lalu kembalikan ke `false`.
3. **Deploy pertama harus menjalankan playbook penuh, bukan `--tags app`.**
   Playbook menulis unit systemd baru, menjalankan migrasi dalam profile
   `bootstrap`, dan mengganti admin bawaan. Tanpa langkah itu
   `DefaultAdminProductionGuard` menolak start karena kredensial admin/admin masih
   ada di database.

Urutan untuk server Ansible:

```bash
ansible-playbook -i inventory deploy/ansible/site.yml
```

Untuk server yang dikelola tanpa Ansible (container murni), lihat bagian
"Menjalankan container tanpa Ansible" di bawah.

### Menjalankan container tanpa Ansible

Image tidak punya jalur kedua untuk membuat admin: satu-satunya cara yang didukung
adalah menjalankan container dua kali, pertama dalam mode bootstrap. Mode itu
menjalankan Flyway, mengganti admin bawaan dengan akun dari environment, lalu
keluar dengan kode 0.

```bash
# 1. Bootstrap: migrasi + ganti admin, lalu keluar
docker run --rm --env-file /etc/balaka/production.env \
  -e APP_BOOTSTRAP_ADMIN_USERNAME=ops \
  -e APP_BOOTSTRAP_ADMIN_PASSWORD='<minimal 12 karakter>' \
  balaka:latest \
  --spring.profiles.active=bootstrap \
  --app.bootstrap.exit-after-migrate=true

# 2. Jalankan normal
docker run -d --name balaka --env-file /etc/balaka/production.env \
  -p 8080:8080 balaka:latest
```

`APP_BOOTSTRAP_ADMIN_USERNAME`/`APP_BOOTSTRAP_ADMIN_PASSWORD` hanya dihormati di
profile `bootstrap`. Kalau variabel itu ikut terbawa ke container produksi,
aplikasi menolak start alih-alih diam-diam menimpa admin. Username `admin` juga
ditolak: tujuannya justru menghapus kredensial bawaan, bukan menamai ulang.
Menjalankan ulang langkah 1 dengan username yang sama akan menyetel ulang password
akun itu, jadi aman diulang.

Contoh `production.env` minimal:

```
APP_ENCRYPTION_KEY=<base64 32 byte>
APP_SETUP_ENABLED=false
APP_STORAGE_DOCUMENTS_PATH=/data/documents
SPRING_PROFILES_ACTIVE=production
SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/balaka
SPRING_DATASOURCE_USERNAME=balaka
SPRING_DATASOURCE_PASSWORD=<rahasia>
```

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
