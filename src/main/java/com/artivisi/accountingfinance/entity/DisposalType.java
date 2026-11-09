package com.artivisi.accountingfinance.entity;

/**
 * Type of asset disposal.
 */
public enum DisposalType {
    /**
     * Asset sold to third party.
     */
    SOLD,

    /**
     * Asset given away. No proceeds; the book value is a non-deductible loss.
     */
    GIFTED,

    /**
     * Asset written off (scrapped, no longer usable).
     */
    WRITTEN_OFF,

    /**
     * Asset transferred to another entity.
     */
    TRANSFERRED
}
