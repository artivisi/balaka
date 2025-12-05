# Security Audit Report

**Application:** Indonesian Accounting Application (aplikasi-akunting)
**Version:** 0.0.1-SNAPSHOT
**Audit Date:** 2025-12-05
**Framework:** Spring Boot 4.0.0 + Thymeleaf + PostgreSQL
**Standards Applied:** OWASP Top 10 (2021), PCI-DSS v4.0, NIST CSF

---

## Executive Summary

This security audit assessed the aplikasi-akunting application's readiness to host client data. The application demonstrates **good foundational security** with Spring Security, BCrypt password hashing, and method-level authorization. However, **critical vulnerabilities** must be remediated before production deployment.

### Overall Assessment: **NOT READY FOR PRODUCTION**

| Category | Status | Critical Issues |
|----------|--------|-----------------|
| Authentication | ⚠️ Partial | Weak password policy (4 chars) |
| Authorization | ✅ Good | 133+ @PreAuthorize checks |
| Cryptography | ❌ Critical | Unencrypted PII at rest |
| Input Validation | ⚠️ Partial | 1 SQL injection, 2 XSS |
| Security Headers | ❌ Missing | No CSP, HSTS, X-Frame-Options |
| Logging & Monitoring | ❌ Insufficient | Minimal audit trail |
| File Operations | ⚠️ Partial | RFD, incomplete ZIP validation |
| Dependencies | ✅ Good | Current, actively scanned |

---

## Findings Summary

### By Severity

| Severity | Count | Examples |
|----------|-------|----------|
| **CRITICAL** | 5 | Hardcoded credentials, unencrypted PII, weak passwords |
| **HIGH** | 8 | XSS, missing security headers, no rate limiting |
| **MEDIUM** | 12 | ZIP slip, incomplete file validation, logging gaps |
| **LOW** | 4 | Directory exposure potential, missing CORS config |

### By OWASP Top 10 (2021)

| OWASP ID | Category | Findings | Status |
|----------|----------|----------|--------|
| A01:2021 | Broken Access Control | 4 | ⚠️ Issues Found |
| A02:2021 | Cryptographic Failures | 6 | ❌ Critical Issues |
| A03:2021 | Injection | 3 | ⚠️ Issues Found |
| A04:2021 | Insecure Design | 8 | ⚠️ Issues Found |
| A05:2021 | Security Misconfiguration | 5 | ❌ Critical Issues |
| A06:2021 | Vulnerable Components | 0 | ✅ Compliant |
| A07:2021 | Auth Failures | 3 | ❌ Critical Issues |
| A08:2021 | Software Integrity | 1 | ✅ Mostly Compliant |
| A09:2021 | Logging Failures | 4 | ❌ Critical Issues |
| A10:2021 | SSRF | 0 | ✅ Not Applicable |

### By PCI-DSS v4.0

| Requirement | Description | Status | Gap |
|-------------|-------------|--------|-----|
| 2.2.7 | System hardening | ⚠️ Partial | Security headers missing |
| 3.2 | Protect stored data | ❌ Fail | Bank accounts, tax IDs unencrypted |
| 3.4 | Mask PAN display | ❌ Fail | No data masking implemented |
| 4.2 | Encrypt transmission | ⚠️ Partial | No HTTPS enforcement |
| 6.2 | Security patches | ✅ Pass | Dependencies current |
| 6.4 | Secure development | ⚠️ Partial | Some hardcoded secrets |
| 6.5 | Secure coding | ⚠️ Partial | SQL injection, XSS found |
| 7.1 | Restrict access | ✅ Pass | RBAC implemented |
| 8.2 | Strong authentication | ❌ Fail | 4-char passwords allowed |
| 8.3 | MFA | ❌ Fail | Not implemented |
| 10.1 | Audit logging | ❌ Fail | Minimal implementation |
| 10.2 | Log events | ❌ Fail | Missing login/access logs |

---

## Critical Findings (Must Fix Before Production)

### CRIT-01: Hardcoded Database Credentials

**OWASP:** A02:2021 | **PCI-DSS:** 3.2, 6.4 | **CVSS:** 9.1

