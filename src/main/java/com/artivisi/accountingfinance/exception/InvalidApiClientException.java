package com.artivisi.accountingfinance.exception;

/**
 * Thrown when client_credentials authentication fails (unknown client,
 * inactive client, or wrong secret). Deliberately carries no detail about
 * which part failed; the token endpoint maps it to RFC 6749 invalid_client.
 */
public class InvalidApiClientException extends RuntimeException {

    public InvalidApiClientException() {
        super("invalid_client");
    }
}
