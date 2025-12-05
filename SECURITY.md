# Security Policy

## Supported Versions

| Version | Supported          |
| ------- | ------------------ |
| 1.x     | :white_check_mark: |

## Reporting a Vulnerability

We take security vulnerabilities seriously. If you discover a security issue, please report it responsibly.

### How to Report

1. **Do NOT create a public GitHub issue** for security vulnerabilities
2. Send an email to: **security@artivisi.com**
3. Include the following information:
   - Description of the vulnerability
   - Steps to reproduce
   - Potential impact
   - Any suggested fixes (optional)

### What to Expect

| Timeframe | Action |
|-----------|--------|
| 24 hours | Acknowledgment of your report |
| 72 hours | Initial assessment and severity classification |
| 7 days | Status update on remediation plan |
| 30 days | Target for fix release (critical/high severity) |
| 90 days | Target for fix release (medium/low severity) |

### Severity Classification

We use CVSS v3.1 for severity scoring:

| Severity | CVSS Score | Response Time |
|----------|------------|---------------|
| Critical | 9.0 - 10.0 | 24-48 hours |
| High | 7.0 - 8.9 | 7 days |
| Medium | 4.0 - 6.9 | 30 days |
| Low | 0.1 - 3.9 | 90 days |

### Safe Harbor

We consider security research conducted in accordance with this policy to be:
- Authorized concerning any applicable anti-hacking laws
- Exempt from restrictions in our Terms of Service that would interfere with security research

We will not pursue legal action against researchers who:
- Act in good faith
- Avoid privacy violations, data destruction, and service disruption
- Report vulnerabilities promptly
- Give us reasonable time to address the issue before public disclosure

## Security Measures

This application implements the following security controls:

### Authentication & Authorization
- Password complexity requirements (12+ characters, mixed case, numbers, symbols)
- Account lockout after 5 failed attempts (30-minute duration)
- Session timeout after 15 minutes of inactivity
- Role-based access control (RBAC)

### Data Protection
- AES-256-GCM encryption for sensitive data at rest (PII fields)
- TLS 1.2/1.3 for data in transit
- Secure cookie flags (HttpOnly, Secure, SameSite=Strict)

### Security Headers
- Content-Security-Policy
- X-Frame-Options: DENY
- X-Content-Type-Options: nosniff
- Strict-Transport-Security (HSTS)

### Audit & Monitoring
- Comprehensive security audit logging
- Failed login attempt tracking
- Rate limiting on authentication endpoints

### Compliance
- OWASP Top 10 (2021) mitigations
- GDPR / UU PDP (Indonesian Data Protection Law) compliance
- UU KUP Art. 28 (10-year financial record retention)

## Security Testing

We perform the following security testing:
- Static Application Security Testing (SAST) via CodeQL and SonarCloud
- Software Composition Analysis (SCA) via OWASP Dependency-Check
- Dynamic Application Security Testing (DAST) via OWASP ZAP
- Secret detection via GitLeaks
- Security regression tests via Playwright

## Acknowledgments

We appreciate the security research community's efforts in helping keep our users safe. Researchers who report valid vulnerabilities will be acknowledged here (with permission).

---

Last updated: December 2025
