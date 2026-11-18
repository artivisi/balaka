# Deployment Guide

Deploy Accounting Finance application to Digital Ocean VPS.

## Prerequisites

### Local Machine

```bash
# Install Pulumi
curl -fsSL https://get.pulumi.com | sh

# Install Ansible
pip install ansible

# Install Ansible collections
cd deploy/ansible
ansible-galaxy collection install -r requirements.yml

# Install Python bcrypt (for password hashing)
pip install bcrypt
```

### Digital Ocean

1. Create a Digital Ocean account
2. Generate API token: https://cloud.digitalocean.com/account/api/tokens
3. Add SSH key to Digital Ocean and note the fingerprint

## Infrastructure Setup (Pulumi)

```bash
cd deploy/pulumi

# Install dependencies
npm install

# Login to Pulumi (use local backend for simplicity)
pulumi login --local

# Create stack
pulumi stack init prod

# Configure Digital Ocean token
export DIGITALOCEAN_TOKEN="your-token-here"

# Set required configuration
pulumi config set dropletName accounting-app
pulumi config set region sgp1
pulumi config set sshKeyName "your-ssh-key-name"
pulumi config set domainName artivisi.id
pulumi config set subdomainName akunting

# Preview changes
pulumi preview

# Deploy infrastructure
pulumi up
```

Get the droplet IP:
```bash
pulumi stack output dropletIp
```

## Server Configuration (Ansible)

### 1. Create Configuration Files

```bash
cd deploy/ansible

# Create inventory
cp inventory.ini.example inventory.ini
# Edit inventory.ini and set YOUR_DROPLET_IP

# Create variables
cp group_vars/all.yml.example group_vars/all.yml
```

### 2. Configure Variables

Edit `group_vars/all.yml`:

```yaml
# REQUIRED: Change these values
db_password: "your-secure-db-password"
admin_password_plain: "your-secure-admin-password"
app_domain: "your-domain.com"  # or IP address

# REQUIRED: production secrets. Generate each one with:
#   openssl rand -base64 32
# Both site.yml and deploy.yml stop in pre_tasks if the key is missing or
# malformed. Set it BEFORE the first boot. Never commit the real value;
# group_vars/all.yml is gitignored.
document_encryption_key: "<output of: openssl rand -base64 32>"

# Optional: Enable SSL
ssl_enabled: true
ssl_email: "your-email@example.com"

# Optional: Enable Google Cloud Vision for Receipt OCR
google_cloud_vision_enabled: true
google_cloud_credentials_file: "~/gcp-credentials/accounting-app-credentials.json"

# Optional: Enable Backblaze B2 Backup
backup_b2_enabled: true
backup_b2_account_id: "0123456789abcdef0123"  # Your B2 Account ID (NOT email)
backup_b2_application_key: "K003xxxxxxxxxxxxxxxxxx"  # Your B2 Application Key
backup_b2_bucket: "your-bucket-name"
```

#### Getting Backblaze B2 Credentials

1. Sign up at https://www.backblaze.com/b2/cloud-storage.html
2. Create a bucket (e.g., "my-company-backup")
3. Go to **App Keys** section in the B2 dashboard
4. Look at the top of the page - you'll see:
   - **Account ID** (also called `keyID`) - a 25-character string like `0035b35e2e1a0000000000001`
   - This is displayed above the list of application keys
5. Click **Add a New Application Key** button
6. When the key is created, you'll see two values:
   - **keyID** (Application Key ID) - this is the SAME as your Account ID shown at the top
   - **applicationKey** - a long secret string (shown only once!)
7. For the Ansible configuration:
   - `backup_b2_account_id` = the **Account ID** / **keyID** (25 characters)
   - `backup_b2_application_key` = the **applicationKey** (the long secret)

**Note:** The **Account ID** and **Application Key ID** (keyID) are typically the SAME value in B2. You only need the Account ID shown at the top of the App Keys page.

#### Setting up Google Cloud Vision

To enable receipt OCR with Google Cloud Vision:

1. **Create GCP Project**:
   - Go to https://console.cloud.google.com
   - Create a new project (e.g., "accounting-ocr")

2. **Enable Vision API**:
   - In the project, go to APIs & Services → Library
   - Search for "Cloud Vision API"
   - Click Enable

3. **Create Service Account**:
   - Go to IAM & Admin → Service Accounts
   - Click "Create Service Account"
   - Name: "accounting-ocr-service"
   - Click "Create and Continue"
   - Grant role: "Cloud Vision API User"
   - Click "Done"

4. **Download JSON Key**:
   - Click on the service account you just created
   - Go to "Keys" tab
   - Click "Add Key" → "Create new key"
   - Choose "JSON" format
   - Save the file (e.g., `~/gcp-credentials/accounting-app-credentials.json`)

5. **Configure in Ansible**:
   ```yaml
   google_cloud_vision_enabled: true
   google_cloud_credentials_file: "~/gcp-credentials/accounting-app-credentials.json"
   ```

6. **Deploy**: Ansible will automatically upload the credentials to the server

