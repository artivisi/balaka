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

### Phase 1: Database & Core Entities

#### 1.1 Database Schema (V008 migration)

```sql
-- Clients
CREATE TABLE clients (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    contact_person VARCHAR(255),
    email VARCHAR(255),
    phone VARCHAR(50),
    address TEXT,
    notes TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Projects
CREATE TABLE projects (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    id_client UUID REFERENCES clients(id),
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    contract_value DECIMAL(19, 2),
    budget_amount DECIMAL(19, 2),
    start_date DATE,
    end_date DATE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Milestones
CREATE TABLE project_milestones (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_project UUID NOT NULL REFERENCES projects(id),
    sequence INT NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    completion_percent INT NOT NULL DEFAULT 0,
    target_date DATE,
    actual_date DATE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(id_project, sequence)
);

-- Payment Terms
CREATE TABLE project_payment_terms (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_project UUID NOT NULL REFERENCES projects(id),
    sequence INT NOT NULL,
    name VARCHAR(255) NOT NULL,
    percentage DECIMAL(5, 2),
    amount DECIMAL(19, 2),
    due_trigger VARCHAR(20) NOT NULL,
    id_milestone UUID REFERENCES project_milestones(id),
    due_date DATE,
    id_invoice UUID,
    id_amortization_schedule UUID REFERENCES amortization_schedules(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(id_project, sequence)
);

-- Invoices
CREATE TABLE invoices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_number VARCHAR(50) NOT NULL UNIQUE,
    id_client UUID NOT NULL REFERENCES clients(id),
    id_project UUID REFERENCES projects(id),
    id_payment_term UUID REFERENCES project_payment_terms(id),
    invoice_date DATE NOT NULL,
    due_date DATE NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    sent_at TIMESTAMP,
    paid_at TIMESTAMP,
    id_journal_entry UUID REFERENCES journal_entries(id),
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Add project_id to journal_entries
ALTER TABLE journal_entries ADD COLUMN id_project UUID REFERENCES projects(id);
CREATE INDEX idx_journal_entries_project ON journal_entries(id_project);

-- Indexes
CREATE INDEX idx_projects_client ON projects(id_client);
CREATE INDEX idx_projects_status ON projects(status);
CREATE INDEX idx_milestones_project ON project_milestones(id_project);
CREATE INDEX idx_payment_terms_project ON project_payment_terms(id_project);
CREATE INDEX idx_invoices_client ON invoices(id_client);
CREATE INDEX idx_invoices_project ON invoices(id_project);
CREATE INDEX idx_invoices_status ON invoices(status);
```

#### 1.2 Enums
- [ ] `ProjectStatus.java` - ACTIVE, COMPLETED, ARCHIVED
- [ ] `MilestoneStatus.java` - PENDING, IN_PROGRESS, COMPLETED
- [ ] `PaymentTrigger.java` - ON_SIGNING, ON_MILESTONE, ON_COMPLETION, FIXED_DATE
- [ ] `InvoiceStatus.java` - DRAFT, SENT, PAID, OVERDUE, CANCELLED

#### 1.3 Entity Classes
- [ ] `Client.java` entity
- [ ] `Project.java` entity with @ManyToOne to Client
- [ ] `ProjectMilestone.java` entity with @ManyToOne to Project
- [ ] `ProjectPaymentTerm.java` entity with @ManyToOne to Project, Milestone
- [ ] `Invoice.java` entity with references to Client, Project, PaymentTerm

#### 1.4 Repositories
- [ ] `ClientRepository.java` with search by name/code
- [ ] `ProjectRepository.java` with filters (status, client)
- [ ] `ProjectMilestoneRepository.java`
- [ ] `ProjectPaymentTermRepository.java`
- [ ] `InvoiceRepository.java` with filters (status, client, project)

---

### Phase 2: Client Management

#### 2.1 ClientService
- [ ] `create(client)` - create client
- [ ] `update(id, client)` - update client
- [ ] `findById(id)` / `findAll()`
- [ ] `findByFilters(search, active, pageable)` - search and filter
- [ ] `deactivate(id)` - soft deactivate
- [ ] `activate(id)` - reactivate

#### 2.2 ClientController
- [ ] `GET /clients` - list with HTMX search
- [ ] `GET /clients/new` - form
- [ ] `POST /clients` - create
- [ ] `GET /clients/{id}` - detail
- [ ] `GET /clients/{id}/edit` - edit form
- [ ] `POST /clients/{id}` - update
- [ ] `POST /clients/{id}/deactivate` - deactivate
- [ ] `POST /clients/{id}/activate` - activate

#### 2.3 Client Templates
- [ ] `clients/list.html` - list with HTMX search
- [ ] `clients/form.html` - create/edit form
- [ ] `clients/detail.html` - detail with project list
- [ ] `clients/fragments/client-table.html` - HTMX fragment

