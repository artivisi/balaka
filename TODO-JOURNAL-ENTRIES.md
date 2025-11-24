# TODO: Journal Entries (1.2)

Core double-entry bookkeeping engine. Item is only checked when verified by Playwright functional test.

## Purpose

- Accountants can record journal entries directly
- Core service reused by Reports (1.3), Templates (1.4), Transactions (1.5)

## Dependencies

- COA (1.1) ✅ Complete

---

## Current State

**Existing code:**
- Entity: `JournalEntry` - flat structure (each line is a record, linked to Transaction)
- Migration: `V005__create_journal_entries.sql` - table exists
- Repository: `JournalEntryRepository` - read queries only
- Service: `JournalEntryService` - read operations, GeneralLedger calculation
- Controller: `JournalEntryController` - read-only views
- Templates: `journals/list.html`, `journals/detail.html` - **PLACEHOLDER with hardcoded data**

**Design Decision Needed:**
Current design ties journal entries to Transactions. Plan suggests standalone entries.
Recommend: Keep current design but add manual entry support for adjustments.

---

## TODO List

### 1. Entity & Database

- [x] Journal entry entity exists
- [x] Migration V005 exists
- [x] Repository with read queries
- [ ] Add status field to journal_entries (DRAFT, POSTED, VOID)
- [ ] Add posted_at, voided_at, void_reason fields
- [ ] Make id_transaction nullable (for manual entries)
- [ ] Migration V007 for schema changes

### 2. Service Layer

- [x] JournalEntryService.findById() - exists
- [x] JournalEntryService.findByTransactionId() - exists
- [x] JournalEntryService.getGeneralLedger() - exists
- [ ] JournalEntryService.create() - create manual entry
- [ ] JournalEntryService.update() - update draft entry only
- [ ] JournalEntryService.post() - validate and post entry
- [ ] JournalEntryService.void() - void posted entry with reason
- [ ] JournalEntryService.validateBalance() - debit must equal credit
- [ ] Immutability enforcement - posted entries cannot be edited

### 3. Journal Entry List UI (Buku Besar)

- [x] Display page title "Buku Besar"
- [x] Controller endpoint exists (/journals)
- [x] Template exists (journals/list.html)
- [ ] **Replace hardcoded data with dynamic data**
- [ ] Account filter dropdown (dynamic from DB)
- [ ] Date range filter (functional)
- [ ] Search filter (functional)
- [ ] Display actual journal entries from DB
- [ ] Show running balance per account
- [ ] Pagination (functional)
- [ ] Click row navigates to detail

### 4. Create Journal Entry

- [ ] Navigate to create form via "Tambah Jurnal" button
- [ ] Display form header fields (date, reference, description)
- [ ] Display journal lines table (dynamic rows)
- [ ] Add line button
- [ ] Remove line button
- [ ] Account dropdown (active accounts only)
- [ ] Debit input field
- [ ] Credit input field
- [ ] Line description field
- [ ] Display running total (debit/credit)
- [ ] Display balance indicator (balanced/unbalanced)
- [ ] Save as draft button
- [ ] Save and post button
- [ ] Validation: at least 2 lines required
- [ ] Validation: debit = credit before posting
- [ ] Validation: date is required
- [ ] Success message after save

### 5. Edit Journal Entry

- [ ] Navigate to edit form via edit button (draft only)
- [ ] Form displays existing data
- [ ] Can modify all fields
- [ ] Save updates entry
- [ ] Cannot edit posted entries (show read-only view)
- [ ] Cannot edit voided entries (show read-only view)

### 6. View Journal Entry

- [x] Detail template exists (journals/detail.html)
- [x] Controller endpoint exists (/journals/{id})
- [ ] **Replace hardcoded data with dynamic data**
- [ ] Detail view shows header fields from DB
- [ ] Detail view shows all lines with accounts
- [ ] Shows debit/credit totals (calculated)
- [ ] Shows status with timestamp
- [ ] Post button visible on draft entries
- [ ] Void button visible on posted entries
- [ ] Edit button visible on draft entries only
- [ ] Account impact section (before/after balances)

