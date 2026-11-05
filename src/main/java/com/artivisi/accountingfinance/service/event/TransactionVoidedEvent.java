package com.artivisi.accountingfinance.service.event;

import com.artivisi.accountingfinance.entity.Transaction;

/** Published synchronously inside the void transaction; a listener failure rolls the void back. */
public record TransactionVoidedEvent(Transaction transaction) {}