#### 2.4 Client Functional Tests
- [ ] Display client list page
- [ ] Search clients
- [ ] Create new client
- [ ] Edit client
- [ ] Deactivate/activate client

---

### Phase 3: Project Management

#### 3.1 ProjectService
- [ ] `create(project)` - create project with milestones and payment terms
- [ ] `update(id, project)` - update project
- [ ] `findById(id)` / `findAll()`
- [ ] `findByFilters(status, clientId, search, pageable)`
- [ ] `complete(id)` - mark as completed
- [ ] `archive(id)` - archive project
- [ ] `calculateProgress(id)` - weighted milestone progress

#### 3.2 ProjectController
- [ ] `GET /projects` - list with HTMX filters
- [ ] `GET /projects/new` - form
- [ ] `POST /projects` - create
- [ ] `GET /projects/{id}` - detail with milestones, payment terms
- [ ] `GET /projects/{id}/edit` - edit form
- [ ] `POST /projects/{id}` - update
- [ ] `POST /projects/{id}/complete` - mark completed
- [ ] `POST /projects/{id}/archive` - archive

#### 3.3 Project Templates
- [ ] `projects/list.html` - list with HTMX filters
- [ ] `projects/form.html` - form with inline milestones/payment terms
- [ ] `projects/detail.html` - detail with milestones, terms, transactions
- [ ] `projects/fragments/project-table.html` - HTMX fragment

#### 3.4 Project Functional Tests
- [ ] Display project list page
- [ ] Filter by status/client
- [ ] Create project with milestones
- [ ] Create project with payment terms
- [ ] Complete project
- [ ] Archive project

---

### Phase 4: Milestone Management

#### 4.1 MilestoneService
- [ ] `create(projectId, milestone)` - add milestone to project
- [ ] `update(id, milestone)` - update milestone
- [ ] `delete(id)` - delete milestone
- [ ] `startProgress(id)` - mark as IN_PROGRESS
- [ ] `complete(id)` - mark as COMPLETED, trigger revenue recognition
- [ ] `findByProjectId(projectId)` - list milestones

#### 4.2 Milestone Controller Endpoints (nested under projects)
- [ ] `POST /projects/{id}/milestones` - add milestone
- [ ] `POST /projects/{id}/milestones/{mid}` - update milestone (HTMX)
- [ ] `POST /projects/{id}/milestones/{mid}/start` - start progress (HTMX)
- [ ] `POST /projects/{id}/milestones/{mid}/complete` - complete (HTMX)
- [ ] `DELETE /projects/{id}/milestones/{mid}` - delete (HTMX)

#### 4.3 Milestone Templates
- [ ] `projects/fragments/milestone-table.html` - HTMX fragment
- [ ] `projects/fragments/milestone-form.html` - inline form fragment

#### 4.4 Milestone Functional Tests
- [ ] Add milestone to project
- [ ] Update milestone progress
- [ ] Complete milestone
- [ ] Verify progress calculation

---

### Phase 5: Payment Terms & Invoices

#### 5.1 PaymentTermService
- [ ] `create(projectId, paymentTerm)` - add payment term
- [ ] `update(id, paymentTerm)` - update
- [ ] `delete(id)` - delete
- [ ] `findByProjectId(projectId)` - list payment terms
- [ ] `linkMilestone(id, milestoneId)` - link to milestone

#### 5.2 InvoiceService
- [ ] `generateFromPaymentTerm(paymentTermId)` - create invoice from term
- [ ] `create(invoice)` - create invoice directly
- [ ] `update(id, invoice)` - update
- [ ] `send(id)` - mark as sent
- [ ] `markPaid(id, paidAt)` - mark as paid, create journal entry
- [ ] `cancel(id)` - cancel invoice
- [ ] `findByFilters(status, clientId, projectId, pageable)`

#### 5.3 InvoiceController
- [ ] `GET /invoices` - list with HTMX filters
- [ ] `GET /invoices/new` - form (optional project/client preselect)
- [ ] `POST /invoices` - create
- [ ] `GET /invoices/{id}` - detail
- [ ] `GET /invoices/{id}/edit` - edit form
- [ ] `POST /invoices/{id}` - update
- [ ] `POST /invoices/{id}/send` - mark sent
- [ ] `POST /invoices/{id}/paid` - mark paid
- [ ] `POST /invoices/{id}/cancel` - cancel

#### 5.4 Invoice Templates
- [ ] `invoices/list.html` - list with HTMX filters
- [ ] `invoices/form.html` - create/edit form
- [ ] `invoices/detail.html` - invoice detail
- [ ] `invoices/fragments/invoice-table.html` - HTMX fragment

#### 5.5 Revenue Recognition Integration
- [ ] On milestone complete → find linked payment term → create amortization entry
- [ ] Journal: Dr. Pendapatan Diterima Dimuka / Cr. Pendapatan Jasa

#### 5.6 Invoice Functional Tests
- [ ] Generate invoice from payment term
- [ ] Create standalone invoice
- [ ] Send invoice
- [ ] Mark invoice paid
- [ ] Verify journal entry created