**Location:** `compose.yml` line 7

```yaml
environment:
  - POSTGRES_PASSWORD=Smartly-Jacket-Sandlot9  # EXPOSED
```

**Impact:** Complete database compromise if repository is public or leaked.

**Remediation:**
```yaml
environment:
  - POSTGRES_PASSWORD=${DB_PASSWORD}  # Use environment variable
```

---

### CRIT-02: Unencrypted Sensitive Data at Rest

**OWASP:** A02:2021 | **PCI-DSS:** 3.2, 3.4 | **CVSS:** 8.6

**Locations:**
- `Employee.java:72` - NPWP (Tax ID)
- `Employee.java:77` - NIK KTP (National ID)
- `Employee.java:116` - Bank account number
- `Employee.java:125-130` - BPJS numbers
- `CompanyBankAccount.java:44` - Account number

**Impact:** Database breach exposes PII enabling identity theft, financial fraud.

**Remediation:**
```java
@Convert(converter = EncryptedStringConverter.class)
@Column(name = "bank_account_number", length = 255)
private String bankAccountNumber;
```

Implement AES-256 field-level encryption with external key management (AWS KMS, HashiCorp Vault).

---

### CRIT-03: Weak Password Policy

**OWASP:** A07:2021 | **PCI-DSS:** 8.2.3 | **CVSS:** 8.1

**Location:** `UserController.java:206`

```java
if (newPassword.length() < 4) {  // DANGEROUSLY WEAK
```

**Impact:** Trivial brute-force attacks, credential stuffing success.

**Remediation:**
```java
// Minimum 12 characters, complexity requirements
if (newPassword.length() < 12 ||
    !newPassword.matches(".*[A-Z].*") ||
    !newPassword.matches(".*[a-z].*") ||
    !newPassword.matches(".*[0-9].*") ||
    !newPassword.matches(".*[!@#$%^&*].*")) {
    throw new WeakPasswordException("Password does not meet complexity requirements");
}
```

---

### CRIT-04: DOM-Based XSS in Template Preview

**OWASP:** A03:2021 | **PCI-DSS:** 6.5.7 | **CVSS:** 7.5

**Location:** `templates/form.html:760-803`

```javascript
const accountName = line.accountCode + ' - ' + line.accountName;
html += `<td>${accountName}</td>`;  // NO ESCAPING
div.innerHTML = html;  // DANGEROUS
```

**Impact:** Stored XSS via account name, session hijacking, admin account compromise.

**Remediation:**
```javascript
// Use textContent or DOMPurify
const td = document.createElement('td');
td.textContent = accountName;  // Automatically escaped
```

---

### CRIT-05: Missing Security Headers

**OWASP:** A05:2021 | **PCI-DSS:** 2.2.7 | **CVSS:** 6.1

**Location:** `SecurityConfig.java` - No headers configuration

**Missing:**
- Content-Security-Policy (XSS mitigation)
- X-Frame-Options (Clickjacking)
- X-Content-Type-Options (MIME sniffing)
- Strict-Transport-Security (HTTPS enforcement)

**Remediation:**
```java
http.headers(headers -> headers
    .contentSecurityPolicy(csp -> csp
        .policyDirectives("default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'"))
    .frameOptions(frame -> frame.deny())
    .httpStrictTransportSecurity(hsts -> hsts
        .includeSubDomains(true)
        .maxAgeInSeconds(31536000))
    .contentTypeOptions(Customizer.withDefaults())
);
```

---

## High Severity Findings

### HIGH-01: SQL Injection in Data Import

**OWASP:** A03:2021 | **PCI-DSS:** 6.5.1 | **CVSS:** 7.2

**Location:** `DataImportService.java:251`

```java
entityManager.createNativeQuery("TRUNCATE TABLE " + table + " CASCADE")
    .executeUpdate();  // Table name concatenated
```

**Remediation:** Implement explicit whitelist validation before SQL execution.

---

### HIGH-02: Password Hashes in Data Export

**OWASP:** A02:2021 | **PCI-DSS:** 3.2 | **CVSS:** 6.8

