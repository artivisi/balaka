package com.artivisi.accountingfinance.exception;

/**
 * Raised when an account cannot be marked as cash/bank (or when a bank account
 * tries to link an account that is not an eligible ASSET leaf).
 */
public class InvalidCashBankAccountException extends IllegalArgumentException {

    public InvalidCashBankAccountException(String message) {
        super(message);
    }
}
