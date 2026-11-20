# API Quickstart

Complete working example from zero to first API call using `curl`. Replace `https://balaka.example.com` with your Balaka instance URL.

## Prerequisites

- A running Balaka instance with at least one user account.
- `curl` and `jq` installed.

## 1. Obtain an Access Token

```bash
# Step 1: Request a device code
RESPONSE=$(curl -s -X POST https://balaka.example.com/api/device/code \
  -H "Content-Type: application/json" \
  -d '{"clientId": "my-script"}')

DEVICE_CODE=$(echo $RESPONSE | jq -r '.deviceCode')
USER_CODE=$(echo $RESPONSE | jq -r '.userCode')
VERIFY_URL=$(echo $RESPONSE | jq -r '.verificationUriComplete')

echo "Open this URL in your browser: $VERIFY_URL"
echo "Or go to $(echo $RESPONSE | jq -r '.verificationUri') and enter code: $USER_CODE"
```

Open the URL in your browser, log in if needed, and click "Authorize".

```bash
# Step 2: Poll for the token (run after authorizing in browser)
TOKEN_RESPONSE=$(curl -s -X POST https://balaka.example.com/api/device/token \
  -H "Content-Type: application/json" \
  -d "{\"deviceCode\": \"$DEVICE_CODE\"}")

TOKEN=$(echo $TOKEN_RESPONSE | jq -r '.accessToken')
echo "Access token: $TOKEN"
```

If you get `authorization_pending`, wait 5 seconds and try again.

## 2. Get Trial Balance

```bash
curl -s https://balaka.example.com/api/analysis/trial-balance?asOfDate=2025-12-31 \
  -H "Authorization: Bearer $TOKEN" | jq .
```

Response:

```json
{
  "reportType": "trial-balance",
  "generatedAt": "2025-12-31T10:00:00",
  "parameters": {
    "asOfDate": "2025-12-31"
  },
  "data": {
    "items": [
      {
        "accountCode": "1-1100",
        "accountName": "Kas",
        "debit": 50000000.00,
        "credit": 0.00
      }
    ],
    "totalDebit": 150000000.00,
    "totalCredit": 150000000.00
  },
  "metadata": {
    "currency": "IDR",
    "accountingBasis": "accrual",
    "description": "Trial balance as of 2025-12-31..."
  }
}
```

## 3. Create a Draft Transaction

First, find a template ID. Use the Swagger UI at `/swagger-ui.html` to browse available templates, or query the templates API.

```bash
# Create a draft transaction
curl -s -X POST https://balaka.example.com/api/drafts \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "templateId": "e0000000-0000-0000-0000-000000000001",
    "description": "Office supplies purchase",
    "amount": 500000,
    "transactionDate": "2025-12-15"
  }' | jq .
```

Response:

```json
{
  "transactionId": "a1b2c3d4-...",
  "transactionNumber": null,
  "status": "DRAFT",
  "merchant": null,
  "amount": 500000,
  "transactionDate": "2025-12-15",
  "description": "Office supplies purchase",
  "journalEntries": [
    {
      "journalNumber": null,
      "accountCode": "5-1200",
      "accountName": "Beban Perlengkapan Kantor",
      "debitAmount": 500000.00,
      "creditAmount": 0.00
    },
    {
      "journalNumber": null,
      "accountCode": "1-1100",
      "accountName": "Kas",
      "debitAmount": 0.00,
      "creditAmount": 500000.00
    }
  ]
}
```

## 4. Post the Transaction

```bash
TX_ID="a1b2c3d4-..."  # from the response above

curl -s -X POST "https://balaka.example.com/api/transactions/$TX_ID/post" \
  -H "Authorization: Bearer $TOKEN" | jq .
```

Response:

```json
{
  "transactionId": "a1b2c3d4-...",
  "transactionNumber": "JU-202512-0001",
  "status": "POSTED",
  "merchant": null,
  "amount": 500000,
  "transactionDate": "2025-12-15",
  "description": "Office supplies purchase",
  "journalEntries": [...]
}
```

## 5. Verify in Trial Balance

```bash
curl -s https://balaka.example.com/api/analysis/trial-balance?asOfDate=2025-12-31 \
  -H "Authorization: Bearer $TOKEN" | jq '.data.items[] | select(.accountCode == "5-1200")'
```

## Full API Reference

Browse the interactive API documentation at `/swagger-ui.html` on your Balaka instance (log in to the application first; anonymous visitors are sent to the login page). All endpoints under `/api/**` are documented with request/response schemas and examples.

---

Next: [Transactions](03-transactions.md)
