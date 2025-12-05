# Penetration Testing Checklist

This document provides a checklist for manual security testing of the application. Use this alongside automated security testing (SAST, DAST, SCA).

## Pre-Test Setup

- [ ] Set up test environment (not production)
- [ ] Create test accounts for each role (Admin, Owner, Accountant, Staff, Auditor, Employee)
- [ ] Prepare intercepting proxy (Burp Suite, OWASP ZAP)
- [ ] Review application architecture and data flow

## 1. Authentication Testing

### 1.1 Login Functionality
| Test | Expected Result | Status |
|------|-----------------|--------|
| Valid credentials login | Redirect to dashboard | |
| Invalid username | Generic error "Username atau password salah" | |
| Invalid password | Generic error (same as above) | |
| Empty credentials | Validation error | |
| SQL injection in username (`admin' OR '1'='1`) | Rejected, no bypass | |
| SQL injection in password | Rejected, no bypass | |
| XSS in username (`<script>alert(1)</script>`) | Escaped, no execution | |

### 1.2 Account Lockout
| Test | Expected Result | Status |
|------|-----------------|--------|
| 5 failed login attempts | Account locked for 30 minutes | |
| 6th attempt with correct password | Still locked | |
| Login after lockout expires | Successful | |
| Different IPs, same username | Lockout triggered (per-user, not per-IP) | |

### 1.3 Password Policy
| Test | Expected Result | Status |
|------|-----------------|--------|
| Password < 12 chars | Rejected | |
| Password without uppercase | Rejected | |
| Password without lowercase | Rejected | |
| Password without digit | Rejected | |
| Password without special char | Rejected | |
| Valid complex password | Accepted | |

### 1.4 Session Management
| Test | Expected Result | Status |
|------|-----------------|--------|
| Session cookie has HttpOnly flag | Yes | |
| Session cookie has Secure flag | Yes (HTTPS) | |
| Session cookie has SameSite=Strict | Yes | |
| Session timeout after 15 min idle | Yes | |
| Session invalidated on logout | Yes | |
| Session ID changes after login | Yes (session fixation protection) | |
| Reuse old session after logout | Rejected | |

## 2. Authorization Testing

### 2.1 Role-Based Access Control
| Test | Expected Result | Status |
|------|-----------------|--------|
| Staff accessing /settings/users | 403 Forbidden | |
| Employee accessing /payroll | 403 Forbidden | |
| Auditor modifying transactions | 403 Forbidden | |
| Admin accessing all features | Allowed | |
| Horizontal privilege (user A accessing user B data) | 403 Forbidden | |

### 2.2 Direct Object Reference (IDOR)
| Test | Expected Result | Status |
|------|-----------------|--------|
| Access /employees/{other-id} as Employee role | 403 or own data only | |
| Modify /transactions/{other-id} without permission | 403 Forbidden | |
| Download /documents/{other-id} without permission | 403 Forbidden | |
| Access /payroll/{other-id}/payslip as wrong employee | 403 Forbidden | |

### 2.3 Function-Level Access
| Test | Expected Result | Status |
|------|-----------------|--------|
| POST to admin endpoints as non-admin | 403 Forbidden | |
| DELETE operations without permission | 403 Forbidden | |
| Data export without permission | 403 Forbidden | |

## 3. Input Validation

### 3.1 SQL Injection
| Test | Expected Result | Status |
|------|-----------------|--------|
| Search field: `' OR '1'='1` | No data leak | |
| ID parameter: `1; DROP TABLE users--` | Rejected | |
| Sort parameter: `name; DELETE FROM users` | Rejected | |
| Filter parameter injection | Rejected | |

### 3.2 Cross-Site Scripting (XSS)
| Test | Expected Result | Status |
|------|-----------------|--------|
| Stored XSS in name field | Escaped on display | |
| Reflected XSS in search | Escaped | |
| XSS in error messages | Escaped | |
| XSS in file names | Escaped | |
| DOM-based XSS | No vulnerable sinks | |