### Production secret requirements

`site.yml` and `deploy.yml` both validate `document_encryption_key` in
`pre_tasks`, before any package installation, migration, file upload, or
service restart. A missing or malformed key aborts the run with a message
naming the variable and the generation command, instead of failing later inside
the application startup guard.

| Variable | Requirement | Generate with |
|----------|-------------|---------------|
| `document_encryption_key` | Non-empty, Base64-encoded 32 bytes (AES-256) | `openssl rand -base64 32` |

The value must be set in `group_vars/all.yml` (gitignored) before the first
boot. Do not put it in the repository or in the inventory file.

Rotation has consequences:

- **`document_encryption_key`**: this key encrypts PII fields and uploaded
  documents. Rotating it makes previously encrypted data unreadable unless a
  re-encryption migration is performed first. Losing it means the encrypted
  data cannot be recovered. Back it up separately from the database.

`PiiEncryptionProductionGuard` remains in place and fails closed at startup.
The playbook checks are an earlier, actionable layer, not a replacement.

### 3. Run Initial Setup

```bash
# Full server setup (first time only)
ansible-playbook site.yml
```

### 4. Deployment Output and Secrets

Ansible `debug` output is written to stdout, CI logs, and any saved playbook
log. Treat it as a public sink: never print a credential there.

- The `site.yml` deployment summary prints the admin username and tells you to
  use the `admin_password_plain` value from `group_vars/all.yml`. It never
  prints the password itself; it is not a generated value.
- When remote backup is enabled, the summary prints the path of the backup
  encryption key, not the key. Retrieve it once over an authenticated SSH
  session and store it in at least two safe places:

  ```bash
  sudo cat /opt/accounting-finance/.backup-key
  ```

Before running a playbook, check that no `debug` task re-introduces a leak:

```bash
cd deploy/ansible
./tests/check-secret-debug.py
```

The check exits 1 when a `debug` task interpolates a credential name such as
`admin_password_plain`, `db_password`, `document_encryption_key`, `*_secret`,
`*_token`, or a backup key. Both `msg` and
`var` are scanned, including Jinja references such as
`var: "{{ admin_password_plain }}"`. It is a static check for direct `{{ ... }}`
references; indirect flows (loops, `set_fact`, `slurp`, command output) still
require manual review during code review. CI runs the same guard in the
`deploy-lint` job, and its fixtures cover the `debug.var` regression.

## SSL Setup

After initial deployment, setup SSL for `akunting.artivisi.id`:

```bash
cd deploy/ansible

# Make sure DNS is pointed to server IP first, then run:
ansible-playbook setup-ssl.yml
```

This will:
1. Install certbot
2. Obtain Let's Encrypt certificate
3. Configure nginx for HTTPS with auto-redirect
4. Setup auto-renewal cron job

## Application Deployment

For subsequent deployments:

```bash
cd deploy/ansible

# Build and deploy application
ansible-playbook deploy.yml
```

This will:
1. Build the jar locally
2. Stop the application
3. Upload new jar to server
4. Update admin password in database (bcrypt hashed)
5. Start the application
6. Wait for health check

## Changing Admin Password

Edit `group_vars/all.yml`:
```yaml
admin_password_plain: "new-password-here"
```

Then run:
```bash
ansible-playbook deploy.yml
```

The plaintext password will be converted to bcrypt hash and injected into PostgreSQL.

## Architecture

```
Internet → Nginx (port 80/443) → Spring Boot (port 10000) → PostgreSQL (local)
```

## Specifications

- **Droplet**: s-1vcpu-2gb ($12/month)
  - 1 vCPU
  - 2 GB RAM
  - 50 GB SSD
- **OS**: Ubuntu 24.04 LTS
- **Java**: Azul Zulu JDK 25
- **Database**: PostgreSQL (local)
- **Reverse Proxy**: Nginx

## Commands

```bash
# View application logs
journalctl -u accounting-finance -f

# Restart application
sudo systemctl restart accounting-finance

# Check application status
sudo systemctl status accounting-finance

# Check PostgreSQL status
sudo systemctl status postgresql

# View nginx logs
tail -f /var/log/nginx/access.log
tail -f /var/log/nginx/error.log
```

## Troubleshooting

### Application won't start

```bash
# Check logs
journalctl -u accounting-finance -n 100

# Check if port is in use
sudo ss -tlnp | grep 10000

# Verify database connection
sudo -u postgres psql -d accountingdb -c "SELECT 1"
```

### Database issues

```bash
# Connect to database
sudo -u postgres psql -d accountingdb

# Check admin user
SELECT username, active FROM users WHERE username = 'admin';
```

### Reset admin password manually

```bash
# Generate bcrypt hash
python3 -c "import bcrypt; print(bcrypt.hashpw(b'newpassword', bcrypt.gensalt(rounds=10)).decode())"

# Update in database
sudo -u postgres psql -d accountingdb -c "UPDATE users SET password = 'bcrypt-hash-here' WHERE username = 'admin'"
```
