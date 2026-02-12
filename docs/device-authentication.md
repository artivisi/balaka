# Device Authentication (OAuth 2.0 Device Flow)

## Overview

AI assistants (Claude Code, Gemini CLI, etc.) authenticate using **OAuth 2.0 Device Authorization Grant (RFC 8628)**.

This provides:
- ✅ User traceability (each token linked to specific user)
- ✅ Browser-based authorization (secure, familiar)
- ✅ Per-device tokens (name devices, revoke individually)
- ✅ Full audit trail
- ✅ Industry standard (same as Docker Desktop, GitHub CLI, AWS CLI)

## How It Works

```
┌─────────────┐                                    ┌──────────────┐
│ Claude Code │                                    │  Web Browser │
│  (Desktop)  │                                    │    (User)    │
└──────┬──────┘                                    └──────┬───────┘
       │                                                  │
       │ 1. POST /api/device/code                        │
       │    { clientId: "claude-code" }                  │
       ├─────────────────────────────────►               │
       │                                                  │
       │ 2. {device_code, user_code,                     │
       │     verification_uri}                           │
       │◄─────────────────────────────────               │
       │                                                  │
       │ 3. Display:                                     │
       │    "Go to http://localhost:10000/device         │
       │     Enter code: WDJB-MJHT"                      │
       │                                                  │
       │                                  4. User opens   │
       │                                     browser      │
       │                                  ───────────────►│
       │                                                  │
       │                                  5. Login &      │
       │                                     enter code   │
       │                                                  │
       │                                  6. Authorize    │
       │                                     device       │
       │                                                  │
       │ 7. Poll: POST /api/device/token                 │
       │    { device_code }                              │
       ├─────────────────────────────────►               │
       │                                                  │
       │ 8. Still pending...                             │
       │    {error: "authorization_pending"}             │
       │◄─────────────────────────────────               │
       │                                                  │
       │ 9. Poll again (every 5 seconds)                 │
       ├─────────────────────────────────►               │
       │                                                  │
       │ 10. Success!                                    │
       │     {access_token, expires_in}                  │
       │◄─────────────────────────────────               │
       │                                                  │
       │ 11. Use token:                                  │
       │     Authorization: Bearer <token>               │
```

## API Endpoints

### 1. Request Device Code

```http
POST /api/device/code
Content-Type: application/json

{
  "clientId": "claude-code"
}
```

**Response:**
```json
{
  "deviceCode": "a1b2c3d4e5f6...",
  "userCode": "WDJB-MJHT",
  "verificationUri": "http://localhost:10000/device",
  "verificationUriComplete": "http://localhost:10000/device?code=WDJB-MJHT",
  "expiresIn": 900,
  "interval": 5
}
```

### 2. Poll for Token

```http
POST /api/device/token
Content-Type: application/json

{
  "deviceCode": "a1b2c3d4e5f6..."
}
```

**Response (Pending):**
```json
{
  "error": "authorization_pending",
  "errorDescription": "The authorization request is still pending"
}
```

**Response (Authorized):**
```json
{
  "accessToken": "xyz789...",
  "tokenType": "Bearer",
  "expiresIn": 2592000,
  "scope": "drafts:create,drafts:approve,drafts:read"
}
```

### 3. Use Access Token

```http
POST /api/drafts/from-text
Authorization: Bearer xyz789...
Content-Type: application/json

{
  "merchant": "Starbucks",
  "amount": 75000,
  "transactionDate": "2026-02-11",
  ...
}
```

## Database Schema

### device_codes (temporary)

| Column | Type | Description |
|--------|------|-------------|
| id | UUID | Primary key |
| device_code | VARCHAR(100) | Long random string for polling |
| user_code | VARCHAR(10) | Short code (e.g., "WDJB-MJHT") |
| verification_uri | VARCHAR(255) | URL for browser |
| client_id | VARCHAR(50) | Client identifier |
| status | VARCHAR(20) | PENDING, AUTHORIZED, EXPIRED, DENIED |
| id_user | UUID | User who authorized (NULL until authorized) |
| created_at | TIMESTAMP | Creation time |
| expires_at | TIMESTAMP | Expiry time (15 minutes) |
| authorized_at | TIMESTAMP | Authorization time |

### device_tokens (long-lived)

