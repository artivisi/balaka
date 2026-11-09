package com.artivisi.accountingfinance.service.event;

import com.artivisi.accountingfinance.entity.Transaction;

/** Published synchronously inside the posting transaction; a listener failure rolls the posting back. */
public record TransactionPostedEvent(Transaction transaction) {}