### 7. Post Journal Entry

- [ ] Post button on draft entry
- [ ] Validation: debit must equal credit
- [ ] Confirmation dialog before posting
- [ ] Status changes to POSTED
- [ ] posted_at timestamp set
- [ ] Entry becomes immutable
- [ ] Success message displayed

### 8. Void Journal Entry

- [ ] Void button on posted entry
- [ ] Void reason input required
- [ ] Confirmation dialog with reason
- [ ] Status changes to VOID
- [ ] voided_at timestamp set
- [ ] void_reason stored
- [ ] Original entry preserved (audit trail)
- [ ] Success message displayed

### 9. Account Validation (from COA)

- [ ] Account dropdown excludes inactive accounts
- [ ] Cannot change account type if has journal entries
- [ ] Cannot delete account if has journal entries

### 10. Balance Validation

- [ ] Real-time balance check on form
- [ ] Visual indicator when balanced (green)
- [ ] Visual indicator when unbalanced (red)
- [ ] Cannot post if unbalanced
- [ ] Error message shows difference amount

---

## Test Files to Create

1. `JournalEntryListTest.java` - List display and filtering
2. `JournalEntryCreateTest.java` - Create entry scenarios
3. `JournalEntryEditTest.java` - Edit draft scenarios
4. `JournalEntryPostTest.java` - Post and validation scenarios
5. `JournalEntryVoidTest.java` - Void scenarios
6. `JournalEntryValidationTest.java` - Balance and field validation

---

## Key Service Interface

```java
@Service
public class JournalEntryService {

    JournalEntry create(JournalEntryRequest request);

    JournalEntry update(UUID id, JournalEntryRequest request);

    JournalEntry post(UUID id);

    JournalEntry void(UUID id, String reason);

    void validateBalance(List<JournalEntryLine> lines);

    List<JournalEntry> findByDateRange(LocalDate from, LocalDate to);

    List<JournalEntry> findByStatus(JournalEntryStatus status);

    Page<JournalEntry> search(JournalEntrySearchCriteria criteria, Pageable pageable);
}
```

---

## Database Schema

```sql
-- V003: Journal entries
CREATE TABLE journal_entries (
    id UUID PRIMARY KEY,
    entry_date DATE NOT NULL,
    reference_number VARCHAR(50),
    description VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    posted_at TIMESTAMP,
    voided_at TIMESTAMP,
    void_reason VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by VARCHAR(100),
    updated_by VARCHAR(100),

    CONSTRAINT chk_status CHECK (status IN ('DRAFT', 'POSTED', 'VOID'))
);

CREATE TABLE journal_entry_lines (
    id UUID PRIMARY KEY,
    journal_entry_id UUID NOT NULL REFERENCES journal_entries(id),
    account_id UUID NOT NULL REFERENCES chart_of_accounts(id),
    line_number INTEGER NOT NULL,
    debit DECIMAL(15,2) NOT NULL DEFAULT 0,
    credit DECIMAL(15,2) NOT NULL DEFAULT 0,
    description VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_debit_credit CHECK (debit >= 0 AND credit >= 0),
    CONSTRAINT chk_one_side CHECK (debit = 0 OR credit = 0)
);

CREATE INDEX idx_je_entry_date ON journal_entries(entry_date);
CREATE INDEX idx_je_status ON journal_entries(status);
CREATE INDEX idx_je_reference ON journal_entries(reference_number);
CREATE INDEX idx_jel_journal_entry ON journal_entry_lines(journal_entry_id);
CREATE INDEX idx_jel_account ON journal_entry_lines(account_id);
```

---

## Definition of Done

All items checked = Journal Entry feature complete. Each checkbox requires:
1. Feature works in UI
2. Playwright test verifies the feature
3. Test passes consistently
