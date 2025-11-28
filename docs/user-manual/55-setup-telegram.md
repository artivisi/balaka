# Setup Telegram Bot

## Kapan Anda Membutuhkan Ini

Gunakan panduan ini ketika Anda:
- Ingin mengaktifkan fitur Telegram Receipt di server
- Pertama kali melakukan deployment aplikasi dengan fitur Telegram
- Perlu mengkonfigurasi ulang koneksi Telegram bot

## Konsep yang Perlu Dipahami

### Arsitektur Integrasi Telegram

```
User HP (Telegram)
    │
    ▼
Telegram Server
    │
    ▼ (Webhook POST)
Aplikasi Akunting (/api/telegram/webhook)
    │
    ▼
TelegramBotService → DraftTransactionService
```

Telegram menggunakan **webhook** untuk mengirim update ke aplikasi. Setiap kali user mengirim pesan ke bot, Telegram akan melakukan HTTP POST ke URL webhook yang dikonfigurasi.

### Komponen yang Dibutuhkan

| Komponen | Fungsi |
|----------|--------|
| Bot Token | Kredensial untuk berkomunikasi dengan Telegram API |
| Bot Username | Nama bot yang akan dicari user di Telegram |
| Webhook URL | URL publik yang bisa diakses Telegram |
| Secret Token | Token rahasia untuk validasi request webhook |

## Skenario 1: Membuat Bot di Telegram

**Situasi**: Anda belum memiliki bot Telegram untuk aplikasi.

**Langkah-langkah**:

1. Buka Telegram di HP atau desktop
2. Cari **@BotFather** (bot resmi Telegram untuk membuat bot)
3. Mulai chat dan ketik `/newbot`
4. BotFather akan meminta nama bot, contoh: `Aplikasi Akunting ArtiVisi`
5. BotFather akan meminta username bot, contoh: `ArtivisiAkuntingBot`
   - Username harus unik dan diakhiri dengan `Bot`
6. Setelah berhasil, BotFather akan memberikan **token**:
   ```
   Done! Congratulations on your new bot. You will find it at t.me/ArtivisiAkuntingBot.
   You can now add a description, about section and profile picture for your bot.

   Use this token to access the HTTP API:
   7123456789:AAHx8qZ-abcdefghijklmnopqrstuvwxyz
   ```
7. Simpan token ini dengan aman - jangan bagikan ke siapapun

**Konfigurasi Tambahan di BotFather (Opsional)**:

```
/setdescription - Deskripsi bot
/setabouttext - Tentang bot
/setuserpic - Foto profil bot
```

## Skenario 2: Konfigurasi Environment Variables

**Situasi**: Bot sudah dibuat, sekarang perlu dikonfigurasi di server.

**Environment Variables yang Diperlukan**:

```bash
# Aktifkan integrasi Telegram
TELEGRAM_BOT_ENABLED=true

# Token dari BotFather
TELEGRAM_BOT_TOKEN=7123456789:AAHx8qZ-abcdefghijklmnopqrstuvwxyz

# Username bot (tanpa @)
TELEGRAM_BOT_USERNAME=ArtivisiAkuntingBot

# URL webhook (domain publik aplikasi)
TELEGRAM_WEBHOOK_URL=https://akunting.example.com/api/telegram/webhook

# Secret token untuk validasi webhook (generate sendiri, min 32 karakter)
TELEGRAM_WEBHOOK_SECRET=abc123xyz789secrettoken456def
```

**Cara Generate Secret Token**:

```bash
# Linux/macOS
openssl rand -hex 32

# Atau gunakan online generator dengan min 32 karakter
```

**Konfigurasi di Docker Compose**:

```yaml
services:
  app:
    environment:
      - TELEGRAM_BOT_ENABLED=true
      - TELEGRAM_BOT_TOKEN=${TELEGRAM_BOT_TOKEN}
      - TELEGRAM_BOT_USERNAME=${TELEGRAM_BOT_USERNAME}
      - TELEGRAM_WEBHOOK_URL=${TELEGRAM_WEBHOOK_URL}
      - TELEGRAM_WEBHOOK_SECRET=${TELEGRAM_WEBHOOK_SECRET}
```

**Konfigurasi di Systemd**:

```ini
[Service]
Environment="TELEGRAM_BOT_ENABLED=true"
Environment="TELEGRAM_BOT_TOKEN=7123456789:AAH..."
Environment="TELEGRAM_BOT_USERNAME=ArtivisiAkuntingBot"
Environment="TELEGRAM_WEBHOOK_URL=https://akunting.example.com/api/telegram/webhook"
Environment="TELEGRAM_WEBHOOK_SECRET=abc123xyz789..."
```

## Skenario 3: Mendaftarkan Webhook ke Telegram

**Situasi**: Environment sudah dikonfigurasi, perlu mendaftarkan webhook ke Telegram.

**Langkah-langkah**:

1. Pastikan aplikasi sudah berjalan dan webhook endpoint bisa diakses publik
2. Jalankan command berikut untuk mendaftarkan webhook:

```bash
curl -X POST "https://api.telegram.org/bot<TOKEN>/setWebhook" \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://akunting.example.com/api/telegram/webhook",
    "secret_token": "abc123xyz789secrettoken456def",
    "allowed_updates": ["message"]
  }'
```

