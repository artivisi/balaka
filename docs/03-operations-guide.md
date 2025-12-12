# Operations Guide

Complete guide for deploying, releasing, and operating the accounting application.

## Quick Start

```bash
# 1. Configure Ansible
cd deploy/ansible
cp group_vars/all.yml.example group_vars/all.yml
cp inventory.ini.example inventory.ini
# Edit with your credentials

# 2. Server setup (one-time)
ansible-playbook -i inventory.ini site.yml

# 3. Deploy application
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

### Required Credentials

| Credential | Source | Notes |
|------------|--------|-------|
| VPS Provider | IDCloudHost, Biznet, DigitalOcean | For provisioning |
| Domain | Registrar | Point A record to VPS IP |
| SSH Key | `ssh-keygen -t ed25519` | For passwordless SSH |
| Database Password | `openssl rand -base64 24` | Strong, random |
| Admin Credentials | Your choice | NOT 'admin' as username |
| SSL Email | Your email | For Let's Encrypt |
| B2 Account ID | Backblaze B2 Console | Optional: 25-char keyID |
| B2 Application Key | Backblaze B2 Console | Optional: backup |

## Deployment Process

### Step 1: Server Setup

Run `site.yml` to install and configure:
- Java 25
- PostgreSQL 16
- Nginx with SSL
- Application directories
- Systemd service
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
/opt/accounting-finance/
├── app.jar
├── application.properties
├── documents/
├── backup/
├── scripts/
│   ├── backup.sh
│   ├── backup-b2.sh
│   ├── backup-gdrive.sh
│   └── restore.sh
├── backup.conf
├── .backup-key
└── .pgpass

/var/log/accounting-finance/
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

- [ ] All tests passing (`./mvnw test`)
- [ ] Release notes created in `docs/releases/`
- [ ] Version updated in `pom.xml`
- [ ] JAR built successfully (`./mvnw clean package`)
- [ ] Release files committed
- [ ] Git tag created
- [ ] Changes pushed to GitHub
- [ ] GitHub Actions workflow completed (auto-creates release)
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
| Local | Daily 02:00 | 7 days | `/opt/accounting-finance/backup/` |
| B2 | Daily 03:00 | 4 weeks | Backblaze B2 |
| Google Drive | Daily 04:00 | 12 months | Google Drive |

### Backup Contents

```
accounting-finance_20251129_020000.tar.gz
└── accounting-finance_20251129_020000/
    ├── database.sql
    ├── documents.tar.gz
    └── manifest.json
```

### Manual Backup

```bash
sudo -u accounting /opt/accounting-finance/scripts/backup.sh
```

### Restore Procedure

```bash
# List available backups
ls -la /opt/accounting-finance/backup/

# Restore
sudo /opt/accounting-finance/scripts/restore.sh \
  /opt/accounting-finance/backup/accounting-finance_20251129_020000.tar.gz
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

Backup encryption key location: `/opt/accounting-finance/.backup-key`

**CRITICAL:** Save this key externally. Without it, encrypted backups are unrecoverable.

Store in at least TWO locations:
- Password manager (Bitwarden, 1Password)
- Printed copy in physical safe
- Encrypted USB drive

## Service Management

### Application Service

```bash
# Status
sudo systemctl status accounting-finance

# Start/Stop/Restart
sudo systemctl start accounting-finance
sudo systemctl stop accounting-finance
sudo systemctl restart accounting-finance

# Logs
sudo journalctl -u accounting-finance -f
tail -f /var/log/accounting-finance/app.log
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
echo | openssl s_client -servername akunting.artivisi.id \
  -connect akunting.artivisi.id:443 2>/dev/null | \
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
sudo systemctl status accounting-finance
ps aux | grep java
sudo netstat -tlnp | grep 10000
tail -100 /var/log/accounting-finance/app.log
```

### Database Connection Failed

```bash
sudo systemctl status postgresql
sudo -u postgres psql -d accountingdb -c "SELECT 1;"
grep -i "datasource" /opt/accounting-finance/application.properties
```

### SSL Certificate Expired

```bash
sudo certbot certificates
sudo certbot renew --force-renewal
sudo systemctl restart nginx
```

### Backup Failed

```bash
cat /var/log/accounting-finance/backup.log
df -h
cat /opt/accounting-finance/.pgpass
sudo -u accounting bash -x /opt/accounting-finance/scripts/backup.sh
```

### Database Reset (Clear All Data)

**WARNING:** This will delete ALL data. Use only for fresh deployments or when data migration is not needed.

```bash
# Stop application first
sudo systemctl stop accounting-finance

# Drop and recreate database
sudo -u postgres psql -c "DROP DATABASE IF EXISTS accountingdb;"
sudo -u postgres psql -c "CREATE DATABASE accountingdb OWNER accounting;"

# Restart application (Flyway will recreate schema and seed data)
sudo systemctl start accounting-finance
```

## Rollback Procedure

### Application Rollback

```bash
# Check backup exists
ls -la /opt/accounting-finance/app.jar.backup

# Rollback
mv /opt/accounting-finance/app.jar.backup /opt/accounting-finance/app.jar
systemctl restart accounting-finance

# Verify
curl -I http://localhost:10000/login
```

### Database Rollback

```bash
sudo /opt/accounting-finance/scripts/restore.sh \
  /opt/accounting-finance/backup/LATEST.tar.gz
```

## Configuration Reference

### group_vars/all.yml

```yaml
# Application
app_name: accounting-finance
app_domain: akunting.artivisi.id

# Database
db_password: "<STRONG_PASSWORD>"

# Admin user
admin_username: "<UNIQUE_USERNAME>"
admin_password_plain: "<ADMIN_PASSWORD>"
admin_full_name: "Your Name"
admin_email: "your@email.com"

# SSL
ssl_enabled: true
ssl_email: "admin@artivisi.com"

# Backup
backup_cron_enabled: true
backup_cron_hour: "2"
backup_retention_count: 7

# B2 (optional)
backup_b2_enabled: true
backup_b2_account_id: "..."
backup_b2_application_key: "..."
backup_b2_bucket: "artivisi-backup"

# Google Drive (optional)
backup_gdrive_enabled: true
backup_gdrive_token: '{"access_token":"...", ...}'
backup_gdrive_folder: "accounting-backup"
```

### inventory.ini

```ini
[app]
akunting.artivisi.id ansible_user=root
```
