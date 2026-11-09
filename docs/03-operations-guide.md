# Operations Guide

Complete guide for deploying, releasing, and operating the accounting application.

## Quick Start

Deployment configuration (Ansible playbooks, inventory, credentials) lives in a **separate private repository**. See that repository's README for setup instructions.

```bash
# 1. Clone the deploy repository (private)
# 2. Configure client-specific vars in clients/<client>/group_vars/all.yml
# 3. Server setup (one-time)
ansible-playbook -i inventory.ini site.yml
# 4. Deploy application
ansible-playbook -i inventory.ini deploy.yml
```

## Prerequisites

### VPS Requirements

| Resource | Minimum | Recommended |
|----------|---------|-------------|
| OS | Ubuntu 22.04 LTS | Ubuntu 24.04 LTS |
| CPU | 1 vCPU | 2 vCPU |
| RAM | 2 GB | 4 GB |
| Disk | 20 GB SSD | 40 GB SSD |

### Capacity Planning (2GB VPS)

Memory budget allocation for minimum 2GB VPS:

| Component | Allocation | Notes |
|-----------|------------|-------|
| JVM Heap | 512-1024 MB | Dynamic sizing |
| JVM Metaspace | 128-192 MB | Class metadata |
| PostgreSQL | ~256 MB | shared_buffers + connections |
| OS/Buffers | ~512 MB | Page cache, kernel |

#### JVM Configuration

JVM uses G1GC (Java 25 default, optimal for heaps <4GB):

```bash
-Xms512m -Xmx1024m             # Dynamic heap sizing
-XX:MetaspaceSize=128m
-XX:MaxMetaspaceSize=192m
```

GC logging is enabled at `/var/log/aplikasi-akunting/gc.log` for diagnostics.

#### PostgreSQL Configuration

Optimized for OLTP workload on small server:

| Setting | Value | Rationale |
|---------|-------|-----------|
| shared_buffers | 128 MB | ~6% RAM for shared server |
| effective_cache_size | 384 MB | OS cache estimate |
| work_mem | 4 MB | Per-operation sort memory |
| maintenance_work_mem | 64 MB | VACUUM, CREATE INDEX |
| max_connections | 20 | Match HikariCP pool |
| random_page_cost | 1.1 | SSD storage |

Autovacuum is tuned aggressively (5% scale factor) for OLTP.

#### Nginx Configuration

| Setting | Value | Notes |
|---------|-------|-------|
| worker_processes | auto | Matches CPU cores |
| worker_connections | 1024 | Per worker |
| keepalive_timeout | 65s | Connection reuse |
| rate_limit | 10r/s | Per IP, burst 20 |
| gzip | on | Level 5 compression |

Security headers (HSTS, X-Frame-Options, CSP) are configured in the SSL site template.

### Required Credentials

All credentials are stored in the **private deploy repository** (`clients/<client>/group_vars/all.yml`). See the deploy repository README for the full list of required credentials.

## Deployment Process

### Step 1: Server Setup

