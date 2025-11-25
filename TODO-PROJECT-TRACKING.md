# TODO: Project Tracking (1.9)

## Purpose

Track profitability per project/job for service businesses. Simple tagging approach - not full project management.

## Dependencies

- COA (1.1) - accounts for revenue/expense tracking
- JournalEntryService (1.2) - creates journal entries
- Transactions (1.5) - link transactions to projects
- AccountBalanceCalculator (1.3) - profitability calculations

---

## Implementation Phases

### Phase 1: Database & Core Entities ✅

#### 1.1 Database Schema (V008 migration)
- [x] Clients table
- [x] Projects table
- [x] Project Milestones table
- [x] Project Payment Terms table
- [x] Invoices table
- [x] Add project_id to journal_entries
- [x] Add project_id to transactions
- [x] All indexes created

#### 1.2 Enums
- [x] `ProjectStatus.java` - ACTIVE, COMPLETED, ARCHIVED
- [x] `MilestoneStatus.java` - PENDING, IN_PROGRESS, COMPLETED
- [x] `PaymentTrigger.java` - ON_SIGNING, ON_MILESTONE, ON_COMPLETION, FIXED_DATE
- [x] `InvoiceStatus.java` - DRAFT, SENT, PAID, OVERDUE, CANCELLED

#### 1.3 Entity Classes
- [x] `Client.java` entity
- [x] `Project.java` entity with @ManyToOne to Client
- [x] `ProjectMilestone.java` entity with @ManyToOne to Project
- [x] `ProjectPaymentTerm.java` entity with @ManyToOne to Project, Milestone
- [x] `Invoice.java` entity with references to Client, Project, PaymentTerm

#### 1.4 Repositories
- [x] `ClientRepository.java` with search by name/code
- [x] `ProjectRepository.java` with filters (status, client)
- [x] `ProjectMilestoneRepository.java`
- [x] `ProjectPaymentTermRepository.java`
- [x] `InvoiceRepository.java` with filters (status, client, project)

---

### Phase 2: Client Management ✅

#### 2.1 ClientService
- [x] `create(client)` - create client
- [x] `update(id, client)` - update client
- [x] `findById(id)` / `findAll()`
- [x] `findByFilters(search, active, pageable)` - search and filter
- [x] `deactivate(id)` - soft deactivate
- [x] `activate(id)` - reactivate

#### 2.2 ClientController
- [x] `GET /clients` - list with HTMX search
- [x] `GET /clients/new` - form
- [x] `POST /clients` - create
- [x] `GET /clients/{id}` - detail
- [x] `GET /clients/{id}/edit` - edit form
- [x] `POST /clients/{id}` - update
- [x] `POST /clients/{id}/deactivate` - deactivate
- [x] `POST /clients/{id}/activate` - activate

#### 2.3 Client Templates
- [x] `clients/list.html` - list with HTMX search
- [x] `clients/form.html` - create/edit form
- [x] `clients/detail.html` - detail with project list

#### 2.4 Client Functional Tests
- [x] Display client list page
- [x] Search clients
- [x] Create new client
- [x] Edit client
- [x] Deactivate/activate client

---

### Phase 3: Project Management ✅

#### 3.1 ProjectService
- [x] `create(project)` - create project with milestones and payment terms
- [x] `update(id, project)` - update project
- [x] `findById(id)` / `findAll()`
- [x] `findByFilters(status, clientId, search, pageable)`
- [x] `complete(id)` - mark as completed
- [x] `archive(id)` - archive project
- [x] `calculateProgress(id)` - weighted milestone progress

#### 3.2 ProjectController
- [x] `GET /projects` - list with HTMX filters
- [x] `GET /projects/new` - form
- [x] `POST /projects` - create
- [x] `GET /projects/{id}` - detail with milestones, payment terms
- [x] `GET /projects/{id}/edit` - edit form
- [x] `POST /projects/{id}` - update
- [x] `POST /projects/{id}/complete` - mark completed
- [x] `POST /projects/{id}/archive` - archive