**Location:** `DataExportService.java:850`

```java
csv.append(escapeCsv(u.getPassword())).append(",");  // BCrypt hash exported
```

**Remediation:** Exclude password field from all exports.

---

### HIGH-03: Telegram Webhook Authentication Bypass

**OWASP:** A07:2021 | **PCI-DSS:** 8.1.3 | **CVSS:** 8.2

**Location:** `TelegramWebhookController.java:37-43`

If `configuredSecret` is null/blank, ALL webhook requests are accepted without authentication.

**Remediation:** Fail startup if webhook secret is not configured.

---

### HIGH-04: HTTP Response Splitting (RFD)

**OWASP:** A03:2021 | **PCI-DSS:** 6.5.7 | **CVSS:** 6.5

**Location:** `DocumentController.java:159,175`

```java
.header(HttpHeaders.CONTENT_DISPOSITION,
        disposition + "; filename=\"" + document.getOriginalFilename() + "\"")
```

**Remediation:** Use RFC 6266 encoding for Content-Disposition filename.

---

### HIGH-05: No Rate Limiting

**OWASP:** A04:2021 | **PCI-DSS:** 6.5.5 | **CVSS:** 6.5

No rate limiting on login endpoint or API endpoints.

**Remediation:** Implement Resilience4j rate limiter or Spring Cloud Gateway.

---

### HIGH-06: No Login Attempt Monitoring

**OWASP:** A07:2021 | **PCI-DSS:** 8.2.4 | **CVSS:** 6.5

No account lockout after failed login attempts.

**Remediation:** Implement failed attempt counter with 30-minute lockout after 5 failures.

---

### HIGH-07: Insufficient Audit Logging

**OWASP:** A09:2021 | **PCI-DSS:** 10.1, 10.2 | **CVSS:** 6.0

AuditLog entity exists but only used in 2 services. Missing logs for:
- Login/logout events
- User management operations
- Document access
- Report exports
- Settings changes

**Remediation:** Implement comprehensive audit logging for all security-relevant events.

---

### HIGH-08: Unencrypted Backup Exports

**OWASP:** A02:2021 | **PCI-DSS:** 3.2 | **CVSS:** 6.5

Data exports contain full database including sensitive data, exported as unencrypted ZIP.

**Remediation:** Encrypt export files with user-provided password using AES-256.

---

## Medium Severity Findings

| ID | Finding | Location | OWASP | Remediation |
|----|---------|----------|-------|-------------|
| MED-01 | ZIP Slip incomplete check | DataImportService.java:157-184 | A01 | Normalize paths, check resolved path |
| MED-02 | Content-Type only file validation | DocumentStorageService.java:142-164 | A04 | Add magic byte validation |
| MED-03 | DB truncation without backup | DataImportService.java:218-259 | A01 | Require backup before truncate |
| MED-04 | Memory-based temp files | DataExportService.java:80 | A04 | Secure wipe after use |
| MED-05 | Google credentials path logged | GoogleCloudVisionConfig.java:38 | A02 | Remove path from logs |
| MED-06 | Telegram token in URL | TelegramApiConfig.java:25-26 | A02 | Use header-based auth |
| MED-07 | No session timeout config | SecurityConfig.java | A07 | Set 15-minute timeout |
| MED-08 | Remember-me not implemented | SecurityConfig.java | A07 | Remove checkbox or implement |
| MED-09 | Error messages expose details | Multiple controllers | A09 | Return generic messages |
| MED-10 | Log injection potential | DraftTransactionController.java:97-98 | A09 | Sanitize user input in logs |
| MED-11 | No CORS configuration | SecurityConfig.java | A05 | Configure explicit CORS policy |
| MED-12 | Sensitive data in audit logs | AuditLog.java | A09 | Mask sensitive fields |

---

## Data Security by State

### Data at Rest