| Column | Type | Description |
|--------|------|-------------|
| id | UUID | Primary key |
| id_user | UUID | Token owner |
| token_hash | VARCHAR(255) | BCrypt hash of token |
| device_name | VARCHAR(100) | User-friendly name |
| client_id | VARCHAR(50) | Client identifier |
| scopes | VARCHAR(255) | Permissions (comma-separated) |
| last_used_at | TIMESTAMP | Last usage time |
| last_used_ip | VARCHAR(45) | Last IP address |
| created_at | TIMESTAMP | Creation time |
| expires_at | TIMESTAMP | Expiry time (30 days, NULL = never) |
| revoked_at | TIMESTAMP | Revocation time |
| revoked_by | VARCHAR(100) | Who revoked it |

## Example: Claude Code Integration

```javascript
// MCP server authentication
async function authenticateWithDeviceFlow() {
    const API_BASE = 'http://localhost:10000';

    // Step 1: Request device code
    const codeResponse = await fetch(`${API_BASE}/api/device/code`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ clientId: 'claude-code' })
    });

    const { deviceCode, userCode, verificationUriComplete, interval } =
        await codeResponse.json();

    // Step 2: Show user the code
    console.log(`\nPlease visit: ${verificationUriComplete}`);
    console.log(`Or go to ${API_BASE}/device and enter code: ${userCode}\n`);

    // Step 3: Poll for token
    while (true) {
        await new Promise(resolve => setTimeout(resolve, interval * 1000));

        const tokenResponse = await fetch(`${API_BASE}/api/device/token`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ deviceCode })
        });

        if (tokenResponse.ok) {
            const { accessToken } = await tokenResponse.json();

            // Save token
            fs.writeFileSync('~/.claude/accounting-token', accessToken);
            console.log('Authentication successful!');
            return accessToken;
        }

        const error = await tokenResponse.json();
        if (error.error !== 'authorization_pending') {
            throw new Error(error.errorDescription);
        }
    }
}

// Use token in API calls
async function createDraft(accessToken, data) {
    const response = await fetch('http://localhost:10000/api/drafts/from-text', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${accessToken}`
        },
        body: JSON.stringify(data)
    });

    return await response.json();
}
```

## Security Features

### Token Storage
- ✅ Tokens stored as BCrypt hashes (never plaintext)
- ✅ Plaintext token shown only once during creation
- ✅ Cannot retrieve plaintext later

### Token Validation
- ✅ Validates token hash on each request
- ✅ Checks expiration
- ✅ Checks revocation status
- ✅ Updates last used timestamp and IP

### Cleanup
- ✅ Expired device codes cleaned up hourly
- ✅ Old codes (>24h) deleted automatically
- ✅ Scheduled task runs via `@Scheduled`

### Audit Trail
- ✅ Token creation logged
- ✅ Token usage tracked (timestamp, IP)
- ✅ Revocation logged
- ✅ Links to user account

## User Management

Users can manage their device tokens via Settings page (future):

- View all authorized devices
- See last used time and IP
- Revoke individual devices
- Set device-specific names

## Configuration

```properties
# Device authentication
device.auth.code-expiry-minutes=15      # Device code expiry
device.auth.token-expiry-days=30        # Access token expiry

# Server
server.port=10000                       # Must match verification URI
```

## Testing

```bash
# Test device flow manually
curl -X POST http://localhost:10000/api/device/code \
  -H "Content-Type: application/json" \
  -d '{"clientId":"test-client"}'

# Visit the verification URI in browser
open "http://localhost:10000/device?code=XXXX-XXXX"

# Poll for token
curl -X POST http://localhost:10000/api/device/token \
  -H "Content-Type: application/json" \
  -d '{"deviceCode":"..."}'

# Use token
curl -X POST http://localhost:10000/api/drafts/from-text \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{...}'
```

## Error Codes

| Error | Description |
|-------|-------------|
| `authorization_pending` | User hasn't authorized yet (keep polling) |
| `expired_token` | Device code expired (15 minutes) |
| `access_denied` | User denied authorization |
| `invalid_request` | Invalid device code |

## Differences from OAuth 2.0 Spec

This implementation follows RFC 8628 with these simplifications:

- No refresh tokens (access tokens are long-lived: 30 days)
- No client secrets (local development only)
- Fixed scopes (no scope selection)
- Simple cleanup (no persistent token registry)

## Future Enhancements

1. **Token Management UI**: Settings page to view/revoke devices
2. **Scope Selection**: Let users choose permissions
3. **Refresh Tokens**: Short-lived access tokens + refresh tokens
4. **Rate Limiting**: Prevent polling abuse
5. **Webhooks**: Notify device when authorized (instead of polling)
6. **2FA**: Require 2FA for device authorization
