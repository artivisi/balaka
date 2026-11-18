package com.artivisi.accountingfinance.enums;

/**
 * Provenance of {@code chart_of_accounts.is_cash_bank}.
 *
 * <p>Only {@link #BANK_LINK} flags are automatically cleared when the last
 * company bank account stops pointing at the GL account. Explicit user intent
 * ({@link #EXPLICIT}) and legacy/migrated flags ({@link #MIGRATED}) are never
 * cleared by the bank-account workflow.
 */
public enum CashBankSource {
    EXPLICIT,
    BANK_LINK,
    MIGRATED
}