Run `site.yml` to install and configure:
- Java 25 (Azul Zulu)
- PostgreSQL 18 (from PGDG repository)
- Nginx with SSL (Let's Encrypt)
- Application directories
- Systemd service with optimized JVM settings
- Backup scripts and cron

```bash
ansible-playbook -i inventory.ini site.yml
```

### Step 2: Application Deployment

Run `deploy.yml` to:
- Build JAR locally (`./mvnw clean package -DskipTests`)
- Upload to server
- Configure admin user
- Restart service
- Run health check

```bash
ansible-playbook -i inventory.ini deploy.yml
```

### Directory Structure

```
/opt/aplikasi-akunting/
├── aplikasi-akunting.jar
├── application.properties
├── documents/
├── backup/
├── scripts/
│   ├── backup.sh
│   ├── backup-b2.sh
│   ├── backup-gdrive.sh
│   ├── backup-upload.sh   (sourced by the two upload scripts)
│   ├── backup-check.sh
│   ├── backup-lib.sh      (shared: config, logging, alerts, archive checks)
│   └── restore.sh
├── backup.conf
├── .backup-key
└── .pgpass

/var/log/aplikasi-akunting/
├── app.log
├── backup.log
└── restore.log
```

## Release Procedure

### Version Convention

Calendar versioning (CalVer): `YYYY.MM[.PATCH]-RELEASE`

Examples:
- `2025.11-RELEASE` (Monthly release)
- `2025.11.1-RELEASE` (Patch release within same month)
- `2025.11.2-RELEASE` (Second patch release)

### Release Steps

Follow these steps in order:

#### 0. Manual Backup on Production

Before any release, create a manual backup on the production server:

```bash
ssh <user>@<server>
sudo -u akunting /opt/aplikasi-akunting/scripts/backup.sh
```

Verify the backup was created:
```bash
ls -lh /opt/aplikasi-akunting/backup/ | tail -1
```

Server connection details are in the deploy repository's inventory file.

#### 1. Prepare Release Notes

```bash
# Copy template
cp docs/releases/TEMPLATE.md docs/releases/2025.12-RELEASE.md

# Edit release notes
# Fill in:
# - Highlights
# - What's New (features by category)
# - Improvements
# - Bug Fixes
# - Breaking Changes (if any)
# - Migration Guide
# - Known Issues
# - Dependencies
```

Get commits since last release for reference:
```bash
git log 2025.11-RELEASE..HEAD --oneline
```

#### 2. Update pom.xml Version

```bash
# Open pom.xml and update version
<version>2025.12-RELEASE</version>
```

#### 3. Build and Test

```bash
# Clean build with tests
./mvnw clean package

# Verify JAR was created
ls -lh target/accounting-finance-2025.12-RELEASE.jar

# Optional: Quick smoke test locally
java -jar target/accounting-finance-2025.12-RELEASE.jar
# Visit http://localhost:10000 and verify login works
# Ctrl+C to stop
```

#### 4. Commit Release Files

```bash
# Stage release files
git add pom.xml docs/releases/2025.12-RELEASE.md

# Commit with release message
git commit -m "release: bump version to 2025.12-RELEASE

🤖 Generated with [Claude Code](https://claude.com/claude-code)

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"

# Verify commit
git log --oneline -1
```

#### 5. Create Git Tag

```bash
# Create annotated tag
git tag -a 2025.12-RELEASE -m "Release 2025.12

See docs/releases/2025.12-RELEASE.md for full release notes."

# Verify tag
git tag -l | tail -5
```

#### 6. Push to Remote

```bash
# Push commits and tags
git push origin main
git push origin 2025.12-RELEASE

# Verify on GitHub
# Check: https://github.com/<username>/<repo>/releases
```

**Note:** GitHub Actions will automatically create the release when the tag is pushed.

The `.github/workflows/release.yml` workflow will:
1. Trigger on any `*-RELEASE` tag
2. Build the JAR (`./mvnw package -DskipTests`)
3. Read release notes from `docs/releases/<TAG>.md`
4. Create GitHub Release with the JAR attached

Wait ~2 minutes for the workflow to complete, then verify at:
```bash
gh run list --workflow=release.yml --limit 1
gh release view 2025.12-RELEASE
```

#### 7. Prepare Next Development Iteration

```bash
# Update pom.xml to next SNAPSHOT version
<version>2025.12-SNAPSHOT</version>

# Commit
git add pom.xml
git commit -m "chore: prepare for next development iteration"
git push origin main
```

**Note:** For production deployment, see the "Deployment Process" section above.

### Release Checklist

Use this checklist for each release:

- [ ] Manual backup on production server
- [ ] All tests passing (`./mvnw test`)
- [ ] Release notes created in `docs/releases/`
- [ ] Version updated in `pom.xml`
- [ ] JAR built successfully (`./mvnw clean package`)
- [ ] Release files committed
- [ ] Git tag created
- [ ] Changes pushed to GitHub
- [ ] GitHub Actions workflow completed (auto-creates release)
- [ ] Deployed to production (`ansible-playbook -i inventory.ini deploy.yml`)
- [ ] Production health verified (login page returns HTTP 200)
- [ ] Next SNAPSHOT version prepared

### GitHub Actions (Optional)

`.github/workflows/deploy.yml` triggers on release tags:

```yaml
on:
  push:
    tags:
      - '[0-9][0-9][0-9][0-9].[0-9][0-9]-RELEASE'
```

## Backup & Restore

### Backup Schedule

| Type | Schedule | Retention | Location |
|------|----------|-----------|----------|
| Local | Daily 02:00 | 7 days | `/opt/aplikasi-akunting/backup/` |
| B2 | Daily 03:00 | 4 weeks | Backblaze B2 |
| Google Drive | Daily 04:00 | 12 months | Google Drive |
| Freshness check | Daily 06:00 | — | local archive and each enabled remote |

### Failure Alerts

Every backup job sends an alert and exits non-zero on failure. The role requires an alert channel and fails without one: Telegram (`telegram_bot_enabled` + `telegram_admin_chat_id`) and/or a webhook (`backup_notification_enabled` + `backup_notification_url`).

| Job | Fails and alerts when |
|-----|-----------------------|
| `backup.sh` | `pg_dump` fails or writes nothing, `manifest.json` is not valid JSON, the archive cannot be read back, or any other command fails. The partial working directory is removed. |
| `backup-b2.sh`, `backup-gdrive.sh` | the newest local archive is older than `backup_max_age_hours` (default 26), its `database.sql` is empty, its manifest is invalid, encryption or upload fails, or the uploaded object is missing or empty. A stale archive is never uploaded. |
| `backup-check.sh` | the newest local archive, or the newest object in B2/Google Drive, is older than `backup_max_age_hours`. Catches jobs that stopped running. |

A failed retention cleanup on a remote alerts without failing the upload.

### Backup Contents

```
aplikasi-akunting_20251129_020000.tar.gz
└── aplikasi-akunting_20251129_020000/
    ├── database.sql
    ├── documents.tar.gz
    └── manifest.json
```

### Manual Backup

```bash
sudo -u akunting /opt/aplikasi-akunting/scripts/backup.sh
```

### Restore Procedure

```bash
# List available backups
ls -la /opt/aplikasi-akunting/backup/

# Restore
sudo /opt/aplikasi-akunting/scripts/restore.sh \
  /opt/aplikasi-akunting/backup/aplikasi-akunting_20251129_020000.tar.gz
```

Restore process:
1. Validates checksums
2. Stops application
3. Drops and recreates database
4. Imports database dump
5. Restores documents
6. Starts application

### Disaster Recovery

1. Provision new VPS
2. Run `site.yml`
3. Copy backup file
4. Run restore script

**RTO:** ~4 hours | **RPO:** 24 hours

### Encryption Key Management

Backup encryption key location: `/opt/aplikasi-akunting/.backup-key`

**CRITICAL:** Save this key externally. Without it, encrypted backups are unrecoverable.

Store in at least TWO locations:
- Password manager (Bitwarden, 1Password)
- Printed copy in physical safe
- Encrypted USB drive

## Service Management

### Application Service

```bash
# Status
sudo systemctl status aplikasi-akunting

# Start/Stop/Restart
sudo systemctl start aplikasi-akunting
sudo systemctl stop aplikasi-akunting
sudo systemctl restart aplikasi-akunting

# Logs
sudo journalctl -u aplikasi-akunting -f
tail -f /var/log/aplikasi-akunting/app.log
```

### Nginx

```bash
# Test config
sudo nginx -t

# Reload
sudo systemctl reload nginx

# Access logs
tail -f /var/log/nginx/access.log
```

### PostgreSQL

```bash
# Status
sudo systemctl status postgresql

# Connect
sudo -u postgres psql -d accountingdb

# Check connections
sudo -u postgres psql -c "SELECT * FROM pg_stat_activity WHERE datname = 'accountingdb';"
```

## SSL Certificate

Using Let's Encrypt (configured by Ansible).

```bash
# Check certificate
sudo certbot certificates

# Test renewal
sudo certbot renew --dry-run

# Force renewal
sudo certbot renew --force-renewal

# Check expiry
echo | openssl s_client -servername <your-domain> \
  -connect <your-domain>:443 2>/dev/null | \
  openssl x509 -noout -dates
```

## Monitoring Checklist

### Daily
- [ ] Backup log shows success
- [ ] Application running

### Weekly
- [ ] Review application logs for errors
- [ ] Check disk usage
- [ ] SSL certificate >30 days valid

### Monthly
- [ ] Test restore procedure
- [ ] Rotate old backups
- [ ] Update system packages

## Troubleshooting

### Application Won't Start

```bash
sudo systemctl status aplikasi-akunting
ps aux | grep java
sudo netstat -tlnp | grep 10000
tail -100 /var/log/aplikasi-akunting/app.log
```

### Flyway Migration Checksum Mismatch

This occurs when a migration file (e.g., V003) was modified after it was already applied to production. Flyway records checksums of applied migrations and rejects mismatches.

```
Migration checksum mismatch for migration version 003
-> Applied to database : -1535984110
-> Resolved locally    : -1414456606
```

**Fix:**

1. Identify what schema changes were added to the modified migration (e.g., new columns)
2. Apply those changes manually:

```bash
# Check current table schema
sudo -u postgres psql -d accountingdb -c "\d <table_name>"

# Add missing columns
sudo -u postgres psql -d accountingdb \
  -c "ALTER TABLE <table_name> ADD COLUMN <column_name> <type>;"
```

3. Update the checksum in Flyway's schema history to match the local file:

```bash
sudo -u postgres psql -d accountingdb \
  -c "UPDATE flyway_schema_history SET checksum = <new_checksum> WHERE version = '<version>';"
```

4. Restart the application:

```bash
sudo systemctl restart aplikasi-akunting
```

**Note:** The new checksum value is shown in the error message as "Resolved locally".

### Database Connection Failed

```bash
sudo systemctl status postgresql
sudo -u postgres psql -d accountingdb -c "SELECT 1;"
grep -i "datasource" /opt/aplikasi-akunting/application.properties
```

### SSL Certificate Expired

```bash
sudo certbot certificates
sudo certbot renew --force-renewal
sudo systemctl restart nginx
```

### Backup Failed

```bash
cat /var/log/aplikasi-akunting/backup.log
df -h
cat /opt/aplikasi-akunting/.pgpass
sudo -u akunting bash -x /opt/aplikasi-akunting/scripts/backup.sh
```

### Database Reset (Clear All Data)

**WARNING:** This will delete ALL data. Use only for fresh deployments or when data migration is not needed.

```bash
# Stop application first
sudo systemctl stop aplikasi-akunting

# Drop and recreate database
sudo -u postgres psql -c "DROP DATABASE IF EXISTS accountingdb;"
sudo -u postgres psql -c "CREATE DATABASE accountingdb OWNER akunting;"

# Restart application (Flyway will recreate schema and seed data)
sudo systemctl start aplikasi-akunting
```

## PostgreSQL Major Version Upgrade

Use the `upgrade-postgresql.yml` playbook for major version upgrades (e.g., 17 → 18).

### Prerequisites

1. Schedule maintenance window (5-30 min downtime depending on database size)
2. Verify backup is current
3. Ensure PGDG repository is configured

### Upgrade Procedure

```bash
# Run upgrade playbook
ansible-playbook -i inventory.ini upgrade-postgresql.yml \
  -e "pg_old_version=17 pg_new_version=18"
```

The playbook will:
1. Stop the application
2. Create full database backup using `pg_dump`
3. Install new PostgreSQL version from PGDG
4. Configure new cluster on port 5432
5. Restore database to new cluster
6. Start the application
7. Verify connectivity

### Post-Upgrade Verification

```bash
# Check PostgreSQL version
sudo -u postgres psql -c "SELECT version();"

# Verify optimized settings applied
sudo -u postgres psql -c "SHOW shared_buffers; SHOW work_mem;"

# Check application health
curl -s http://localhost:10000/actuator/health
```

### Rollback (if needed)

Old cluster remains available on port 5433:

```bash
# Stop new cluster
sudo pg_ctlcluster 18 main stop

# Configure old cluster back to port 5432
sudo sed -i 's/port = 5433/port = 5432/' /etc/postgresql/17/main/postgresql.conf

# Start old cluster
sudo pg_ctlcluster 17 main start

# Restart application
sudo systemctl restart aplikasi-akunting
```

After verification, remove old cluster:

```bash
sudo pg_dropcluster 17 main
sudo apt remove postgresql-17
```

## Rollback Procedure

### Application Rollback

```bash
# Check backup exists
ls -la /opt/aplikasi-akunting/aplikasi-akunting.jar.backup

# Rollback
mv /opt/aplikasi-akunting/aplikasi-akunting.jar.backup /opt/aplikasi-akunting/aplikasi-akunting.jar
sudo systemctl restart aplikasi-akunting

# Verify
curl -I http://localhost:10000/login
```

### Database Rollback

```bash
sudo /opt/aplikasi-akunting/scripts/restore.sh \
  /opt/aplikasi-akunting/backup/LATEST.tar.gz
```

## Configuration Reference

All deployment configuration (Ansible variables, inventory, credentials) is maintained in the **private deploy repository**. See that repository's README for variable reference and examples.