### 3.3 File Upload
| Test | Expected Result | Status |
|------|-----------------|--------|
| Upload .exe as .pdf | Rejected (magic byte validation) | |
| Upload oversized file (>10MB) | Rejected | |
| Upload with path traversal name (`../../../etc/passwd`) | Rejected | |
| Upload HTML file | Rejected or served as download | |
| Upload SVG with embedded script | Rejected or sanitized | |

### 3.4 Other Injection
| Test | Expected Result | Status |
|------|-----------------|--------|
| LDAP injection (if applicable) | Rejected | |
| Command injection in filename | Rejected | |
| Template injection in user input | Escaped | |
| Log injection with newlines | Sanitized | |

## 4. Security Headers

| Header | Expected Value | Status |
|--------|----------------|--------|
| Content-Security-Policy | `default-src 'self'...` | |
| X-Frame-Options | DENY | |
| X-Content-Type-Options | nosniff | |
| Strict-Transport-Security | max-age=63072000 | |
| X-XSS-Protection | 1; mode=block | |
| Referrer-Policy | strict-origin-when-cross-origin | |

## 5. CSRF Protection

| Test | Expected Result | Status |
|------|-----------------|--------|
| CSRF token present in forms | Yes | |
| POST without CSRF token | 403 Forbidden | |
| Reuse CSRF token from another session | Rejected | |
| CSRF token in AJAX requests (HTMX) | Included via header | |

## 6. Data Protection

### 6.1 Sensitive Data Exposure
| Test | Expected Result | Status |
|------|-----------------|--------|
| PII visible in page source | Masked (last 4 digits only) | |
| Passwords in form values | Hidden, not echoed | |
| Sensitive data in URL parameters | Not exposed | |
| Sensitive data in error messages | Not exposed | |
| Sensitive data in logs | Masked | |

### 6.2 Encryption
| Test | Expected Result | Status |
|------|-----------------|--------|
| Database PII fields encrypted | Yes (AES-256-GCM) | |
| Uploaded documents encrypted | Yes | |
| Backup files encrypted | Yes (GPG) | |
| TLS version | 1.2 or 1.3 only | |

## 7. Business Logic

### 7.1 Financial Transactions
| Test | Expected Result | Status |
|------|-----------------|--------|
| Modify posted journal entry | Rejected | |
| Delete posted transaction | Only void allowed | |
| Negative amounts where not allowed | Rejected | |
| Journal entry debit ≠ credit | Rejected | |

### 7.2 Data Integrity
| Test | Expected Result | Status |
|------|-----------------|--------|
| Modify voided transactions | Rejected | |
| Access data from closed fiscal period | Read-only | |
| Bypass fiscal period lock | Not possible | |

## 8. Error Handling

| Test | Expected Result | Status |
|------|-----------------|--------|
| 404 page leaks information | No (generic error) | |
| 500 error shows stack trace | No (generic error) | |
| Database error exposes query | No | |
| Path traversal error shows paths | No | |

## 9. Rate Limiting

| Test | Expected Result | Status |
|------|-----------------|--------|
| Rapid login attempts | Rate limited after 10/min | |
| Bulk API requests | Rate limited | |
| Password reset spam | Rate limited | |

## 10. Audit & Logging

| Test | Expected Result | Status |
|------|-----------------|--------|
| Failed logins logged | Yes, with IP | |
| Successful logins logged | Yes | |
| Data exports logged | Yes | |
| User modifications logged | Yes | |
| Logs tamper-proof | Append-only, rotated | |

## Post-Test

- [ ] Document all findings with severity (CVSS)
- [ ] Provide proof-of-concept for vulnerabilities
- [ ] Recommend remediation steps
- [ ] Re-test after fixes applied
- [ ] Update this checklist with new test cases

## Severity Classification

| Severity | CVSS Score | Examples |
|----------|------------|----------|
| Critical | 9.0 - 10.0 | RCE, SQL injection with data access, auth bypass |
| High | 7.0 - 8.9 | Stored XSS, IDOR with sensitive data, privilege escalation |
| Medium | 4.0 - 6.9 | Reflected XSS, CSRF, information disclosure |
| Low | 0.1 - 3.9 | Missing headers, verbose errors |
| Info | 0.0 | Best practice recommendations |

---

Last updated: December 2025