Ganti:
- `<TOKEN>` dengan bot token
- `url` dengan URL webhook aplikasi
- `secret_token` dengan secret yang sama di environment

**Response Sukses**:
```json
{
  "ok": true,
  "result": true,
  "description": "Webhook was set"
}
```

3. Verifikasi webhook sudah terdaftar:

```bash
curl "https://api.telegram.org/bot<TOKEN>/getWebhookInfo"
```

**Response**:
```json
{
  "ok": true,
  "result": {
    "url": "https://akunting.example.com/api/telegram/webhook",
    "has_custom_certificate": false,
    "pending_update_count": 0,
    "max_connections": 40
  }
}
```

## Skenario 4: Setup dengan HTTPS (SSL/TLS)

**Situasi**: Telegram mensyaratkan webhook harus HTTPS.

**Opsi 1: Menggunakan Reverse Proxy (Nginx)**

```nginx
server {
    listen 443 ssl;
    server_name akunting.example.com;

    ssl_certificate /etc/letsencrypt/live/akunting.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/akunting.example.com/privkey.pem;

    location /api/telegram/webhook {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Telegram-Bot-Api-Secret-Token $http_x_telegram_bot_api_secret_token;
    }
}
```

**Opsi 2: Menggunakan Cloudflare Tunnel**

```bash
# Install cloudflared
cloudflared tunnel create akunting
cloudflared tunnel route dns akunting akunting.example.com

# Jalankan tunnel
cloudflared tunnel run --url http://localhost:8080 akunting
```

## Skenario 5: Testing Integrasi

**Situasi**: Semua konfigurasi selesai, perlu memastikan integrasi berfungsi.

**Langkah-langkah**:

1. Buka Telegram, cari bot dengan username yang dikonfigurasi
2. Kirim `/start`
3. Bot harus merespon dengan pesan selamat datang
4. Cek log aplikasi untuk memastikan request masuk:
   ```
   INFO TelegramBotService - Received message from username (userid)
   ```

**Test dengan curl (Simulasi Webhook)**:

```bash
curl -X POST "http://localhost:8080/api/telegram/webhook" \
  -H "Content-Type: application/json" \
  -H "X-Telegram-Bot-Api-Secret-Token: abc123xyz789secrettoken456def" \
  -d '{
    "update_id": 123456789,
    "message": {
      "message_id": 1,
      "from": {
        "id": 12345,
        "is_bot": false,
        "first_name": "Test",
        "username": "testuser"
      },
      "chat": {
        "id": 12345,
        "type": "private"
      },
      "date": 1234567890,
      "text": "/status"
    }
  }'
```

## Skenario 6: Troubleshooting Webhook

**Situasi**: Bot tidak merespon atau webhook tidak berfungsi.

**Langkah Diagnosis**:

1. **Cek status webhook**:
   ```bash
   curl "https://api.telegram.org/bot<TOKEN>/getWebhookInfo"
   ```

   Perhatikan field:
   - `pending_update_count` - jumlah update yang menunggu
   - `last_error_date` - waktu error terakhir
   - `last_error_message` - pesan error

2. **Cek log aplikasi**:
   ```bash
   # Cari error terkait Telegram
   grep -i telegram /var/log/aplikasi/app.log
   ```

3. **Cek konektivitas**:
   ```bash
   # Pastikan endpoint bisa diakses dari luar
   curl -I https://akunting.example.com/api/telegram/webhook
   ```

4. **Reset webhook jika bermasalah**:
   ```bash
   # Hapus webhook
   curl "https://api.telegram.org/bot<TOKEN>/deleteWebhook"

   # Set ulang
   curl -X POST "https://api.telegram.org/bot<TOKEN>/setWebhook" ...
   ```

**Masalah Umum**:

| Masalah | Penyebab | Solusi |
|---------|----------|--------|
| Bot tidak merespon | Webhook belum terdaftar | Jalankan setWebhook |
| 401 Unauthorized | Secret token tidak cocok | Samakan secret di env dan webhook |
| 502 Bad Gateway | Aplikasi tidak berjalan | Restart aplikasi |
| SSL Certificate Error | Sertifikat tidak valid | Gunakan Let's Encrypt atau self-signed dengan upload |

## Skenario 7: Menonaktifkan Integrasi

**Situasi**: Perlu menonaktifkan fitur Telegram sementara.

**Langkah-langkah**:

1. Set environment variable:
   ```bash
   TELEGRAM_BOT_ENABLED=false
   ```

2. Restart aplikasi

3. (Opsional) Hapus webhook:
   ```bash
   curl "https://api.telegram.org/bot<TOKEN>/deleteWebhook"
   ```

## Security Checklist

- [ ] Token bot tidak di-commit ke repository
- [ ] Secret token minimal 32 karakter
- [ ] Webhook menggunakan HTTPS
- [ ] Environment variables tidak ter-expose di log
- [ ] Token disimpan di secrets manager (untuk production)

## Referensi

- [Telegram Bot API](https://core.telegram.org/bots/api)
- [Telegram Webhooks Guide](https://core.telegram.org/bots/webhooks)

## Lihat Juga

- [Telegram Receipt](13-telegram-receipt.md) - Panduan penggunaan untuk end user
- [Setup Awal](50-setup-awal.md) - Setup aplikasi secara umum