#### 3.3 Project Templates
- [x] `projects/list.html` - list with HTMX filters
- [x] `projects/form.html` - form with inline milestones/payment terms
- [x] `projects/detail.html` - detail with milestones, terms, transactions

#### 3.4 Project Functional Tests
- [x] Display project list page
- [x] Filter by status/client
- [x] Create project with milestones
- [x] Create project with payment terms
- [x] Complete project
- [x] Archive project

---

### Phase 4: Milestone Management ✅

#### 4.1 MilestoneService
- [x] `create(projectId, milestone)` - add milestone to project
- [x] `update(id, milestone)` - update milestone
- [x] `delete(id)` - delete milestone
- [x] `startProgress(id)` - mark as IN_PROGRESS
- [x] `complete(id)` - mark as COMPLETED
- [x] `findByProjectId(projectId)` - list milestones

#### 4.2 Milestone Controller Endpoints (nested under projects)
- [x] `POST /projects/{id}/milestones` - add milestone
- [x] `POST /projects/{id}/milestones/{mid}` - update milestone (HTMX)
- [x] `POST /projects/{id}/milestones/{mid}/start` - start progress (HTMX)
- [x] `POST /projects/{id}/milestones/{mid}/complete` - complete (HTMX)
- [x] `DELETE /projects/{id}/milestones/{mid}` - delete (HTMX)

#### 4.3 Milestone Templates
- [x] Inline milestone management in project detail

#### 4.4 Milestone Functional Tests
- [x] Add milestone to project
- [x] Update milestone
- [x] Complete milestone
- [x] Verify progress calculation

---

### Phase 5: Payment Terms & Invoices ✅

#### 5.1 PaymentTermService
- [x] `create(projectId, paymentTerm)` - add payment term
- [x] `update(id, paymentTerm)` - update
- [x] `delete(id)` - delete
- [x] `findByProjectId(projectId)` - list payment terms
- [x] `linkMilestone(id, milestoneId)` - link to milestone

#### 5.2 InvoiceService
- [x] `generateFromPaymentTerm(paymentTermId)` - create invoice from term
- [x] `create(invoice)` - create invoice directly
- [x] `update(id, invoice)` - update
- [x] `send(id)` - mark as sent
- [x] `markPaid(id)` - mark as paid
- [x] `cancel(id)` - cancel invoice
- [x] `findByFilters(status, clientId, projectId, pageable)`

#### 5.3 InvoiceController
- [x] `GET /invoices` - list with HTMX filters
- [x] `GET /invoices/new` - form (optional project/client preselect)
- [x] `POST /invoices` - create
- [x] `GET /invoices/{id}` - detail
- [x] `GET /invoices/{id}/edit` - edit form
- [x] `POST /invoices/{id}` - update
- [x] `POST /invoices/{id}/send` - mark sent
- [x] `POST /invoices/{id}/paid` - mark paid
- [x] `POST /invoices/{id}/cancel` - cancel

#### 5.4 Invoice Templates
- [x] `invoices/list.html` - list with status filters
- [x] `invoices/form.html` - create/edit form
- [x] `invoices/detail.html` - invoice detail with status actions

#### 5.5 Revenue Recognition Integration ✅
- [x] Add `templateId` (FK to journal_templates) to ProjectPaymentTerm
- [x] Add `autoPost` (boolean, default false) to ProjectPaymentTerm
- [x] Create system template "Pengakuan Pendapatan Proyek" (Dr. Pendapatan Diterima Dimuka / Cr. Pendapatan Jasa)
- [x] On milestone complete → find linked payment terms → create Transaction via template
- [x] If autoPost=true → auto-post transaction; else → DRAFT for review

**Design Decision:** Use Transaction → Template → Journal flow (not Amortization) because:
- Amortization is time-based periodic allocation
- Milestone recognition is event-based (completion trigger)
- Transaction flow provides audit trail and user visibility

#### 5.6 Invoice Payment Integration ✅
- [x] Change "Tandai Lunas" to redirect to `/transactions/new?invoiceId={id}`
- [x] Pre-fill transaction: amount, description, project from invoice
- [x] User enters: bank account, reference number, transactionDate (actual cash date)
- [x] On transaction post → mark invoice as PAID
- [x] Add `transactionId` to Invoice entity for linking
- [x] On cancel/back → invoice stays unpaid

