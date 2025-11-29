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

# Optional: Enable SSL
ssl_enabled: true
ssl_email: "your-email@example.com"

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

### 3. Run Initial Setup

```bash
# Full server setup (first time only)
ansible-playbook site.yml
```

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