---

### Phase 6: Transaction-Project Linking

#### 6.1 JournalEntry Updates
- [ ] Add `project` field to JournalEntry entity
- [ ] Update JournalEntryService to accept projectId
- [ ] Update TransactionService to pass projectId

#### 6.2 UI Updates
- [ ] Add project dropdown to transaction form
- [ ] Project dropdown loaded via HTMX based on client (optional)
- [ ] Display project on transaction list
- [ ] Display project on journal entry detail

#### 6.3 Functional Tests
- [ ] Create transaction with project
- [ ] Filter transactions by project
- [ ] Verify project shown on journal entry

---

### Phase 7: Profitability Reports

#### 7.1 ProjectProfitabilityService
- [ ] `calculateProjectProfitability(projectId, dateRange)` - single project
- [ ] `calculateClientProfitability(clientId, dateRange)` - all client projects
- [ ] `getTopClients(dateRange, limit)` - client ranking by revenue

#### 7.2 Cost Overrun Detection
- [ ] `calculateCostOverrunRisk(projectId)` - % spent vs % complete
- [ ] Return: budget, spent, progress, projected final cost, projected loss

#### 7.3 Report Controller
- [ ] `GET /reports/project-profitability` - project profitability report
- [ ] `GET /reports/client-profitability` - client profitability report
- [ ] `GET /reports/client-ranking` - top clients by revenue

#### 7.4 Report Templates
- [ ] `reports/project-profitability.html` - single project P&L
- [ ] `reports/client-profitability.html` - client aggregate
- [ ] `reports/client-ranking.html` - client ranking

#### 7.5 Report Functional Tests
- [ ] Generate project profitability report
- [ ] Generate client profitability report
- [ ] Verify cost overrun detection

---

## File Structure

```
src/main/java/com/artivisi/accountingfinance/
├── entity/
│   ├── Client.java
│   ├── Project.java
│   ├── ProjectMilestone.java
│   ├── ProjectPaymentTerm.java
│   └── Invoice.java
├── enums/
│   ├── ProjectStatus.java
│   ├── MilestoneStatus.java
│   ├── PaymentTrigger.java
│   └── InvoiceStatus.java
├── repository/
│   ├── ClientRepository.java
│   ├── ProjectRepository.java
│   ├── ProjectMilestoneRepository.java
│   ├── ProjectPaymentTermRepository.java
│   └── InvoiceRepository.java
├── service/
│   ├── ClientService.java
│   ├── ProjectService.java
│   ├── MilestoneService.java
│   ├── PaymentTermService.java
│   ├── InvoiceService.java
│   └── ProjectProfitabilityService.java
└── controller/
    ├── ClientController.java
    ├── ProjectController.java
    └── InvoiceController.java

src/main/resources/
├── db/migration/
│   └── V008__create_project_tracking.sql
└── templates/
    ├── clients/
    │   ├── list.html
    │   ├── form.html
    │   ├── detail.html
    │   └── fragments/client-table.html
    ├── projects/
    │   ├── list.html
    │   ├── form.html
    │   ├── detail.html
    │   └── fragments/
    │       ├── project-table.html
    │       ├── milestone-table.html
    │       └── milestone-form.html
    ├── invoices/
    │   ├── list.html
    │   ├── form.html
    │   ├── detail.html
    │   └── fragments/invoice-table.html
    └── reports/
        ├── project-profitability.html
        ├── client-profitability.html
        └── client-ranking.html

src/test/java/com/artivisi/accountingfinance/functional/
├── ClientTest.java
├── ProjectTest.java
├── InvoiceTest.java
├── ProjectProfitabilityTest.java
└── page/
    ├── ClientListPage.java
    ├── ClientFormPage.java
    ├── ClientDetailPage.java
    ├── ProjectListPage.java
    ├── ProjectFormPage.java
    ├── ProjectDetailPage.java
    ├── InvoiceListPage.java
    ├── InvoiceFormPage.java
    └── InvoiceDetailPage.java
```

---

## Status Flows

### Project Status
```
ACTIVE → COMPLETED → ARCHIVED
           ↓
       (can reopen)
           ↓
        ACTIVE
```

### Milestone Status
```
PENDING → IN_PROGRESS → COMPLETED
```

### Invoice Status
```
DRAFT → SENT → PAID
          ↓
      OVERDUE (automatic based on due_date)

Any status → CANCELLED
```

---

## Acceptance Criteria

1. User can create and manage clients
2. User can create projects with milestones and payment terms
3. User can link transactions to projects
4. Milestone completion triggers revenue recognition
5. Invoice generation from payment terms works
6. Project profitability report shows revenue - costs
7. Client profitability report aggregates all client projects
8. Cost overrun detection calculates % spent vs % complete
9. HTMX partial updates work for all list pages
10. All functionality verified by Playwright tests
