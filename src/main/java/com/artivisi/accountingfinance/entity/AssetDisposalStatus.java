package com.artivisi.accountingfinance.entity;

/**
 * DRAFT: journal composed, not posted, asset unchanged but locked. POSTED: asset is DISPOSED. VOIDED: the posted
 * journal was voided and the asset restored. CANCELLED: the draft was removed before posting.
 */
public enum AssetDisposalStatus {
    DRAFT,
    POSTED,
    VOIDED,
    CANCELLED
}