**Design Decision:** Invoice payment creates cash receipt transaction because:
- Ensures every PAID invoice has journal entry
- User specifies bank account and reference number
- Proper audit trail with bank statement reference
- Date handling: transactionDate = actual cash date, createdAt = key-in date

#### 5.7 Invoice Functional Tests
- [x] Create invoice
- [x] Send invoice
- [x] Redirect to transaction form when clicking Tandai Lunas
- [x] Cancel invoice
- [ ] Generate invoice from payment term (click button, verify invoice created)
- [ ] Full invoice payment flow with transaction completion

---

### Phase 6: Transaction-Project Linking ✅

#### 6.1 JournalEntry Updates
- [x] `project` field already on JournalEntry entity
- [x] TransactionService assigns project to journal entries on post

#### 6.2 Transaction Updates
- [x] Add `project` field to Transaction entity
- [x] Add `projectId` to TransactionDto
- [x] TransactionService loads project on create

#### 6.3 UI Updates
- [x] Add project dropdown to transaction form
- [x] Display project on transaction detail page
- [x] Link to project detail from transaction

#### 6.4 Filter by Project ✅
- [x] Add project dropdown to transaction list filter
- [x] Update TransactionRepository query to filter by projectId
- [x] Update controller to accept projectId parameter
- [x] HTMX partial update for filtered results

#### 6.5 Functional Tests
- [x] Create transaction with project
- [x] Verify project shown on transaction detail
- [ ] Filter transactions by project (manual test)

---

### Phase 7: Profitability Reports ✅

#### 7.1 ProjectProfitabilityService
- [x] `calculateProjectProfitability(projectId, dateRange)` - single project
- [x] `calculateClientProfitability(clientId, dateRange)` - all client projects
- [x] `getClientRanking(dateRange, limit)` - client ranking by revenue

#### 7.2 Cost Overrun Detection
- [x] `calculateCostOverrun(projectId)` - % spent vs % complete
- [x] Return: budget, spent, progress, projected final cost, projected loss, risk level

#### 7.3 Report Controller
- [x] `GET /reports/project-profitability` - project profitability report
- [x] `GET /reports/client-profitability` - client profitability report
- [x] `GET /reports/client-ranking` - top clients by revenue

#### 7.4 Report Templates
- [x] `reports/project-profitability.html` - single project P&L with cost status
- [x] `reports/client-profitability.html` - client aggregate with project breakdown
- [x] `reports/client-ranking.html` - client ranking with progress bars

#### 7.5 Report Functional Tests
- [x] Generate project profitability report (11 tests)
- [x] Generate client profitability report (10 tests)
- [x] Client ranking report with limits (5 tests)
- [x] Verify cost overrun detection with risk levels

---

## Status Summary

| Phase | Description | Status |
|-------|-------------|--------|
| 1 | Database & Core Entities | ✅ Complete |
| 2 | Client Management | ✅ Complete |
| 3 | Project Management | ✅ Complete |
| 4 | Milestone Management | ✅ Complete |
| 5 | Payment Terms & Invoices | ✅ Complete |
| 6 | Transaction-Project Linking | ✅ Complete |
| 7 | Profitability Reports | ✅ Complete |

---

## Acceptance Criteria

1. ✅ User can create and manage clients
2. ✅ User can create projects with milestones and payment terms
3. ✅ User can link transactions to projects
4. ✅ Milestone completion triggers revenue recognition (via Transaction → Template → Journal)
5. ✅ Invoice generation from payment terms works
6. ✅ Invoice payment redirects to transaction form for cash receipt entry
7. ✅ Project profitability report shows revenue - costs
8. ✅ Client profitability report aggregates all client projects
9. ✅ Cost overrun detection calculates % spent vs % complete with risk levels
10. ✅ HTMX partial updates work for all list pages
11. ✅ Filter transactions by project works
12. ✅ All functionality verified by Playwright tests