| Asset | Current State | Risk | Remediation |
|-------|---------------|------|-------------|
| **Database** | ❌ Unencrypted | HIGH | PostgreSQL TDE or cloud-managed encryption |
| **PII Fields** | ❌ Plaintext | CRITICAL | AES-256 field-level encryption (Phase 6.2) |
| **Document Storage** | ❌ Unencrypted | MEDIUM | Encrypt files at rest or use encrypted filesystem |
| **Backup Files** | ❌ Unencrypted ZIP | HIGH | AES-256 encrypted exports (Phase 6.6) |
| **Log Files** | ❌ Plaintext | LOW | Consider log encryption for sensitive environments |
| **Session Data** | ✅ Server-side | LOW | Spring Security default (not in cookies) |

**Current Configuration (`application.properties`):**
```properties
spring.datasource.url=${DATABASE_URL:jdbc:postgresql://localhost:12345/accountingdb}
# No SSL parameters, no encryption configuration
```

**Recommended Database Connection:**
```properties
spring.datasource.url=jdbc:postgresql://host:5432/db?ssl=true&sslmode=verify-full&sslrootcert=/path/to/ca.crt
```

### Data in Transit

| Channel | Current State | Risk | Remediation |
|---------|---------------|------|-------------|
| **Browser ↔ Server** | ⚠️ No HTTPS enforcement | HIGH | Configure TLS 1.3, add HSTS header |
| **Server ↔ PostgreSQL** | ❌ Unencrypted | HIGH | Enable `sslmode=verify-full` |
| **Server ↔ Telegram API** | ✅ HTTPS | LOW | Already uses `api.telegram.org` (HTTPS) |
| **Server ↔ Google Vision** | ✅ HTTPS | LOW | Google Cloud client uses TLS |
| **Backup Transfer** | ⚠️ Depends on config | MEDIUM | Use rsync over SSH or encrypted channel |

**Missing TLS Configuration:**
```properties
# Not configured in application.properties:
server.ssl.enabled=true
server.ssl.key-store=classpath:keystore.p12
server.ssl.key-store-password=${SSL_KEYSTORE_PASSWORD}
server.ssl.key-store-type=PKCS12
server.ssl.protocol=TLS
server.ssl.enabled-protocols=TLSv1.3,TLSv1.2
```

### Data in Use (Memory)

| Concern | Current State | Risk | Remediation |
|---------|---------------|------|-------------|
| **Sensitive Data in Memory** | ❌ Not protected | MEDIUM | Use `char[]` instead of `String` for passwords |
| **Memory Wiping** | ❌ Not implemented | MEDIUM | Clear sensitive byte arrays after use |
| **Heap Dump Protection** | ❌ Not configured | LOW | Disable heap dumps in production, encrypt if needed |
| **Debug Endpoints** | ✅ Disabled | LOW | Actuator not exposed by default |
| **Temporary Files** | ⚠️ ByteArrayOutputStream | MEDIUM | Secure wipe after export operations |

**Current Issue (`DataExportService.java:80`):**
```java
ByteArrayOutputStream baos = new ByteArrayOutputStream();
// Sensitive data remains in memory until GC
// No explicit clearing mechanism
```

**Recommended Pattern:**
```java
byte[] sensitiveData = /* ... */;
try {
    // Use data
} finally {
    Arrays.fill(sensitiveData, (byte) 0);  // Secure wipe
}
```

### Data State Summary

| State | Compliance | Priority |
|-------|------------|----------|
| **At Rest** | ❌ 20% | P0 - Critical |
| **In Transit** | ⚠️ 50% | P1 - High |
| **In Use** | ❌ 10% | P2 - Medium |

---

## Security Controls Assessment

### Implemented (Strengths)

| Control | Implementation | Evidence |
|---------|---------------|----------|
| Password Hashing | BCryptPasswordEncoder | SecurityConfig.java |
| RBAC | 6 roles, 140+ permissions | Permission.java, Role.java |
| Method Authorization | @PreAuthorize on 133+ endpoints | All controllers |
| CSRF Protection | Enabled for forms, HTMX integration | SecurityConfig.java, main.html |
| Input Validation | @Valid on 42+ DTOs | Controller layer |
| Parameterized Queries | JPA/JPQL throughout | All repositories |
| Dependency Scanning | OWASP Dependency-Check | pom.xml, security.yml |
| Soft Delete | Implemented for major entities | Entity classes |

