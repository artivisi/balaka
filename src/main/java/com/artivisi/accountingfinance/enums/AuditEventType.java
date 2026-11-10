package com.artivisi.accountingfinance.enums;

/**
 * Types of security audit events.
 */
public enum AuditEventType {
    // Authentication events
    LOGIN_SUCCESS,
    LOGIN_FAILURE,
    LOGOUT,
    ACCOUNT_LOCKED,

    // Multi-factor authentication
    MFA_ENABLED,
    MFA_ENABLE_FAILED,
    MFA_DISABLED,
    MFA_VERIFY_SUCCESS,
    MFA_VERIFY_FAILED,
    MFA_LOCKED,
    MFA_RECOVERY_USED,
    MFA_RECOVERY_CODES_REGENERATED,
    MFA_ADMIN_RESET,

    // User management
    USER_CREATE,
    USER_CREATED,
    USER_UPDATE,
    USER_UPDATED,
    USER_DELETE,
    USER_DELETED,
    USER_ROLE_CHANGE,
    USER_STATUS_CHANGED,
    PASSWORD_CHANGE,
    PASSWORD_CHANGED,

    // Data access
    DATA_EXPORT,
    DATA_IMPORT,
    REPORT_GENERATE,
    BACKUP_CREATE,

    // Document operations
    DOCUMENT_UPLOAD,
    DOCUMENT_DOWNLOAD,
    DOCUMENT_DELETE,

    // Settings
    SETTINGS_CHANGE,

    // Payroll (sensitive data)
    PAYROLL_VIEW,
    PAYROLL_EXPORT,

    // API operations
    API_CALL
}
