-- V008: Project Tracking Tables
-- Clients, Projects, Milestones, Payment Terms, Invoices

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

-- Project Milestones
CREATE TABLE project_milestones (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_project UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    sequence INT NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    weight_percent INT NOT NULL DEFAULT 0,
    target_date DATE,
    actual_date DATE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(id_project, sequence)
);

-- Project Payment Terms
CREATE TABLE project_payment_terms (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_project UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    sequence INT NOT NULL,
    name VARCHAR(255) NOT NULL,
    percentage DECIMAL(5, 2),
    amount DECIMAL(19, 2),
    due_trigger VARCHAR(20) NOT NULL,
    id_milestone UUID REFERENCES project_milestones(id),
    due_date DATE,
    id_template UUID REFERENCES journal_templates(id),
    auto_post BOOLEAN NOT NULL DEFAULT FALSE,
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
    id_journal_entry UUID,
    id_transaction UUID REFERENCES transactions(id),
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Add project_id to journal_entries for linking transactions to projects
ALTER TABLE journal_entries ADD COLUMN id_project UUID REFERENCES projects(id);

-- Add project_id to transactions for project association at transaction level
ALTER TABLE transactions ADD COLUMN id_project UUID REFERENCES projects(id);

-- Indexes
CREATE INDEX idx_clients_active ON clients(active);
CREATE INDEX idx_clients_name ON clients(name);

CREATE INDEX idx_projects_client ON projects(id_client);
CREATE INDEX idx_projects_status ON projects(status);

CREATE INDEX idx_milestones_project ON project_milestones(id_project);
CREATE INDEX idx_milestones_status ON project_milestones(status);

CREATE INDEX idx_payment_terms_project ON project_payment_terms(id_project);
CREATE INDEX idx_payment_terms_milestone ON project_payment_terms(id_milestone);
CREATE INDEX idx_payment_terms_template ON project_payment_terms(id_template);

CREATE INDEX idx_invoices_client ON invoices(id_client);
CREATE INDEX idx_invoices_project ON invoices(id_project);
CREATE INDEX idx_invoices_status ON invoices(status);
CREATE INDEX idx_invoices_due_date ON invoices(due_date);

CREATE INDEX idx_journal_entries_project ON journal_entries(id_project);
CREATE INDEX idx_transactions_project ON transactions(id_project);