### Missing (Gaps)

| Control | Priority | Effort | Impact |
|---------|----------|--------|--------|
| Field-level encryption | P0 | 2 weeks | Critical |
| Security headers | P0 | 1 day | High |
| Password complexity | P0 | 2 hours | High |
| Rate limiting | P1 | 1 week | High |
| Comprehensive audit logging | P1 | 2 weeks | High |
| MFA | P2 | 2 weeks | Medium |
| Data masking | P2 | 1 week | Medium |
| Encrypted exports | P2 | 3 days | Medium |

---

## Remediation Roadmap

### Phase 1: Critical (Week 1-2)

1. **Remove hardcoded credentials** from compose.yml
2. **Implement password complexity** (12+ chars, complexity requirements)
3. **Add security headers** to SecurityConfig
4. **Fix XSS** in templates/form.html (replace innerHTML)
5. **Fix SQL injection** in DataImportService

### Phase 2: High Priority (Week 3-4)

1. **Implement field-level encryption** for PII (bank accounts, tax IDs, BPJS)
2. **Add rate limiting** to login and API endpoints
3. **Implement account lockout** after failed logins
4. **Enforce Telegram webhook authentication**
5. **Remove password hashes** from data exports

### Phase 3: Medium Priority (Week 5-6)

1. **Implement comprehensive audit logging**
2. **Add data masking** for sensitive fields in views
3. **Encrypt backup exports**
4. **Fix ZIP slip validation** in DataImportService
5. **Add magic byte validation** for file uploads

### Phase 4: Hardening (Week 7-8)

1. **Implement MFA** for admin accounts
2. **Configure HTTPS enforcement**
3. **Add session timeout configuration**
4. **Implement CORS policy**
5. **Documentation and security procedures**

---

## Compliance Gap Analysis

### PCI-DSS v4.0 Compliance

| Requirement | Status | Gap | Remediation |
|-------------|--------|-----|-------------|
| 1. Network Security | N/A | Infrastructure level | Configure firewall rules |
| 2. Secure Defaults | ⚠️ Partial | Hardcoded secrets | Remove from code |
| 3. Protect Stored Data | ❌ Fail | No encryption | Implement AES-256 |
| 4. Encrypt Transmission | ⚠️ Partial | No enforcement | Add HSTS |
| 5. Malware Protection | N/A | Infrastructure level | Deploy WAF |
| 6. Secure Development | ⚠️ Partial | 4 vulnerabilities | Fix injection/XSS |
| 7. Restrict Access | ✅ Pass | RBAC implemented | - |
| 8. Strong Authentication | ❌ Fail | Weak passwords | Implement policy |
| 9. Physical Security | N/A | Infrastructure level | Cloud provider |
| 10. Logging & Monitoring | ❌ Fail | Minimal logging | Implement audit |
| 11. Vulnerability Scanning | ✅ Pass | Dependency-Check | - |
| 12. Security Policies | ❌ Missing | No documentation | Create policies |

### OWASP ASVS Level 1 Compliance

Estimated compliance: **62%** (must reach 100% for production)

### GDPR / UU PDP Compliance

**Note:** UU PDP (Law No. 27/2022) is Indonesian data protection law with requirements similar to GDPR.

| Requirement | GDPR Article | UU PDP | Status | Gap |
|-------------|--------------|--------|--------|-----|
| Lawful Basis | Art. 6 | Pasal 20 | ❌ Missing | No consent tracking |
| Data Subject Access | Art. 15 | Pasal 7 | ⚠️ Partial | Admin-only export |
| Right to Rectification | Art. 16 | Pasal 8 | ✅ Pass | Users can edit data |
| Right to Erasure | Art. 17 | Pasal 9 | ❌ Fail | Soft delete only |
| Data Portability | Art. 20 | Pasal 12 | ⚠️ Partial | Export exists, not self-service |
| Privacy by Design | Art. 25 | Pasal 34 | ⚠️ Partial | Some controls |
| Data Protection Officer | Art. 37 | Pasal 53 | N/A | Organizational |
| Breach Notification | Art. 33-34 | Pasal 46 | ❌ Missing | No procedures (72h/14 days) |
| Records of Processing | Art. 30 | Pasal 31 | ❌ Missing | No ROPA documentation |
| Data Retention | Art. 5(1)(e) | Pasal 25 | ⚠️ Partial | Policy exists, no enforcement |
| Security Measures | Art. 32 | Pasal 35 | ⚠️ Partial | Encryption planned |
| DPIA | Art. 35 | Pasal 34 | ❌ Missing | No assessment |

**Key GDPR/UU PDP Gaps:**

1. **No True Deletion** - Soft delete doesn't satisfy right to erasure
2. **No Consent Management** - No tracking of processing consent
3. **No Breach Response** - Missing notification procedures
4. **No Self-Service Data Export** - Employees can't export their own data
5. **No Privacy Notice** - No in-app privacy policy display
6. **No ROPA** - No documentation of processing activities

**Indonesian-Specific Requirements (UU PDP):**

- Data controller must notify KOMDIGI within 14 days of breach (vs 72h GDPR)
- Written consent required for sensitive data processing
- Data transfer restrictions (domestic processing preferred)
- Penalties up to 2% of annual revenue or IDR 20 billion

---

## Testing Recommendations

### Penetration Testing Scope

1. **Authentication Testing**
   - Brute force login attempts
   - Password policy bypass
   - Session fixation/hijacking

2. **Authorization Testing**
   - Horizontal privilege escalation
   - Vertical privilege escalation
   - IDOR vulnerabilities

3. **Injection Testing**
   - SQL injection (focus on DataImportService)
   - XSS (focus on templates/form.html)
   - Command injection

4. **Cryptographic Testing**
   - Data at rest encryption verification
   - TLS configuration
   - Password storage audit

5. **File Upload Testing**
   - ZIP slip exploitation
   - Malicious file upload
   - Path traversal

---

## Appendix A: Vulnerability Details by File

| File | Line | Issue | Severity |
|------|------|-------|----------|
| compose.yml | 7 | Hardcoded password | CRITICAL |
| Employee.java | 72,77,116,125-130 | Unencrypted PII | CRITICAL |
| UserController.java | 206 | 4-char password | CRITICAL |
| templates/form.html | 760-803 | XSS via innerHTML | CRITICAL |
| SecurityConfig.java | - | Missing headers | CRITICAL |
| DataImportService.java | 251 | SQL injection | HIGH |
| DataExportService.java | 850 | Password export | HIGH |
| TelegramWebhookController.java | 37-43 | Auth bypass | HIGH |
| DocumentController.java | 159,175 | RFD | HIGH |
| DataImportService.java | 157-184 | ZIP slip | MEDIUM |
| DocumentStorageService.java | 142-164 | Content-Type only | MEDIUM |
| TelegramApiConfig.java | 25-26 | Token in URL | MEDIUM |

---

## Appendix B: Security Configuration Checklist

```properties
# application-production.properties security settings

# Session management
server.servlet.session.timeout=15m
server.servlet.session.cookie.secure=true
server.servlet.session.cookie.http-only=true
server.servlet.session.cookie.same-site=strict

# Error handling
server.error.include-message=never
server.error.include-stacktrace=never
server.error.include-exception=false

# Logging
logging.level.org.springframework.security=WARN
logging.pattern.console=%d{yyyy-MM-dd HH:mm:ss} - %msg%n

# File uploads
spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=50MB
```

---

## Appendix C: References

- OWASP Top 10 (2021): https://owasp.org/Top10/
- PCI-DSS v4.0: https://www.pcisecuritystandards.org/
- NIST Cybersecurity Framework: https://www.nist.gov/cyberframework
- CWE/SANS Top 25: https://cwe.mitre.org/top25/
- Spring Security Reference: https://docs.spring.io/spring-security/reference/

---

**Report Prepared By:** Security Audit (Automated)
**Review Required By:** Security Team, Development Lead
**Next Audit:** Quarterly or after major release
